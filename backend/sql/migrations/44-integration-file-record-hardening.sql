USE `smart_food_safety`;

-- 44: 第三方附件转存记录加固
-- 1) 补充配置/业务/平台/组织快照、错误码、链路ID、源地址签名、MIME 类型等字段
-- 2) 历史数据尽量回填绑定、配置、业务编号、名称快照与脱敏后的原始地址
-- 3) 对同一绑定下同一附件来源签名建立唯一约束，避免轮询/回调/人工重试产生重复附件记录

SET @add_file_record_config_id := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'config_id'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN config_id BIGINT DEFAULT NULL COMMENT ''接入配置ID'' AFTER binding_id'
);
PREPARE stmt_add_file_record_config_id FROM @add_file_record_config_id;
EXECUTE stmt_add_file_record_config_id;
DEALLOCATE PREPARE stmt_add_file_record_config_id;

SET @add_file_record_config_name_snapshot := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'config_name_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN config_name_snapshot VARCHAR(200) DEFAULT NULL COMMENT ''执行当时的配置名称快照'' AFTER config_id'
);
PREPARE stmt_add_file_record_config_name_snapshot FROM @add_file_record_config_name_snapshot;
EXECUTE stmt_add_file_record_config_name_snapshot;
DEALLOCATE PREPARE stmt_add_file_record_config_name_snapshot;

SET @add_file_record_biz_id := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'biz_id'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN biz_id BIGINT DEFAULT NULL COMMENT ''业务主键ID'' AFTER biz_scene'
);
PREPARE stmt_add_file_record_biz_id FROM @add_file_record_biz_id;
EXECUTE stmt_add_file_record_biz_id;
DEALLOCATE PREPARE stmt_add_file_record_biz_id;

SET @add_file_record_biz_no := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'biz_no'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN biz_no VARCHAR(100) DEFAULT NULL COMMENT ''业务编号'' AFTER biz_id'
);
PREPARE stmt_add_file_record_biz_no FROM @add_file_record_biz_no;
EXECUTE stmt_add_file_record_biz_no;
DEALLOCATE PREPARE stmt_add_file_record_biz_no;

SET @add_file_record_provider_name_snapshot := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'provider_name_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN provider_name_snapshot VARCHAR(100) DEFAULT NULL COMMENT ''执行当时的平台名称快照'' AFTER provider_code'
);
PREPARE stmt_add_file_record_provider_name_snapshot FROM @add_file_record_provider_name_snapshot;
EXECUTE stmt_add_file_record_provider_name_snapshot;
DEALLOCATE PREPARE stmt_add_file_record_provider_name_snapshot;

SET @add_file_record_source_url_signature := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'source_url_signature'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN source_url_signature VARCHAR(128) DEFAULT NULL COMMENT ''原文件地址签名'' AFTER source_file_url'
);
PREPARE stmt_add_file_record_source_url_signature FROM @add_file_record_source_url_signature;
EXECUTE stmt_add_file_record_source_url_signature;
DEALLOCATE PREPARE stmt_add_file_record_source_url_signature;

SET @add_file_record_mime_type := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'mime_type'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN mime_type VARCHAR(100) DEFAULT NULL COMMENT ''文件MIME类型'' AFTER file_size'
);
PREPARE stmt_add_file_record_mime_type FROM @add_file_record_mime_type;
EXECUTE stmt_add_file_record_mime_type;
DEALLOCATE PREPARE stmt_add_file_record_mime_type;

SET @add_file_record_error_code := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'error_code'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN error_code VARCHAR(100) DEFAULT NULL COMMENT ''失败错误码'' AFTER storage_status'
);
PREPARE stmt_add_file_record_error_code FROM @add_file_record_error_code;
EXECUTE stmt_add_file_record_error_code;
DEALLOCATE PREPARE stmt_add_file_record_error_code;

SET @add_file_record_error_message := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'error_message'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN error_message VARCHAR(1000) DEFAULT NULL COMMENT ''失败错误信息'' AFTER error_code'
);
PREPARE stmt_add_file_record_error_message FROM @add_file_record_error_message;
EXECUTE stmt_add_file_record_error_message;
DEALLOCATE PREPARE stmt_add_file_record_error_message;

