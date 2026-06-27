USE `smart_food_safety`;

-- 43: 第三方回调日志中心加固
-- 1) 补充配置/平台/组织快照、claimKey、任务链路ID、独立查看权限
-- 2) 修正 tenantId 可空口径，兼容未能明确归属的异常/恶意回调
-- 3) 为历史日志尽量回填绑定、配置、组织、平台快照与 claimKey

SET @add_callback_log_config_id := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND column_name = 'config_id'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD COLUMN config_id BIGINT DEFAULT NULL COMMENT ''接入配置ID'' AFTER binding_id'
);
PREPARE stmt_add_callback_log_config_id FROM @add_callback_log_config_id;
EXECUTE stmt_add_callback_log_config_id;
DEALLOCATE PREPARE stmt_add_callback_log_config_id;

SET @add_callback_log_config_name_snapshot := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND column_name = 'config_name_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD COLUMN config_name_snapshot VARCHAR(200) DEFAULT NULL COMMENT ''执行当时的配置名称快照'' AFTER config_id'
);
PREPARE stmt_add_callback_log_config_name_snapshot FROM @add_callback_log_config_name_snapshot;
EXECUTE stmt_add_callback_log_config_name_snapshot;
DEALLOCATE PREPARE stmt_add_callback_log_config_name_snapshot;

SET @add_callback_log_provider_name_snapshot := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND column_name = 'provider_name_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD COLUMN provider_name_snapshot VARCHAR(100) DEFAULT NULL COMMENT ''执行当时的平台名称快照'' AFTER provider_code'
);
PREPARE stmt_add_callback_log_provider_name_snapshot FROM @add_callback_log_provider_name_snapshot;
EXECUTE stmt_add_callback_log_provider_name_snapshot;
DEALLOCATE PREPARE stmt_add_callback_log_provider_name_snapshot;

SET @add_callback_log_claim_key := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND column_name = 'claim_key'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD COLUMN claim_key VARCHAR(255) DEFAULT NULL COMMENT ''幂等占坑键'' AFTER idempotent_key'
);
PREPARE stmt_add_callback_log_claim_key FROM @add_callback_log_claim_key;
EXECUTE stmt_add_callback_log_claim_key;
DEALLOCATE PREPARE stmt_add_callback_log_claim_key;

SET @add_callback_log_task_id := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND column_name = 'task_id'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD COLUMN task_id BIGINT DEFAULT NULL COMMENT ''关联同步任务ID'' AFTER external_no'
);
PREPARE stmt_add_callback_log_task_id FROM @add_callback_log_task_id;
EXECUTE stmt_add_callback_log_task_id;
DEALLOCATE PREPARE stmt_add_callback_log_task_id;

SET @add_callback_log_sync_log_id := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND column_name = 'sync_log_id'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD COLUMN sync_log_id BIGINT DEFAULT NULL COMMENT ''关联同步日志ID'' AFTER task_id'
);
PREPARE stmt_add_callback_log_sync_log_id FROM @add_callback_log_sync_log_id;
EXECUTE stmt_add_callback_log_sync_log_id;
DEALLOCATE PREPARE stmt_add_callback_log_sync_log_id;

SET @add_callback_log_audit_log_id := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND column_name = 'audit_log_id'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD COLUMN audit_log_id BIGINT DEFAULT NULL COMMENT ''关联审计日志ID'' AFTER sync_log_id'
);
PREPARE stmt_add_callback_log_audit_log_id FROM @add_callback_log_audit_log_id;
EXECUTE stmt_add_callback_log_audit_log_id;
DEALLOCATE PREPARE stmt_add_callback_log_audit_log_id;

SET @add_callback_log_org_name_snapshot := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND column_name = 'org_name_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD COLUMN org_name_snapshot VARCHAR(100) DEFAULT NULL COMMENT ''执行当时的组织名称快照'' AFTER org_id'
);
PREPARE stmt_add_callback_log_org_name_snapshot FROM @add_callback_log_org_name_snapshot;
EXECUTE stmt_add_callback_log_org_name_snapshot;
DEALLOCATE PREPARE stmt_add_callback_log_org_name_snapshot;

ALTER TABLE sys_integration_callback_log
    MODIFY COLUMN process_status VARCHAR(30) DEFAULT NULL COMMENT '处理状态：pending/security_failed/ignored/duplicate/success/failed/no_data/mapping_missing',
    MODIFY COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '租户ID';

SET @add_callback_log_config_idx := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND index_name = 'idx_integration_callback_config'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD KEY idx_integration_callback_config (config_id)'
);
PREPARE stmt_add_callback_log_config_idx FROM @add_callback_log_config_idx;
EXECUTE stmt_add_callback_log_config_idx;
DEALLOCATE PREPARE stmt_add_callback_log_config_idx;

SET @add_callback_log_task_idx := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND index_name = 'idx_integration_callback_task'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD KEY idx_integration_callback_task (task_id)'
);
PREPARE stmt_add_callback_log_task_idx FROM @add_callback_log_task_idx;
EXECUTE stmt_add_callback_log_task_idx;
DEALLOCATE PREPARE stmt_add_callback_log_task_idx;

