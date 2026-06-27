USE `smart_food_safety`;

-- 42: 第三方同步日志中心加固
-- 1) 补充请求头/请求体、任务类型、结果说明、回写结果、历史名称快照、人工处理闭环字段
-- 2) 兼容历史日志，尽量回填当前可推断的配置/平台/组织名称、任务类型、结果说明和默认闭环状态

SET @add_sync_log_config_name_snapshot := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'config_name_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN config_name_snapshot VARCHAR(200) DEFAULT NULL COMMENT ''执行当时的配置名称快照'' AFTER config_id'
);
PREPARE stmt_add_sync_log_config_name_snapshot FROM @add_sync_log_config_name_snapshot;
EXECUTE stmt_add_sync_log_config_name_snapshot;
DEALLOCATE PREPARE stmt_add_sync_log_config_name_snapshot;

SET @add_sync_log_provider_name_snapshot := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'provider_name_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN provider_name_snapshot VARCHAR(100) DEFAULT NULL COMMENT ''执行当时的平台名称快照'' AFTER provider_code'
);
PREPARE stmt_add_sync_log_provider_name_snapshot FROM @add_sync_log_provider_name_snapshot;
EXECUTE stmt_add_sync_log_provider_name_snapshot;
DEALLOCATE PREPARE stmt_add_sync_log_provider_name_snapshot;

SET @add_sync_log_task_type := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'task_type'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN task_type VARCHAR(50) DEFAULT NULL COMMENT ''任务类型'' AFTER external_no'
);
PREPARE stmt_add_sync_log_task_type FROM @add_sync_log_task_type;
EXECUTE stmt_add_sync_log_task_type;
DEALLOCATE PREPARE stmt_add_sync_log_task_type;

SET @add_sync_log_request_headers := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'request_headers'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN request_headers JSON DEFAULT NULL COMMENT ''请求头快照'' AFTER request_payload'
);
PREPARE stmt_add_sync_log_request_headers FROM @add_sync_log_request_headers;
EXECUTE stmt_add_sync_log_request_headers;
DEALLOCATE PREPARE stmt_add_sync_log_request_headers;

SET @add_sync_log_request_body := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'request_body'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN request_body JSON DEFAULT NULL COMMENT ''请求体快照'' AFTER request_headers'
);
PREPARE stmt_add_sync_log_request_body FROM @add_sync_log_request_body;
EXECUTE stmt_add_sync_log_request_body;
DEALLOCATE PREPARE stmt_add_sync_log_request_body;

SET @add_sync_log_result_message := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'result_message'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN result_message VARCHAR(1000) DEFAULT NULL COMMENT ''结果说明'' AFTER audit_log_id'
);
PREPARE stmt_add_sync_log_result_message FROM @add_sync_log_result_message;
EXECUTE stmt_add_sync_log_result_message;
DEALLOCATE PREPARE stmt_add_sync_log_result_message;

SET @add_sync_log_write_back_result := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'write_back_result'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN write_back_result VARCHAR(1000) DEFAULT NULL COMMENT ''业务回写结果说明'' AFTER result_message'
);
PREPARE stmt_add_sync_log_write_back_result FROM @add_sync_log_write_back_result;
EXECUTE stmt_add_sync_log_write_back_result;
DEALLOCATE PREPARE stmt_add_sync_log_write_back_result;

SET @add_sync_log_org_name_snapshot := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'org_name_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN org_name_snapshot VARCHAR(100) DEFAULT NULL COMMENT ''执行当时的组织名称快照'' AFTER org_id'
);
PREPARE stmt_add_sync_log_org_name_snapshot FROM @add_sync_log_org_name_snapshot;
EXECUTE stmt_add_sync_log_org_name_snapshot;
DEALLOCATE PREPARE stmt_add_sync_log_org_name_snapshot;

SET @add_sync_log_handle_status := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'handle_status'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN handle_status VARCHAR(50) DEFAULT NULL COMMENT ''人工处理闭环状态：pending_review/confirmed/ignored/rechecked'' AFTER tenant_id'
);
PREPARE stmt_add_sync_log_handle_status FROM @add_sync_log_handle_status;
EXECUTE stmt_add_sync_log_handle_status;
DEALLOCATE PREPARE stmt_add_sync_log_handle_status;