SET @add_file_record_task_id := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'task_id'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN task_id BIGINT DEFAULT NULL COMMENT ''关联同步任务ID'' AFTER error_message'
);
PREPARE stmt_add_file_record_task_id FROM @add_file_record_task_id;
EXECUTE stmt_add_file_record_task_id;
DEALLOCATE PREPARE stmt_add_file_record_task_id;

SET @add_file_record_sync_log_id := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'sync_log_id'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN sync_log_id BIGINT DEFAULT NULL COMMENT ''关联同步日志ID'' AFTER task_id'
);
PREPARE stmt_add_file_record_sync_log_id FROM @add_file_record_sync_log_id;
EXECUTE stmt_add_file_record_sync_log_id;
DEALLOCATE PREPARE stmt_add_file_record_sync_log_id;

SET @add_file_record_audit_log_id := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'audit_log_id'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN audit_log_id BIGINT DEFAULT NULL COMMENT ''关联审计日志ID'' AFTER sync_log_id'
);
PREPARE stmt_add_file_record_audit_log_id FROM @add_file_record_audit_log_id;
EXECUTE stmt_add_file_record_audit_log_id;
DEALLOCATE PREPARE stmt_add_file_record_audit_log_id;

SET @add_file_record_org_name_snapshot := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND column_name = 'org_name_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD COLUMN org_name_snapshot VARCHAR(100) DEFAULT NULL COMMENT ''执行当时的组织名称快照'' AFTER org_id'
);
PREPARE stmt_add_file_record_org_name_snapshot FROM @add_file_record_org_name_snapshot;
EXECUTE stmt_add_file_record_org_name_snapshot;
DEALLOCATE PREPARE stmt_add_file_record_org_name_snapshot;

ALTER TABLE sys_integration_file_record
    MODIFY COLUMN source_file_url VARCHAR(1000) DEFAULT NULL COMMENT '脱敏后的原文件地址',
    MODIFY COLUMN download_status VARCHAR(30) DEFAULT NULL COMMENT '下载状态：pending/success/failed/skipped/reused',
    MODIFY COLUMN storage_status VARCHAR(30) DEFAULT NULL COMMENT '转存状态：pending/success/failed/skipped/reused';

UPDATE sys_integration_file_record f
LEFT JOIN sys_integration_binding b
       ON b.id = f.binding_id
      AND b.deleted = 0
LEFT JOIN sys_integration_module_config c
       ON c.id = COALESCE(f.config_id, b.config_id)
      AND c.deleted = 0
LEFT JOIN sys_integration_provider_template p
       ON p.provider_code = COALESCE(NULLIF(f.provider_code, ''), c.provider_code, b.provider_code)
      AND p.deleted = 0
      AND p.tenant_id = COALESCE(f.tenant_id, b.tenant_id, c.tenant_id, p.tenant_id)
LEFT JOIN sys_organization o
       ON o.id = COALESCE(f.org_id, b.org_id, c.org_id)
      AND o.deleted = 0
SET f.config_id = COALESCE(f.config_id, b.config_id),
    f.biz_module = COALESCE(NULLIF(f.biz_module, ''), b.biz_module),
    f.biz_scene = COALESCE(NULLIF(f.biz_scene, ''), b.biz_scene),
    f.biz_id = COALESCE(f.biz_id, b.biz_id),
    f.biz_no = COALESCE(NULLIF(f.biz_no, ''), b.biz_no),
    f.provider_code = COALESCE(NULLIF(f.provider_code, ''), c.provider_code, b.provider_code),
    f.org_id = COALESCE(f.org_id, b.org_id, c.org_id),
    f.tenant_id = COALESCE(f.tenant_id, b.tenant_id, c.tenant_id, 1),
    f.config_name_snapshot = COALESCE(NULLIF(f.config_name_snapshot, ''), c.config_name),
    f.provider_name_snapshot = COALESCE(NULLIF(f.provider_name_snapshot, ''), p.provider_name, NULLIF(f.provider_code, '')),
    f.org_name_snapshot = COALESCE(NULLIF(f.org_name_snapshot, ''), o.org_name)
