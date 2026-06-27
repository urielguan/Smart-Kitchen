USE `smart_food_safety`;

-- 统一历史证件号口径：去掉首尾空格，空串转 NULL，避免条件唯一索引下将脏数据误识别为重复值
UPDATE `scm_supplier`
SET `license_no` = NULLIF(TRIM(`license_no`), '')
WHERE `license_no` IS NOT NULL;

UPDATE `scm_supplier`
SET `food_license_no` = NULLIF(TRIM(`food_license_no`), '')
WHERE `food_license_no` IS NOT NULL;

ALTER TABLE `scm_supplier`
  ADD UNIQUE KEY `uk_tenant_business_license_no` (`tenant_id`, `license_no`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END)) COMMENT '租户+营业执照编号软删除条件唯一约束',
  ADD UNIQUE KEY `uk_tenant_food_license_no` (`tenant_id`, `food_license_no`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END)) COMMENT '租户+食品许可证号软删除条件唯一约束';
