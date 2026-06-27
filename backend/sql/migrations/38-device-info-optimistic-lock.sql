-- 设备表增加乐观锁版本号
ALTER TABLE device_info ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号';
