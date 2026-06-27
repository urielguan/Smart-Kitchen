USE `smart_food_safety`;

-- 41: 第三方接入状态映射加固
-- 1) 增加“同一租户 + 同一接入配置 + 同一第三方状态编码，只允许存在一条有效状态映射”的条件唯一约束
-- 2) 兼容历史数据，对首尾空格导致的重复状态编码先做规整

UPDATE sys_integration_status_mapping
SET source_status_code = TRIM(source_status_code)
WHERE source_status_code IS NOT NULL
  AND source_status_code <> TRIM(source_status_code);

DROP TEMPORARY TABLE IF EXISTS tmp_duplicate_status_mapping_ids;
CREATE TEMPORARY TABLE tmp_duplicate_status_mapping_ids AS
SELECT id
FROM (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY tenant_id, config_id, source_status_code
               ORDER BY enabled DESC, updated_at DESC, id DESC
           ) AS rn
    FROM sys_integration_status_mapping
    WHERE deleted = 0
) ranked
WHERE ranked.rn > 1;

UPDATE sys_integration_status_mapping
SET deleted = 1,
    updated_at = NOW(),
    remark = CONCAT_WS('；', NULLIF(remark, ''), '系统自动清理重复状态映射（为创建唯一约束保留最新记录）')
WHERE id IN (SELECT id FROM tmp_duplicate_status_mapping_ids);

DROP TEMPORARY TABLE IF EXISTS tmp_duplicate_status_mapping_ids;

SET @status_mapping_source_uk_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = 'smart_food_safety'
      AND table_name = 'sys_integration_status_mapping'
      AND index_name = 'uk_integration_status_mapping_source'
);

SET @status_mapping_add_source_uk_sql := IF(
    @status_mapping_source_uk_exists = 0,
    'CREATE UNIQUE INDEX uk_integration_status_mapping_source ON sys_integration_status_mapping (tenant_id, config_id, source_status_code, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END))',
    'SELECT 1'
);

PREPARE status_mapping_add_source_uk_stmt FROM @status_mapping_add_source_uk_sql;
EXECUTE status_mapping_add_source_uk_stmt;
DEALLOCATE PREPARE status_mapping_add_source_uk_stmt;