WHERE f.deleted = 0;

UPDATE sys_integration_file_record
SET source_file_url = SUBSTRING_INDEX(SUBSTRING_INDEX(TRIM(source_file_url), '#', 1), '?', 1)
WHERE deleted = 0
  AND TRIM(COALESCE(source_file_url, '')) <> '';

UPDATE sys_integration_file_record
SET source_url_signature = CASE
    WHEN TRIM(COALESCE(source_url_signature, '')) <> '' THEN TRIM(source_url_signature)
    WHEN TRIM(COALESCE(source_file_url, '')) <> '' THEN SHA2(TRIM(source_file_url), 256)
    WHEN TRIM(COALESCE(source_file_name, '')) <> '' THEN SHA2(CONCAT('name:', TRIM(source_file_name)), 256)
    ELSE SHA2(CONCAT('_blank_', id), 256)
END
WHERE deleted = 0;

UPDATE sys_integration_file_record f
INNER JOIN (
    SELECT binding_id, tenant_id, source_url_signature, MAX(id) AS keep_id
    FROM sys_integration_file_record
    WHERE deleted = 0
      AND binding_id IS NOT NULL
      AND TRIM(COALESCE(source_url_signature, '')) <> ''
    GROUP BY binding_id, tenant_id, source_url_signature
    HAVING COUNT(*) > 1
) dup
        ON dup.binding_id = f.binding_id
       AND dup.tenant_id = f.tenant_id
       AND dup.source_url_signature = f.source_url_signature
SET f.deleted = 1,
    f.source_url_signature = CONCAT(LEFT(f.source_url_signature, 110), '#dup#', f.id)
WHERE f.deleted = 0
  AND f.id <> dup.keep_id;

SET @add_file_record_config_idx := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND index_name = 'idx_integration_file_config'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD KEY idx_integration_file_config (config_id)'
);
PREPARE stmt_add_file_record_config_idx FROM @add_file_record_config_idx;
EXECUTE stmt_add_file_record_config_idx;
DEALLOCATE PREPARE stmt_add_file_record_config_idx;

SET @add_file_record_task_idx := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND index_name = 'idx_integration_file_task'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD KEY idx_integration_file_task (task_id)'
);
PREPARE stmt_add_file_record_task_idx FROM @add_file_record_task_idx;
EXECUTE stmt_add_file_record_task_idx;
DEALLOCATE PREPARE stmt_add_file_record_task_idx;

SET @add_file_record_sync_log_idx := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND index_name = 'idx_integration_file_sync_log'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD KEY idx_integration_file_sync_log (sync_log_id)'
);
PREPARE stmt_add_file_record_sync_log_idx FROM @add_file_record_sync_log_idx;
EXECUTE stmt_add_file_record_sync_log_idx;
DEALLOCATE PREPARE stmt_add_file_record_sync_log_idx;

SET @add_file_record_biz_no_idx := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND index_name = 'idx_integration_file_biz_no'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD KEY idx_integration_file_biz_no (biz_no)'
);
PREPARE stmt_add_file_record_biz_no_idx FROM @add_file_record_biz_no_idx;
EXECUTE stmt_add_file_record_biz_no_idx;
DEALLOCATE PREPARE stmt_add_file_record_biz_no_idx;

SET @add_file_record_updated_idx := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND index_name = 'idx_integration_file_updated'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD KEY idx_integration_file_updated (updated_at)'
);
PREPARE stmt_add_file_record_updated_idx FROM @add_file_record_updated_idx;
EXECUTE stmt_add_file_record_updated_idx;
DEALLOCATE PREPARE stmt_add_file_record_updated_idx;

SET @add_file_record_unique_idx := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_file_record'
          AND index_name = 'uk_integration_file_binding_signature'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_file_record ADD UNIQUE KEY uk_integration_file_binding_signature (binding_id, source_url_signature, tenant_id, deleted)'
);
PREPARE stmt_add_file_record_unique_idx FROM @add_file_record_unique_idx;
EXECUTE stmt_add_file_record_unique_idx;
DEALLOCATE PREPARE stmt_add_file_record_unique_idx;
