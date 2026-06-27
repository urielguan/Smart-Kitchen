ALTER TABLE `device_alert`
    ADD COLUMN `closed_at` DATETIME DEFAULT NULL COMMENT '关闭时间' AFTER `review_remark`,
    ADD COLUMN `closed_by` BIGINT DEFAULT NULL COMMENT '关闭人ID' AFTER `closed_at`,
    ADD COLUMN `close_remark` VARCHAR(500) DEFAULT NULL COMMENT '关闭说明' AFTER `closed_by`,
    ADD COLUMN `archived_at` DATETIME DEFAULT NULL COMMENT '归档时间' AFTER `close_remark`,
    ADD COLUMN `archived_by` BIGINT DEFAULT NULL COMMENT '归档人ID' AFTER `archived_at`,
    ADD COLUMN `archive_remark` VARCHAR(500) DEFAULT NULL COMMENT '归档说明' AFTER `archived_by`;
