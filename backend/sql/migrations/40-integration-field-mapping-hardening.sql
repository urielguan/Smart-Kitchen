USE `smart_food_safety`;

-- 40: 第三方接入字段映射加固
-- 1) 新增异常时处理方式字段，支持 fail / skip / log_only / manual_review
-- 2) 增加“同一租户 + 同一接入配置 + 同一系统字段，只允许一条启用映射”的条件唯一约束

SET @field_mapping_error_strategy_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = 'smart_food_safety'
      AND table_name = 'sys_integration_field_mapping'
      AND column_name = 'error_strategy'
);

SET @field_mapping_add_error_strategy_sql := IF(
    @field_mapping_error_strategy_exists = 0,
    'ALTER TABLE sys_integration_field_mapping ADD COLUMN error_strategy VARCHAR(30) NOT NULL DEFAULT ''fail'' COMMENT ''异常时处理方式：fail/skip/log_only/manual_review'' AFTER enabled',
    'SELECT 1'
);

PREPARE field_mapping_add_error_strategy_stmt FROM @field_mapping_add_error_strategy_sql;
EXECUTE field_mapping_add_error_strategy_stmt;
DEALLOCATE PREPARE field_mapping_add_error_strategy_stmt;

SET @field_mapping_enabled_uk_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = 'smart_food_safety'
      AND table_name = 'sys_integration_field_mapping'
      AND index_name = 'uk_integration_field_mapping_enabled'
);

SET @field_mapping_add_enabled_uk_sql := IF(
    @field_mapping_enabled_uk_exists = 0,
    'CREATE UNIQUE INDEX uk_integration_field_mapping_enabled ON sys_integration_field_mapping (tenant_id, config_id, target_field, (CASE WHEN deleted = 0 AND enabled = 1 THEN 1 ELSE NULL END))',
    'SELECT 1'
);

PREPARE field_mapping_add_enabled_uk_stmt FROM @field_mapping_add_enabled_uk_sql;
EXECUTE field_mapping_add_enabled_uk_stmt;
DEALLOCATE PREPARE field_mapping_add_enabled_uk_stmt;
