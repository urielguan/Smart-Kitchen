ALTER TABLE `wms_inbound_order`
    ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `status`,
    ADD COLUMN `post_status` VARCHAR(32) NOT NULL DEFAULT 'unposted' COMMENT '过账状态' AFTER `version`,
    ADD COLUMN `post_error_message` VARCHAR(1000) NULL COMMENT '过账错误信息' AFTER `post_status`;

CREATE TABLE IF NOT EXISTS `wms_inbound_order_idempotency` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `inbound_order_id` BIGINT NOT NULL COMMENT '入库单ID',
    `action` VARCHAR(32) NOT NULL COMMENT '写操作类型',
    `idempotency_key` VARCHAR(64) NOT NULL COMMENT '幂等键',
    `request_version` BIGINT NULL COMMENT '请求版本号',
    `response_version` BIGINT NULL COMMENT '响应版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inbound_order_action_idempotency` (`tenant_id`, `inbound_order_id`, `action`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入库单写操作幂等记录';