SET @add_callback_log_sync_log_idx := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND index_name = 'idx_integration_callback_sync_log'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD KEY idx_integration_callback_sync_log (sync_log_id)'
);
PREPARE stmt_add_callback_log_sync_log_idx FROM @add_callback_log_sync_log_idx;
EXECUTE stmt_add_callback_log_sync_log_idx;
DEALLOCATE PREPARE stmt_add_callback_log_sync_log_idx;

UPDATE sys_integration_callback_log l
LEFT JOIN sys_integration_binding b
       ON b.id = l.binding_id
      AND b.deleted = 0
LEFT JOIN sys_integration_module_config c
       ON c.id = COALESCE(l.config_id, b.config_id)
      AND c.deleted = 0
LEFT JOIN sys_integration_provider_template p
       ON p.provider_code = COALESCE(NULLIF(l.provider_code, ''), c.provider_code, b.provider_code)
      AND p.deleted = 0
      AND p.tenant_id = COALESCE(l.tenant_id, b.tenant_id, c.tenant_id, p.tenant_id)
LEFT JOIN sys_organization o
       ON o.id = COALESCE(l.org_id, b.org_id, c.org_id)
      AND o.deleted = 0
SET l.config_id = COALESCE(l.config_id, b.config_id),
    l.biz_id = COALESCE(l.biz_id, b.biz_id),
    l.biz_no = COALESCE(NULLIF(l.biz_no, ''), b.biz_no),
    l.biz_module = COALESCE(NULLIF(l.biz_module, ''), b.biz_module),
    l.biz_scene = COALESCE(NULLIF(l.biz_scene, ''), b.biz_scene),
    l.external_no = COALESCE(NULLIF(l.external_no, ''), b.external_no),
    l.org_id = COALESCE(l.org_id, b.org_id, c.org_id),
    l.tenant_id = COALESCE(l.tenant_id, b.tenant_id, c.tenant_id),
    l.config_name_snapshot = COALESCE(NULLIF(l.config_name_snapshot, ''), c.config_name),
    l.provider_name_snapshot = COALESCE(NULLIF(l.provider_name_snapshot, ''), p.provider_name, NULLIF(l.provider_code, '')),
    l.org_name_snapshot = COALESCE(NULLIF(l.org_name_snapshot, ''), o.org_name)
WHERE l.deleted = 0;

UPDATE sys_integration_callback_log
SET claim_key = NULL
WHERE deleted = 0
  AND TRIM(COALESCE(claim_key, '')) = '';

UPDATE sys_integration_callback_log
SET claim_key = CASE
    WHEN TRIM(COALESCE(idempotent_key, '')) = '' THEN NULL
    WHEN binding_id IS NOT NULL THEN CONCAT('binding:', binding_id, '|idempotent:', TRIM(idempotent_key))
    WHEN config_id IS NOT NULL THEN CONCAT('config:', config_id, '|idempotent:', TRIM(idempotent_key))
    WHEN TRIM(COALESCE(provider_code, '')) <> '' THEN CONCAT('provider:', TRIM(provider_code), '|idempotent:', TRIM(idempotent_key))
    ELSE NULL
END
WHERE deleted = 0
  AND claim_key IS NULL;

UPDATE sys_integration_callback_log l
INNER JOIN sys_integration_callback_log earlier
        ON earlier.deleted = 0
       AND l.deleted = 0
       AND l.claim_key IS NOT NULL
       AND l.claim_key = earlier.claim_key
       AND l.id > earlier.id
SET l.claim_key = NULL;

SET @add_callback_log_claim_unique := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_callback_log'
          AND index_name = 'uk_integration_callback_claim_key'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_callback_log ADD UNIQUE KEY uk_integration_callback_claim_key (claim_key)'
);
PREPARE stmt_add_callback_log_claim_unique FROM @add_callback_log_claim_unique;
EXECUTE stmt_add_callback_log_claim_unique;
DEALLOCATE PREPARE stmt_add_callback_log_claim_unique;

INSERT INTO auth_permission (
    permission_code,
    permission_name,
    permission_type,
    parent_id,
    module_code,
    resource_path,
    icon,
    sort_order,
    status
)
SELECT 'integration-management:view-callback',
       '查看第三方回调日志',
       'button',
       p.id,
       'sys',
       '',
       '',
       9,
       'active'
FROM auth_permission p
WHERE p.permission_code = 'integration-management'
  AND NOT EXISTS (
      SELECT 1
      FROM auth_permission
      WHERE permission_code = 'integration-management:view-callback'
  )
LIMIT 1;

INSERT INTO auth_role_permission (`role_id`, `permission_id`, `created_by`)
SELECT r.id, p.id, 1
FROM auth_role r
INNER JOIN auth_permission p ON p.permission_code = 'integration-management:view-callback'
LEFT JOIN auth_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code = 'SUPER_ADMIN'
  AND r.deleted = 0
  AND rp.id IS NULL;
