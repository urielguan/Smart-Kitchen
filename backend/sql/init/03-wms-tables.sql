-- ============================================================
-- 智慧厨房管理平台 - 数据库初始化脚本（第三部分）
-- 仓储管理模块（wms_）
-- ============================================================

USE `smart_food_safety`;

-- ---------------------------
-- 4.1 仓库表（wms_warehouse）
-- ---------------------------
DROP TABLE IF EXISTS `wms_warehouse`;
CREATE TABLE `wms_warehouse` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '仓库ID',
  `warehouse_code` VARCHAR(50) NOT NULL COMMENT '仓库编码',
  `warehouse_name` VARCHAR(100) NOT NULL COMMENT '仓库名称',
  `warehouse_type` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT '仓库类型：normal=常温，cold=冷藏，freeze=冷冻，dry=干货',
  `capacity` DECIMAL(10,2) DEFAULT NULL COMMENT '总容量',
  `capacity_unit` VARCHAR(20) DEFAULT '平方米' COMMENT '容量单位',
  `manager_id` BIGINT DEFAULT NULL COMMENT '仓库管理员ID',
  `manager_name` VARCHAR(50) DEFAULT NULL COMMENT '负责人姓名',
  `manager_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `address` VARCHAR(200) DEFAULT NULL COMMENT '仓库地址',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active=启用，inactive=停用，maintenance=维护中',
  `remark` VARCHAR(300) DEFAULT NULL COMMENT '备注',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `temperature_min` DECIMAL(10,2) DEFAULT NULL COMMENT '最低温度阈值(℃)',
  `temperature_max` DECIMAL(10,2) DEFAULT NULL COMMENT '最高温度阈值(℃)',
  `humidity_min` DECIMAL(10,2) DEFAULT NULL COMMENT '最低湿度阈值(%RH)',
  `humidity_max` DECIMAL(10,2) DEFAULT NULL COMMENT '最高湿度阈值(%RH)',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_warehouse_code` (`warehouse_code`, `org_id`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_warehouse_status` (`status`),
  KEY `idx_warehouse_org` (`org_id`),
  KEY `idx_warehouse_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓库表';

-- ---------------------------
-- 4.2 仓位表（wms_location）
-- ---------------------------
DROP TABLE IF EXISTS `wms_location`;
CREATE TABLE `wms_location` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '仓位ID',
  `location_code` VARCHAR(50) NOT NULL COMMENT '仓位编码',
  `location_name` VARCHAR(50) NOT NULL COMMENT '仓位名称',
  `location_type` VARCHAR(50) NULL COMMENT '仓位类型',
  `region_code` VARCHAR(50) NULL COMMENT '区域编码',
  `shelf_code` VARCHAR(50) NULL COMMENT '货架编码',
  `slot_code` VARCHAR(50) NULL COMMENT '货位编码',
  `warehouse_id` BIGINT NOT NULL COMMENT '所属仓库ID',
  `capacity` DECIMAL(10,3) DEFAULT NULL COMMENT '仓位容量',
  `capacity_unit` VARCHAR(20) DEFAULT '个' COMMENT '容量单位',
  `used_capacity` DECIMAL(10,3) DEFAULT 0.000 COMMENT '已用容量',
  `temperature_min` DECIMAL(5,1) DEFAULT NULL COMMENT '最低温度要求（℃）',
  `temperature_max` DECIMAL(5,1) DEFAULT NULL COMMENT '最高温度要求（℃）',
  `humidity_min` DECIMAL(5,1) DEFAULT NULL COMMENT '最低湿度要求（%）',
  `humidity_max` DECIMAL(5,1) DEFAULT NULL COMMENT '最高湿度要求（%）',
  `sensor_device_id` BIGINT DEFAULT NULL COMMENT '绑定的传感器设备ID',
  `material_types` VARCHAR(200) DEFAULT NULL COMMENT '允许存放的物料类型（逗号分隔）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'available' COMMENT '状态：available=可用，occupied=占用，maintenance=维护中',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_location_code` (`location_code`, `warehouse_id`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_location_warehouse` (`warehouse_id`),
  KEY `idx_location_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓位表';

-- ---------------------------
-- 4.3 物料主数据表（wms_material）
-- ---------------------------
DROP TABLE IF EXISTS `wms_material`;
CREATE TABLE `wms_material` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '物料ID',
  `material_code` VARCHAR(50) NOT NULL COMMENT '物料编码',
  `material_name` VARCHAR(100) NOT NULL COMMENT '物料名称',
  `material_category` VARCHAR(50) DEFAULT NULL COMMENT '物料分类（如：蔬菜/肉类/调料）',
  `unit` VARCHAR(20) NOT NULL COMMENT '基本单位（kg/个/箱）',
  `spec` VARCHAR(100) DEFAULT NULL COMMENT '规格说明',
  `shelf_life_days` INT DEFAULT NULL COMMENT '保质期（天）',
  `min_stock` DECIMAL(10,3) DEFAULT NULL COMMENT '最低库存预警量',
  `max_stock` DECIMAL(10,3) DEFAULT NULL COMMENT '最高库存量',
  `near_expiry_days` INT DEFAULT 7 COMMENT '临期提醒天数（到期前N天提醒）',
  `warning_days` INT DEFAULT 30 COMMENT '预警天数（采购预警）',
  `storage_conditions` VARCHAR(200) DEFAULT NULL COMMENT '存储条件说明',
  `storage_type` VARCHAR(20) DEFAULT 'normal' COMMENT '存储类型：normal=常温，cold=冷藏，freeze=冷冻',
  `image_url` VARCHAR(500) DEFAULT NULL COMMENT '物料图片URL',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active=启用，inactive=停用',
  `remark` VARCHAR(300) DEFAULT NULL COMMENT '备注',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_code` (`material_code`, `org_id`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_material_category` (`material_category`),
  KEY `idx_material_status` (`status`),
  KEY `idx_material_org` (`org_id`),
  KEY `idx_material_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料主数据表';

-- ---------------------------
-- 4.4 库存明细表（wms_inventory）
-- ---------------------------
DROP TABLE IF EXISTS `wms_inventory`;
CREATE TABLE `wms_inventory` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '库存批次ID',
  `warehouse_id` BIGINT NOT NULL COMMENT '仓库ID',
  `location_id` BIGINT DEFAULT NULL COMMENT '仓位ID',
  `material_id` BIGINT NOT NULL COMMENT '物料ID',
  `material_name` VARCHAR(100) NOT NULL COMMENT '物料名称（冗余）',
  `spec` VARCHAR(100) DEFAULT NULL COMMENT '规格说明（冗余）',
  `batch_no` VARCHAR(100) DEFAULT NULL COMMENT '批次号',
  `trace_batch_id` VARCHAR(100) DEFAULT NULL COMMENT '溯源批次码',
  `quantity` DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '当前库存数量',
  `unit` VARCHAR(20) NOT NULL COMMENT '单位（冗余）',
  `unit_cost` DECIMAL(10,2) DEFAULT NULL COMMENT '单位成本',
  `total_cost` DECIMAL(12,2) DEFAULT NULL COMMENT '总库存价值',
  `production_date` DATE DEFAULT NULL COMMENT '生产日期',
  `expiry_date` DATE DEFAULT NULL COMMENT '到期日期',
  `status` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT '状态：normal=正常，warning=临期预警，expired=已过期，locked=已锁定（盘点中）',
  `source_type` VARCHAR(20) NOT NULL COMMENT '入库来源：purchase=采购入库，return=退货入库，transfer=调拨入库，stocktake=盘盈',
  `source_id` BIGINT DEFAULT NULL COMMENT '来源单据ID',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_inv_warehouse` (`warehouse_id`),
  KEY `idx_inv_material` (`material_id`),
  KEY `idx_inv_expiry` (`expiry_date`),
  KEY `idx_inv_status` (`status`),
  KEY `idx_inv_org` (`org_id`),
  KEY `idx_inv_batch` (`trace_batch_id`),
  KEY `idx_inv_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存明细表（按批次）';

-- ---------------------------
-- 4.5 入库单表（wms_inbound_order）
-- ---------------------------
DROP TABLE IF EXISTS `wms_inbound_order`;
CREATE TABLE `wms_inbound_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '入库单ID',
  `inbound_no` VARCHAR(50) NOT NULL COMMENT '入库单编号',
  `source_type` VARCHAR(20) NOT NULL COMMENT '来源类型：purchase=采购入库，return=退货入库，transfer=调拨入库，stocktake=盘盈',
  `source_id` BIGINT DEFAULT NULL COMMENT '来源单据ID（如采购订单ID）',
  `supplier_id` BIGINT DEFAULT NULL COMMENT '供应商ID',
  `supplier_name` VARCHAR(100) DEFAULT NULL COMMENT '供应商名称',
  `source_order_id` BIGINT DEFAULT NULL COMMENT '来源订单ID',
  `source_order_no` VARCHAR(50) DEFAULT NULL COMMENT '来源订单编号',
  `warehouse_id` BIGINT NOT NULL COMMENT '入库仓库ID',
  `total_amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '入库总价值',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `attachments` JSON DEFAULT NULL COMMENT '附件列表',
  `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态：draft=草稿，pending=待审核，approved=已审核，rejected=已驳回',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `post_status` VARCHAR(20) NOT NULL DEFAULT 'none' COMMENT '过账状态：none=未过账，success=成功，failed=失败',
  `post_error_message` VARCHAR(500) DEFAULT NULL COMMENT '过账失败原因',
  `submitted_at` DATETIME DEFAULT NULL COMMENT '提交时间',
  `submitted_by` BIGINT DEFAULT NULL COMMENT '提交人ID',
  `submitter_name` VARCHAR(50) DEFAULT NULL COMMENT '提交人姓名',
  `approved_by` BIGINT DEFAULT NULL COMMENT '审核人ID',
  `approved_at` DATETIME DEFAULT NULL COMMENT '审核时间',
  `approve_remark` VARCHAR(300) DEFAULT NULL COMMENT '审批备注',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `org_name` VARCHAR(100) DEFAULT NULL COMMENT '所属组织名称',
  `receiving_org_id` BIGINT DEFAULT NULL COMMENT '入库组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inbound_no` (`inbound_no`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_inbound_status` (`status`),
  KEY `idx_inbound_warehouse` (`warehouse_id`),
  KEY `idx_inbound_source` (`source_type`, `source_id`),
  KEY `idx_inbound_org` (`org_id`),
  KEY `idx_inbound_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库单表';

-- ---------------------------
-- 4.6 入库单明细表（wms_inbound_order_item）
-- ---------------------------
DROP TABLE IF EXISTS `wms_inbound_order_item`;
CREATE TABLE `wms_inbound_order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `inbound_id` BIGINT NOT NULL COMMENT '入库单ID',
  `warehouse_id` BIGINT DEFAULT NULL COMMENT '入库仓库ID',
  `material_id` BIGINT NOT NULL COMMENT '物料ID',
  `material_name` VARCHAR(100) NOT NULL COMMENT '物料名称（冗余）',
  `spec` VARCHAR(100) DEFAULT NULL COMMENT '规格说明（冗余）',
  `unit` VARCHAR(20) NOT NULL COMMENT '单位（冗余）',
  `location_id` BIGINT DEFAULT NULL COMMENT '仓位ID',
  `quantity` DECIMAL(10,3) NOT NULL COMMENT '入库数量',
  `unit_cost` DECIMAL(10,2) DEFAULT NULL COMMENT '单价',
  `total_cost` DECIMAL(12,2) DEFAULT NULL COMMENT '小计',
  `batch_no` VARCHAR(100) DEFAULT NULL COMMENT '批次号',
  `production_date` DATE DEFAULT NULL COMMENT '生产日期',
  `expiry_date` DATE DEFAULT NULL COMMENT '到期日期',
  `trace_batch_id` VARCHAR(100) DEFAULT NULL COMMENT '溯源批次码',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_inbound_item_order` (`inbound_id`),
  KEY `idx_inbound_item_warehouse` (`warehouse_id`),
  KEY `idx_inbound_item_material` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库单明细表';

-- ---------------------------
-- 4.7 出库单表（wms_outbound_order）
-- ---------------------------
DROP TABLE IF EXISTS `wms_outbound_order`;
CREATE TABLE `wms_outbound_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '出库单ID',
  `outbound_no` VARCHAR(50) NOT NULL COMMENT '出库单编号',
  `outbound_type` VARCHAR(20) NOT NULL COMMENT '出库类型：requisition=领用，sales=销售，return=退货，transfer=调拨，loss=报损，donation=捐赠，scrap=报废，other=其他',
  `warehouse_id` BIGINT NOT NULL COMMENT '出库仓库ID',
  `requester_id` BIGINT DEFAULT NULL COMMENT '领用人ID',
  `department` VARCHAR(100) DEFAULT NULL COMMENT '领用部门',
  `purpose` VARCHAR(200) DEFAULT NULL COMMENT '用途说明',
  `total_amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '出库总价值',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `attachments` JSON DEFAULT NULL COMMENT '附件列表',
  `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态：draft=草稿，pending=待审核，approved=已审核，completed=已出库，rejected=已驳回',
  `submitted_at` DATETIME DEFAULT NULL COMMENT '提交时间',
  `submitted_by` BIGINT DEFAULT NULL COMMENT '提交人ID',
  `submitter_name` VARCHAR(64) DEFAULT NULL COMMENT '提交人姓名',
  `approved_by` BIGINT DEFAULT NULL COMMENT '审核人ID',
  `approved_at` DATETIME DEFAULT NULL COMMENT '审核时间',
  `completed_at` DATETIME DEFAULT NULL COMMENT '出库完成时间',
  `source_order_id` BIGINT DEFAULT NULL COMMENT '来源单据ID（如菜谱计划ID）',
  `source_order_no` VARCHAR(64) DEFAULT NULL COMMENT '来源单据编号（如菜谱计划编号）',
  `target_org_id` BIGINT DEFAULT NULL COMMENT '领用组织ID',
  `approve_remark` VARCHAR(300) DEFAULT NULL COMMENT '审批备注',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbound_no` (`outbound_no`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_outbound_status` (`status`),
  KEY `idx_outbound_type` (`outbound_type`),
  KEY `idx_outbound_warehouse` (`warehouse_id`),
  KEY `idx_outbound_org` (`org_id`),
  KEY `idx_outbound_created_at` (`created_at`),
  KEY `idx_outbound_completed_at` (`completed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出库单表';

-- ---------------------------
-- 4.8 出库单明细表（wms_outbound_order_item）
-- ---------------------------
DROP TABLE IF EXISTS `wms_outbound_order_item`;
CREATE TABLE `wms_outbound_order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `outbound_id` BIGINT NOT NULL COMMENT '出库单ID',
  `material_id` BIGINT NOT NULL COMMENT '物料ID',
  `material_name` VARCHAR(100) NOT NULL COMMENT '物料名称（冗余）',
  `spec` VARCHAR(100) DEFAULT NULL COMMENT '规格说明（冗余）',
  `unit` VARCHAR(20) NOT NULL COMMENT '单位（冗余）',
  `warehouse_id` BIGINT DEFAULT NULL COMMENT '出库仓库ID',
  `location_id` BIGINT DEFAULT NULL COMMENT '出库仓位ID',
  `quantity` DECIMAL(10,3) NOT NULL COMMENT '出库数量',
  `unit_cost` DECIMAL(10,2) DEFAULT NULL COMMENT '单价',
  `total_cost` DECIMAL(12,2) DEFAULT NULL COMMENT '小计',
  `batch_no` VARCHAR(100) DEFAULT NULL COMMENT '批次号',
  `inventory_id` BIGINT DEFAULT NULL COMMENT '关联库存批次ID',
  `expiry_date` DATE DEFAULT NULL COMMENT '到期日期（冗余）',
  `purpose` VARCHAR(200) DEFAULT NULL COMMENT '出库用途',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_outbound_item_order` (`outbound_id`),
  KEY `idx_outbound_item_material` (`material_id`),
  KEY `idx_outbound_item_inventory` (`inventory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出库单明细表';

-- ---------------------------
-- 4.9 出库分配明细表（wms_outbound_order_allocation）
-- ---------------------------
DROP TABLE IF EXISTS `wms_outbound_order_allocation`;
CREATE TABLE `wms_outbound_order_allocation` (
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

-- ---------------------------
-- 4.10 盘点单表（wms_stocktake_order）
-- ---------------------------
DROP TABLE IF EXISTS `wms_stocktake_order`;
CREATE TABLE `wms_stocktake_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '盘点单ID',
  `stocktake_no` VARCHAR(50) NOT NULL COMMENT '盘点单编号',
  `warehouse_id` BIGINT NOT NULL COMMENT '盘点仓库ID',
  `location_id` BIGINT DEFAULT NULL COMMENT '盘点仓位ID',
  `stocktake_type` VARCHAR(20) NOT NULL DEFAULT 'regular' COMMENT '盘点类型：regular=周期盘点，emergency=异常触发',
  `stocktake_date` DATE DEFAULT NULL COMMENT '盘点日期',
  `start_at` DATETIME DEFAULT NULL COMMENT '盘点开始时间',
  `end_at` DATETIME DEFAULT NULL COMMENT '盘点结束时间',
  `checker_id` BIGINT DEFAULT NULL COMMENT '盘点人ID',
  `checker_name` VARCHAR(100) DEFAULT NULL COMMENT '盘点人姓名',
  `item_count` INT NOT NULL DEFAULT 0 COMMENT '盘点明细数量',
  `diff_qty_total` DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '差异总数量',
  `profit_amount_total` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '盘盈总金额',
  `loss_amount_total` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '盘亏总金额',
  `diff_rate` DECIMAL(8,4) DEFAULT NULL COMMENT '差异率',
  `surplus_qty` DECIMAL(10,3) DEFAULT 0.000 COMMENT '盘盈数量',
  `deficit_qty` DECIMAL(10,3) DEFAULT 0.000 COMMENT '盘亏数量',
  `surplus_amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '盘盈金额',
  `deficit_amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '盘亏金额',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `attachments` JSON DEFAULT NULL COMMENT '附件列表',
  `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态：draft=待完成，pending=待审核，rejected=已驳回，completed=已完成，voided=已作废',
  `approved_by` BIGINT DEFAULT NULL COMMENT '审核人ID',
  `approved_at` DATETIME DEFAULT NULL COMMENT '审核时间',
  `approve_remark` VARCHAR(300) DEFAULT NULL COMMENT '审批备注',
  `reject_remark` VARCHAR(300) DEFAULT NULL COMMENT '驳回原因',
  `void_reason` VARCHAR(300) DEFAULT NULL COMMENT '作废原因',
  `version_no` INT NOT NULL DEFAULT 1 COMMENT '提交版本号',
  `warehouse_ids` JSON DEFAULT NULL COMMENT '多仓库盘点时的仓库ID列表',
  `location_ids` JSON DEFAULT NULL COMMENT '多仓位盘点时的仓位ID列表',
  `submitted_by` BIGINT DEFAULT NULL COMMENT '提交人ID',
  `submitted_at` DATETIME DEFAULT NULL COMMENT '提交时间',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stocktake_no` (`stocktake_no`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_stocktake_status` (`status`),
  KEY `idx_stocktake_warehouse` (`warehouse_id`),
  KEY `idx_stocktake_org` (`org_id`),
  KEY `idx_stocktake_created_at` (`created_at`),
  KEY `idx_stocktake_range_lock` (`warehouse_id`, `location_id`, `stocktake_date`, `status`, `deleted`),
  KEY `idx_stocktake_checker_date` (`checker_id`, `stocktake_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点单表';

-- ---------------------------
-- 4.11 盘点单明细表（wms_stocktake_order_item）
-- ---------------------------
DROP TABLE IF EXISTS `wms_stocktake_order_item`;
CREATE TABLE `wms_stocktake_order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `stocktake_id` BIGINT NOT NULL COMMENT '盘点单ID',
  `material_id` BIGINT NOT NULL COMMENT '物料ID',
  `material_name` VARCHAR(100) NOT NULL COMMENT '物料名称（冗余）',
  `spec` VARCHAR(100) DEFAULT NULL COMMENT '规格说明（冗余）',
  `unit` VARCHAR(20) NOT NULL COMMENT '单位（冗余）',
  `warehouse_id` BIGINT NOT NULL COMMENT '所在仓库ID',
  `location_id` BIGINT DEFAULT NULL COMMENT '所在仓位ID',
  `batch_no` VARCHAR(100) DEFAULT NULL COMMENT '批次号',
  `inventory_id` BIGINT DEFAULT NULL COMMENT '关联库存批次ID',
  `expiry_date` DATE DEFAULT NULL COMMENT '到期日期（冗余）',
  `system_qty` DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '系统库存数量',
  `actual_qty` DECIMAL(10,3) DEFAULT NULL COMMENT '实际盘点数量',
  `diff_qty` DECIMAL(10,3) DEFAULT NULL COMMENT '差异数量（actual_qty - system_qty）',
  `unit_cost` DECIMAL(10,2) DEFAULT NULL COMMENT '单价（用于差异金额计算）',
  `diff_amount` DECIMAL(12,2) DEFAULT NULL COMMENT '差异金额（diff_qty × unit_cost）',
  `diff_type` VARCHAR(20) DEFAULT NULL COMMENT '差异类型：surplus=盘盈，deficit=盘亏，normal=正常',
  `diff_direction` VARCHAR(20) DEFAULT NULL COMMENT '差异方向：surplus/deficit/normal',
  `diff_reason` VARCHAR(100) DEFAULT NULL COMMENT '差异原因',
  `recognition_source` VARCHAR(50) DEFAULT NULL COMMENT '识别来源：manual/ai/device/import',
  `ai_confidence` DECIMAL(5,4) DEFAULT NULL COMMENT 'AI识别置信度',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `line_remark` VARCHAR(200) DEFAULT NULL COMMENT '行备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_stocktake_item_order` (`stocktake_id`),
  KEY `idx_stocktake_item_material` (`material_id`),
  KEY `idx_stocktake_item_inventory` (`inventory_id`),
  KEY `idx_stocktake_item_diff_type` (`diff_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点单明细表';

-- ---------------------------
-- 4.12 盘点单版本表（wms_stocktake_order_version）
-- ---------------------------
DROP TABLE IF EXISTS `wms_stocktake_order_version`;
CREATE TABLE `wms_stocktake_order_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  `stocktake_id` BIGINT NOT NULL COMMENT '盘点单ID',
  `version_no` INT NOT NULL COMMENT '版本号',
  `stocktake_no` VARCHAR(50) NOT NULL COMMENT '盘点单编号',
  `warehouse_id` BIGINT NOT NULL COMMENT '盘点仓库ID',
  `location_id` BIGINT DEFAULT NULL COMMENT '盘点仓位ID',
  `stocktake_type` VARCHAR(20) NOT NULL COMMENT '盘点类型',
  `stocktake_date` DATE DEFAULT NULL COMMENT '盘点日期',
  `start_at` DATETIME DEFAULT NULL COMMENT '盘点开始时间',
  `end_at` DATETIME DEFAULT NULL COMMENT '盘点结束时间',
  `checker_id` BIGINT DEFAULT NULL COMMENT '盘点人ID',
  `checker_name` VARCHAR(100) DEFAULT NULL COMMENT '盘点人姓名',
  `item_count` INT NOT NULL DEFAULT 0 COMMENT '盘点明细数量',
  `diff_qty_total` DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '差异总数量',
  `profit_amount_total` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '盘盈总金额',
  `loss_amount_total` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '盘亏总金额',
  `diff_rate` DECIMAL(8,4) DEFAULT NULL COMMENT '差异率',
  `surplus_qty` DECIMAL(10,3) DEFAULT 0.000 COMMENT '盘盈数量',
  `deficit_qty` DECIMAL(10,3) DEFAULT 0.000 COMMENT '盘亏数量',
  `surplus_amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '盘盈金额',
  `deficit_amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '盘亏金额',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `attachments` JSON DEFAULT NULL COMMENT '附件列表',
  `status` VARCHAR(20) NOT NULL COMMENT '状态',
  `approved_by` BIGINT DEFAULT NULL COMMENT '审核人ID',
  `approved_at` DATETIME DEFAULT NULL COMMENT '审核时间',
  `approve_remark` VARCHAR(300) DEFAULT NULL COMMENT '审批备注',
  `reject_remark` VARCHAR(300) DEFAULT NULL COMMENT '驳回原因',
  `void_reason` VARCHAR(300) DEFAULT NULL COMMENT '作废原因',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `submitted_by` BIGINT DEFAULT NULL COMMENT '提交人ID',
  `submitted_at` DATETIME DEFAULT NULL COMMENT '提交时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stocktake_order_version` (`stocktake_id`, `version_no`),
  KEY `idx_stocktake_order_version_lookup` (`stocktake_id`, `version_no`, `created_at`),
  KEY `idx_stocktake_order_version_org` (`org_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点单版本表';

-- ---------------------------
-- 4.13 盘点单明细版本表（wms_stocktake_order_item_version）
-- ---------------------------
DROP TABLE IF EXISTS `wms_stocktake_order_item_version`;
CREATE TABLE `wms_stocktake_order_item_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细版本ID',
  `stocktake_version_id` BIGINT NOT NULL COMMENT '盘点单版本ID',
  `stocktake_id` BIGINT NOT NULL COMMENT '盘点单ID',
  `stocktake_item_id` BIGINT DEFAULT NULL COMMENT '盘点单明细ID',
  `version_no` INT NOT NULL COMMENT '版本号',
  `material_id` BIGINT NOT NULL COMMENT '物料ID',
  `material_name` VARCHAR(100) NOT NULL COMMENT '物料名称（冗余）',
  `spec` VARCHAR(100) DEFAULT NULL COMMENT '规格说明（冗余）',
  `unit` VARCHAR(20) NOT NULL COMMENT '单位（冗余）',
  `warehouse_id` BIGINT NOT NULL COMMENT '所在仓库ID',
  `location_id` BIGINT DEFAULT NULL COMMENT '所在仓位ID',
  `batch_no` VARCHAR(100) DEFAULT NULL COMMENT '批次号',
  `inventory_id` BIGINT DEFAULT NULL COMMENT '关联库存批次ID',
  `expiry_date` DATE DEFAULT NULL COMMENT '到期日期（冗余）',
  `system_qty` DECIMAL(10,3) NOT NULL DEFAULT 0.000 COMMENT '系统库存数量',
  `actual_qty` DECIMAL(10,3) DEFAULT NULL COMMENT '实际盘点数量',
  `diff_qty` DECIMAL(10,3) DEFAULT NULL COMMENT '差异数量',
  `unit_cost` DECIMAL(10,2) DEFAULT NULL COMMENT '单价',
  `diff_amount` DECIMAL(12,2) DEFAULT NULL COMMENT '差异金额',
  `diff_type` VARCHAR(20) DEFAULT NULL COMMENT '差异类型',
  `diff_direction` VARCHAR(20) DEFAULT NULL COMMENT '差异方向',
  `diff_reason` VARCHAR(100) DEFAULT NULL COMMENT '差异原因',
  `recognition_source` VARCHAR(50) DEFAULT NULL COMMENT '识别来源',
  `ai_confidence` DECIMAL(5,4) DEFAULT NULL COMMENT 'AI识别置信度',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `line_remark` VARCHAR(200) DEFAULT NULL COMMENT '行备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_stocktake_item_version_lookup` (`stocktake_version_id`, `version_no`),
  KEY `idx_stocktake_item_version_order` (`stocktake_id`, `version_no`),
  KEY `idx_stocktake_item_version_inventory` (`inventory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点单明细版本表';

-- ---------------------------
-- 4.14 盘点操作日志表（wms_stocktake_operation_log）
-- ---------------------------
DROP TABLE IF EXISTS `wms_stocktake_operation_log`;
CREATE TABLE `wms_stocktake_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `stocktake_id` BIGINT NOT NULL COMMENT '盘点单ID',
  `action` VARCHAR(50) NOT NULL COMMENT '操作编码',
  `action_name` VARCHAR(100) NOT NULL COMMENT '操作名称',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人姓名',
  `content` VARCHAR(1000) DEFAULT NULL COMMENT '操作内容',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_stocktake_log_lookup` (`stocktake_id`, `created_at`),
  KEY `idx_stocktake_log_action` (`stocktake_id`, `action`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点操作日志表';

-- ---------------------------
-- 4.15 仓位面积流水表（wms_location_area_ledger）
-- ---------------------------
DROP TABLE IF EXISTS `wms_location_area_ledger`;
CREATE TABLE `wms_location_area_ledger` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_type` VARCHAR(64) NOT NULL COMMENT '业务类型',
  `biz_action` VARCHAR(64) NOT NULL COMMENT '业务动作',
  `biz_order_id` BIGINT NOT NULL COMMENT '业务单据ID',
  `biz_item_id` BIGINT NULL COMMENT '业务明细ID',
  `warehouse_id` BIGINT NOT NULL COMMENT '仓库ID',
  `location_id` BIGINT NOT NULL COMMENT '仓位ID',
  `material_id` BIGINT NULL COMMENT '物料ID',
  `effective_quantity` DECIMAL(18,4) NULL COMMENT '有效数量快照',
  `area_coefficient_snapshot` DECIMAL(18,4) NULL COMMENT '面积系数快照',
  `area_delta` DECIMAL(18,4) NULL COMMENT '面积变动量',
  `direction` VARCHAR(16) NOT NULL COMMENT '方向 increase/decrease/none',
  `validation_mode` VARCHAR(32) NOT NULL COMMENT '校验模式 strict/skipped',
  `skip_reason` VARCHAR(255) NULL COMMENT '跳过原因',
  `reversed_ledger_id` BIGINT NULL COMMENT '冲销对应流水ID',
  `org_id` BIGINT NOT NULL COMMENT '组织ID',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `created_by` BIGINT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_location_area_ledger_biz_item` (`biz_type`, `biz_action`, `biz_order_id`, `biz_item_id`),
  KEY `idx_location_area_ledger_location_material` (`location_id`, `material_id`),
  KEY `idx_location_area_ledger_reversed` (`reversed_ledger_id`),
  KEY `idx_location_area_ledger_org_tenant` (`org_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓位面积流水表';
