CREATE TABLE IF NOT EXISTS `wms_outbound_import_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_no` VARCHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `file_name` VARCHAR(255) DEFAULT NULL,
    `error_file_name` VARCHAR(255) DEFAULT NULL,
    `total_count` INT NOT NULL DEFAULT 0,
    `success_count` INT NOT NULL DEFAULT 0,
    `failure_count` INT NOT NULL DEFAULT 0,
    `org_id` BIGINT DEFAULT NULL,
    `tenant_id` BIGINT DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by` BIGINT DEFAULT NULL,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wms_outbound_import_task_task_no` (`task_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `wms_outbound_import_task_row` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `row_number` INT NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `error_field` VARCHAR(64) DEFAULT NULL,
    `error_reason` VARCHAR(255) DEFAULT NULL,
    `raw_payload` JSON DEFAULT NULL,
    `org_id` BIGINT DEFAULT NULL,
    `tenant_id` BIGINT DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by` BIGINT DEFAULT NULL,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_wms_outbound_import_task_row_task_id` (`task_id`),
    CONSTRAINT `fk_wms_outbound_import_task_row_task_id`
        FOREIGN KEY (`task_id`) REFERENCES `wms_outbound_import_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
