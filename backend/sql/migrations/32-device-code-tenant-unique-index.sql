-- 32: 设备编码唯一约束从组织级改为租户级
-- 原索引: uk_device_code(device_code, org_id, CASE WHEN deleted=0 THEN 0 ELSE NULL END)
-- 新索引: uk_device_code_tenant(device_code, tenant_id, CASE WHEN deleted=0 THEN 0 ELSE NULL END)

ALTER TABLE `device_info` DROP INDEX `uk_device_code`;
ALTER TABLE `device_info` ADD UNIQUE KEY `uk_device_code_tenant` (`device_code`, `tenant_id`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END));

-- 同步更新 org 索引为 tenant 索引（辅助按租户查询）
ALTER TABLE `device_info` DROP INDEX `idx_di_org`;
ALTER TABLE `device_info` ADD KEY `idx_di_tenant` (`tenant_id`);
