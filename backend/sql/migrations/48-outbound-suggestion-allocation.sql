USE `smart_food_safety`;

CREATE TABLE IF NOT EXISTS `wms_outbound_order_allocation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分配ID',
  `outbound_id` BIGINT NOT NULL COMMENT '出库单ID',
  `outbound_item_id` BIGINT NOT NULL COMMENT '出库单明细ID',
  `source_stock_detail_id` BIGINT NOT NULL COMMENT '来源库存明细ID',
  `warehouse_id` BIGINT NOT NULL COMMENT '分配仓库ID',
  `location_id` BIGINT DEFAULT NULL COMMENT '分配仓位ID',
  `batch_no` VARCHAR(100) DEFAULT NULL COMMENT '批次号',
  `production_date` DATE DEFAULT NULL COMMENT '生产日期',
  `expiry_date` DATE DEFAULT NULL COMMENT '到期日期',
  `quantity` DECIMAL(10,3) NOT NULL COMMENT '分配数量',
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT '来源类型：manual=手工维护，suggestion_apply=建议应用',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_outbound_allocation_order` (`outbound_id`),
  KEY `idx_outbound_allocation_item` (`outbound_item_id`),
  KEY `idx_outbound_allocation_stock` (`source_stock_detail_id`),
  KEY `idx_outbound_allocation_scope` (`org_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出库分配明细表';