SET @add_sync_log_handled_by := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'handled_by'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN handled_by BIGINT DEFAULT NULL COMMENT ''最近处理人ID'' AFTER handle_status'
);
PREPARE stmt_add_sync_log_handled_by FROM @add_sync_log_handled_by;
EXECUTE stmt_add_sync_log_handled_by;
DEALLOCATE PREPARE stmt_add_sync_log_handled_by;

SET @add_sync_log_handled_by_name := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'handled_by_name'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN handled_by_name VARCHAR(100) DEFAULT NULL COMMENT ''最近处理人名称'' AFTER handled_by'
);
PREPARE stmt_add_sync_log_handled_by_name FROM @add_sync_log_handled_by_name;
EXECUTE stmt_add_sync_log_handled_by_name;
DEALLOCATE PREPARE stmt_add_sync_log_handled_by_name;

SET @add_sync_log_handled_at := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'handled_at'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN handled_at DATETIME DEFAULT NULL COMMENT ''最近处理时间'' AFTER handled_by_name'
);
PREPARE stmt_add_sync_log_handled_at FROM @add_sync_log_handled_at;
EXECUTE stmt_add_sync_log_handled_at;
DEALLOCATE PREPARE stmt_add_sync_log_handled_at;

SET @add_sync_log_handle_remark := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'smart_food_safety'
          AND table_name = 'sys_integration_sync_log'
          AND column_name = 'handle_remark'
    ),
    'SELECT 1',
    'ALTER TABLE sys_integration_sync_log ADD COLUMN handle_remark VARCHAR(500) DEFAULT NULL COMMENT ''人工处理备注'' AFTER handled_at'
);
PREPARE stmt_add_sync_log_handle_remark FROM @add_sync_log_handle_remark;
EXECUTE stmt_add_sync_log_handle_remark;
DEALLOCATE PREPARE stmt_add_sync_log_handle_remark;

UPDATE sys_integration_sync_log l
LEFT JOIN sys_integration_module_config c
       ON c.id = l.config_id
      AND c.deleted = 0
LEFT JOIN sys_integration_provider_template p
       ON p.provider_code = l.provider_code
      AND p.tenant_id = l.tenant_id
      AND p.deleted = 0
LEFT JOIN sys_organization o
       ON o.id = l.org_id
      AND o.deleted = 0
LEFT JOIN sys_integration_sync_task t
       ON t.id = l.task_id
      AND t.deleted = 0
SET l.config_name_snapshot = COALESCE(NULLIF(l.config_name_snapshot, ''), c.config_name),
    l.provider_name_snapshot = COALESCE(NULLIF(l.provider_name_snapshot, ''), p.provider_name),
    l.org_name_snapshot = COALESCE(NULLIF(l.org_name_snapshot, ''), o.org_name),
    l.task_type = COALESCE(NULLIF(l.task_type, ''), t.task_type),
    l.result_message = COALESCE(
        NULLIF(l.result_message, ''),
        NULLIF(t.result_message, ''),
        CASE
            WHEN l.sync_status = 'success' AND t.task_type = 'query_only' THEN '同步成功，当前任务仅查询记录结果，不覆盖业务数据'
            WHEN l.sync_status = 'success' THEN '同步成功并已回写业务数据'
            WHEN l.sync_status IN ('failed', 'no_data', 'mapping_missing') THEN l.error_message
            ELSE NULL
        END
    ),
    l.write_back_result = COALESCE(
        NULLIF(l.write_back_result, ''),
        CASE
            WHEN l.sync_status = 'success' AND t.task_type = 'query_only' THEN '本次任务为只查询不覆盖，未执行业务回写'
            WHEN l.sync_status = 'success' THEN '已回写业务数据'
            ELSE NULL
        END
    ),
    l.handle_status = COALESCE(
        NULLIF(l.handle_status, ''),
        CASE
            WHEN l.sync_status IN ('failed', 'no_data', 'mapping_missing') THEN 'pending_review'
            ELSE 'confirmed'
        END
    )
WHERE l.deleted = 0;
