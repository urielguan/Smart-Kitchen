USE `smart_food_safety`;

ALTER TABLE `scm_supplier`
  DROP INDEX `uk_supplier_code`;

ALTER TABLE `scm_supplier`
  ADD UNIQUE KEY `uk_tenant_supplier_code` (`tenant_id`, `supplier_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END)) COMMENT '租户+供应商编码软删除唯一约束',
  ADD UNIQUE KEY `uk_tenant_supplier_name` (`tenant_id`, `supplier_name`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END)) COMMENT '租户+供应商名称软删除唯一约束';
