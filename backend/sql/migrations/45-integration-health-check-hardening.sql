USE `smart_food_safety`;

-- 45: 第三方连接测试与健康检查加固
-- 1) 新增独立健康检查测试日志表，避免测试连接覆盖真实同步状态
-- 2) 保留鉴权/接口/回调三段测试证据链，支持历史回看与排障

CREATE TABLE IF NOT EXISTS `sys_integration_health_check_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_id` BIGINT NOT NULL COMMENT '接入配置ID',
  `config_name_snapshot` VARCHAR(200) DEFAULT NULL COMMENT '测试当时的配置名称快照',
  `biz_module` VARCHAR(100) NOT NULL COMMENT '业务模块',
  `biz_scene` VARCHAR(100) NOT NULL COMMENT '业务场景',
  `provider_code` VARCHAR(100) NOT NULL COMMENT '平台编码',
  `provider_name_snapshot` VARCHAR(100) DEFAULT NULL COMMENT '测试当时的平台名称快照',
  `test_status` VARCHAR(30) NOT NULL COMMENT '测试状态：success/warning/failed',
  `auth_success` TINYINT DEFAULT NULL COMMENT '鉴权是否成功：1成功/0失败',
  `reachable` TINYINT DEFAULT NULL COMMENT '第三方接口是否可达：1成功/0失败',
  `callback_reachable` TINYINT DEFAULT NULL COMMENT '回调地址是否可达：1成功/0失败',
  `auth_message` VARCHAR(1000) DEFAULT NULL COMMENT '鉴权说明',
  `reachability_message` VARCHAR(1000) DEFAULT NULL COMMENT '接口连通性说明',
  `callback_message` VARCHAR(1000) DEFAULT NULL COMMENT '回调连通性说明',
  `test_message` VARCHAR(1000) DEFAULT NULL COMMENT '测试结果汇总',
  `error_code` VARCHAR(100) DEFAULT NULL COMMENT '错误码',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `request_payload` LONGTEXT DEFAULT NULL COMMENT '请求报文快照',
  `request_headers` LONGTEXT DEFAULT NULL COMMENT '请求头快照',
  `request_body` LONGTEXT DEFAULT NULL COMMENT '请求体快照',
  `response_payload` LONGTEXT DEFAULT NULL COMMENT '响应报文快照',
  `operator_id` BIGINT DEFAULT NULL COMMENT '测试人ID',
  `operator_name` VARCHAR(100) DEFAULT NULL COMMENT '测试人名称',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `org_name_snapshot` VARCHAR(100) DEFAULT NULL COMMENT '测试当时的组织名称快照',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_integration_health_config` (`config_id`),
  KEY `idx_integration_health_provider` (`provider_code`),
  KEY `idx_integration_health_org` (`org_id`),
  KEY `idx_integration_health_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方健康检查测试日志表';
