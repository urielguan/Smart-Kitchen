-- ============================================================
-- 智慧厨房管理平台 - 数据库初始化脚本（第四部分）
-- 菜谱营养管理模块（recipe_）
-- 更新日期: 2026-03-26
-- ============================================================

USE `smart_food_safety`;

-- =====================================================
-- 5.1 菜谱类别表
-- =====================================================
DROP TABLE IF EXISTS `recipe_plan_adjustment`;
DROP TABLE IF EXISTS `recipe_plan_item`;
DROP TABLE IF EXISTS `recipe_plan`;
DROP TABLE IF EXISTS `recipe_ingredient`;
DROP TABLE IF EXISTS `recipe`;
DROP TABLE IF EXISTS `recipe_category`;

CREATE TABLE `recipe_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '类别ID',
    `category_code` VARCHAR(50) NOT NULL COMMENT '类别编码',
    `category_name` VARCHAR(100) NOT NULL COMMENT '类别名称',
    `icon` VARCHAR(255) DEFAULT NULL COMMENT '类别图标',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` VARCHAR(20) DEFAULT 'active' COMMENT '状态: active=启用, inactive=停用',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID',
    `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recipe_category_tenant_code_deleted` (`tenant_id`, `category_code`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
    KEY `idx_org_id` (`org_id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜谱类别表';

-- 插入默认类别数据
INSERT INTO `recipe_category` (`category_code`, `category_name`, `icon`, `sort_order`, `status`, `remark`, `org_id`, `tenant_id`) VALUES
('STAPLE', '主食', '🍚', 1, 'active', '系统内置', 0, 0),
('MAIN_DISH', '荤菜', '🍖', 2, 'active', '系统内置', 0, 0),
('SOUP', '汤品', '🥣', 3, 'active', '系统内置', 0, 0),
('SIDE_DISH', '素菜', '🥗', 4, 'active', '系统内置', 0, 0),
('DESSERT', '甜点', '🍰', 5, 'active', '系统内置', 0, 0);

-- =====================================================
-- 5.2 菜谱主表
-- =====================================================
CREATE TABLE `recipe` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜谱ID',
    `recipe_code` VARCHAR(50) NOT NULL COMMENT '菜谱编码',
    `recipe_name` VARCHAR(100) NOT NULL COMMENT '菜谱名称',
    `category_id` BIGINT NOT NULL COMMENT '菜谱类别ID',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '菜谱描述',
    `image_url` VARCHAR(500) DEFAULT NULL COMMENT '菜谱图片URL',
    `serving_size` DECIMAL(10,2) DEFAULT NULL COMMENT '份量（克）',
    `target_cook_time` INT DEFAULT NULL COMMENT '目标烹饪时长（分钟）',
    `target_temp_min` INT DEFAULT NULL COMMENT '最低烹饪温度（℃）',
    `target_temp_max` INT DEFAULT NULL COMMENT '最高烹饪温度（℃）',
    `cooking_steps` TEXT COMMENT '制作步骤',
    `unit_cost` DECIMAL(10,2) DEFAULT NULL COMMENT '单份成本（元）',
    -- 营养成分
    `calories` DECIMAL(10,2) DEFAULT NULL COMMENT '热量（千卡/100g）',
    `protein` DECIMAL(10,2) DEFAULT NULL COMMENT '蛋白质（g/100g）',
    `carbohydrate` DECIMAL(10,2) DEFAULT NULL COMMENT '碳水化合物（g/100g）',
    `fat` DECIMAL(10,2) DEFAULT NULL COMMENT '脂肪（g/100g）',
    `sodium` DECIMAL(10,2) DEFAULT NULL COMMENT '钠（mg/100g）',
    `fiber` DECIMAL(10,2) DEFAULT NULL COMMENT '膳食纤维（g/100g）',
    -- 维生素
    `vitamin_a` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素A（μg）',
    `vitamin_b1` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素B1（mg）',
    `vitamin_b2` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素B2（mg）',
    `vitamin_c` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素C（mg）',
    `vitamin_d` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素D（μg）',
    `vitamin_e` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素E（mg）',
    -- 矿物质
    `calcium` DECIMAL(10,2) DEFAULT NULL COMMENT '钙（mg）',
    `iron` DECIMAL(10,2) DEFAULT NULL COMMENT '铁（mg）',
    `zinc` DECIMAL(10,2) DEFAULT NULL COMMENT '锌（mg）',
    -- AI相关
    `nutrition_score` INT DEFAULT NULL COMMENT '营养评分（0-100）',
    `ai_suggestions` TEXT COMMENT 'AI优化建议',
    `use_ai_suggestion` TINYINT(1) DEFAULT 0 COMMENT '是否使用AI建议',
    -- 状态
    `status` VARCHAR(20) DEFAULT 'active' COMMENT '状态: active=启用, inactive=停用',
    `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID',
    `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recipe_code` (`recipe_code`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_org_id` (`org_id`),
    CONSTRAINT `fk_recipe_category` FOREIGN KEY (`category_id`) REFERENCES `recipe_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜谱主表';

-- =====================================================
-- 5.3 菜谱食材配比表
-- =====================================================
CREATE TABLE `recipe_ingredient` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配比ID',
    `recipe_id` BIGINT NOT NULL COMMENT '菜谱ID',
    `material_id` BIGINT NOT NULL COMMENT '物料ID',
    `material_name` VARCHAR(100) NOT NULL COMMENT '物料名称',
    `material_spec` VARCHAR(100) DEFAULT NULL COMMENT '物料规格',
    `quantity` DECIMAL(10,3) NOT NULL COMMENT '用量',
    `unit` VARCHAR(20) NOT NULL COMMENT '单位',
    `is_main` TINYINT(1) DEFAULT 0 COMMENT '是否主料：0=否，1=是',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
    -- 食材营养成分（冗余存储）
    `calories` DECIMAL(10,2) DEFAULT NULL COMMENT '热量（千卡/100g）',
    `protein` DECIMAL(10,2) DEFAULT NULL COMMENT '蛋白质（g）',
    `carbohydrate` DECIMAL(10,2) DEFAULT NULL COMMENT '碳水化合物（g）',
    `fat` DECIMAL(10,2) DEFAULT NULL COMMENT '脂肪（g）',
    `sodium` DECIMAL(10,2) DEFAULT NULL COMMENT '钠',
    `fiber` DECIMAL(10,2) DEFAULT NULL COMMENT '膳食纤维',
    `vitamin_a` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素A（μg）',
    `vitamin_b1` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素B1（mg）',
    `vitamin_b2` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素B2（mg）',
    `vitamin_c` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素C（mg）',
    `vitamin_d` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素D（μg）',
    `vitamin_e` DECIMAL(10,2) DEFAULT NULL COMMENT '维生素E（mg）',
    `calcium` DECIMAL(10,2) DEFAULT NULL COMMENT '钙',
    `iron` DECIMAL(10,2) DEFAULT NULL COMMENT '铁',
    `zinc` DECIMAL(10,2) DEFAULT NULL COMMENT '锌',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_recipe_id` (`recipe_id`),
    KEY `idx_material_id` (`material_id`),
    CONSTRAINT `fk_ingredient_recipe` FOREIGN KEY (`recipe_id`) REFERENCES `recipe` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜谱食材配比表';

-- =====================================================
-- 5.4 菜谱计划表
-- =====================================================
CREATE TABLE `recipe_plan` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '计划ID',
    `plan_code` VARCHAR(50) NOT NULL COMMENT '计划编码',
    `plan_date` DATE NOT NULL COMMENT '计划日期',
    `start_date` DATE DEFAULT NULL COMMENT '实施开始日期',
    `end_date` DATE DEFAULT NULL COMMENT '实施结束日期',
    `meal_type` VARCHAR(20) NOT NULL COMMENT '餐次: breakfast=早餐, lunch=午餐, dinner=晚餐',
    `expected_count` INT DEFAULT NULL COMMENT '就餐人数',
    `target_group` VARCHAR(50) DEFAULT 'adult' COMMENT '目标人群: adult=普通成人, elderly=老人, child=儿童, patient=病人',
    `health_status` VARCHAR(100) DEFAULT NULL COMMENT '健康状况标签(逗号分隔): diabetes=糖尿病, hypertension=高血压, hyperlipidemia=高血脂, obesity=肥胖',
    `diet_restrictions` VARCHAR(500) DEFAULT NULL COMMENT '饮食限制描述',
    `total_servings` INT DEFAULT 0 COMMENT '总份数',
    `estimated_cost` DECIMAL(12,2) DEFAULT 0.00 COMMENT '预估总成本（元）',
    `nutrition_pass_rate` DECIMAL(5,2) DEFAULT NULL COMMENT '营养达标率（%）',
    `ai_nutrition_assessment` TEXT COMMENT 'AI营养评估结果（JSON）',
    `use_ai_recommend` TINYINT(1) DEFAULT 0 COMMENT '是否使用AI推荐',
    `status` VARCHAR(20) DEFAULT 'draft' COMMENT '状态: draft=草稿, pending=待审核, approved=已审核, rejected=已拒绝, cooking=烹饪中, completed=已完成',
    `stock_risk_status` VARCHAR(20) DEFAULT NULL COMMENT '库存风险状态: normal=正常, warning=临期预警, expired=已过期, shortage=库存不足, unknown=待确认',
    `stock_risk_message` TEXT COMMENT '库存风险说明',
    `stock_validated_at` DATETIME DEFAULT NULL COMMENT '最近一次库存校验时间',
    `stock_next_recheck_at` DATETIME DEFAULT NULL COMMENT '下一次自动复检时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `submitted_by` BIGINT DEFAULT NULL COMMENT '提交人ID',
    `submitted_at` DATETIME DEFAULT NULL COMMENT '提交时间',
    `audited_by` BIGINT DEFAULT NULL COMMENT '审核人ID',
    `audited_at` DATETIME DEFAULT NULL COMMENT '审核时间',
    `audit_remark` VARCHAR(500) DEFAULT NULL COMMENT '审核意见',
    `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID',
    `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_code` (`plan_code`),
    KEY `idx_plan_date` (`plan_date`),
    KEY `idx_meal_type` (`meal_type`),
    KEY `idx_status` (`status`),
    KEY `idx_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜谱计划表';

-- =====================================================
-- 5.5 菜谱计划明细表
-- =====================================================
CREATE TABLE `recipe_plan_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    `plan_id` BIGINT NOT NULL COMMENT '计划ID',
    `recipe_id` BIGINT NOT NULL COMMENT '菜谱ID',
    `recipe_name` VARCHAR(100) DEFAULT NULL COMMENT '菜谱名称',
    `recipe_code` VARCHAR(50) DEFAULT NULL COMMENT '菜谱编码',
    `category_name` VARCHAR(100) DEFAULT NULL COMMENT '菜谱类别',
    `meal_key` VARCHAR(64) DEFAULT NULL COMMENT '餐次唯一标识',
    `meal_type` VARCHAR(20) DEFAULT NULL COMMENT '餐次类型',
    `meal_name` VARCHAR(100) DEFAULT NULL COMMENT '餐次名称',
    `meal_expected_count` INT DEFAULT NULL COMMENT '餐次就餐人数',
    `meal_sort_order` INT DEFAULT 0 COMMENT '餐次排序',
    `planned_servings` INT DEFAULT 1 COMMENT '计划份数',
    `cooked_servings` INT DEFAULT 0 COMMENT '已烹饪份数',
    `unit_cost` DECIMAL(10,2) DEFAULT NULL COMMENT '单份成本（元）',
    `total_cost` DECIMAL(12,2) DEFAULT NULL COMMENT '小计成本（元）',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending=待烹饪, cooking=烹饪中, completed=已完成',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_plan_id` (`plan_id`),
    KEY `idx_recipe_id` (`recipe_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_plan_item_plan` FOREIGN KEY (`plan_id`) REFERENCES `recipe_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜谱计划明细表';

-- =====================================================
-- 5.6 菜谱计划调整申请表
-- =====================================================
CREATE TABLE `recipe_plan_adjustment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '调整申请ID',
    `adjust_code` VARCHAR(32) NOT NULL COMMENT '调整编码',
    `plan_id` BIGINT NOT NULL COMMENT '计划ID',
    `adjust_reason` VARCHAR(500) NOT NULL COMMENT '调整原因',
    `adjust_type` VARCHAR(20) NOT NULL COMMENT '调整类型: add=新增菜谱, remove=移除菜谱, modify=修改份数',
    `before_data` TEXT COMMENT '调整前数据（JSON）',
    `after_data` TEXT COMMENT '调整后数据（JSON）',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending=待审核, approved=已通过, rejected=已拒绝',
    `applied_by` BIGINT DEFAULT NULL COMMENT '申请人ID',
    `applied_at` DATETIME DEFAULT NULL COMMENT '申请时间',
    `audited_by` BIGINT DEFAULT NULL COMMENT '审核人ID',
    `audited_at` DATETIME DEFAULT NULL COMMENT '审核时间',
    `audit_remark` VARCHAR(500) DEFAULT NULL COMMENT '审核意见',
    `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID',
    `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_adjust_code` (`adjust_code`),
    KEY `idx_plan_id` (`plan_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_adjustment_plan` FOREIGN KEY (`plan_id`) REFERENCES `recipe_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜谱计划调整申请表';

-- =====================================================
-- 5.7 物料预警通知表
-- =====================================================
CREATE TABLE IF NOT EXISTS `recipe_material_notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `notification_type` VARCHAR(20) NOT NULL COMMENT '通知类型: expiring=临期预警, expired=已过期, low_stock=库存不足',
    `material_id` BIGINT COMMENT '物料ID',
    `material_name` VARCHAR(100) COMMENT '物料名称',
    `inventory_id` BIGINT COMMENT '库存批次ID (对应wms_inventory的id)',
    `batch_no` VARCHAR(50) COMMENT '批次号',
    `quantity` DECIMAL(18, 4) COMMENT '当前库存数量',
    `unit` VARCHAR(20) COMMENT '单位',
    `expiry_date` DATE COMMENT '到期日期',
    `days_remaining` INT COMMENT '剩余天数',
    `recommended_recipe_ids` VARCHAR(500) COMMENT '推荐菜谱ID列表（逗号分隔）',
    `recommended_recipe_names` VARCHAR(1000) COMMENT '推荐菜谱名称列表（逗号分隔）',
    `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content` TEXT COMMENT '通知内容',
    `priority` VARCHAR(10) NOT NULL DEFAULT 'medium' COMMENT '优先级: high=高, medium=中, low=低',
    `status` VARCHAR(20) NOT NULL DEFAULT 'unread' COMMENT '状态: unread=未读, read=已读, handled=已处理, dismissed=已忽略',
    `handled_by` BIGINT COMMENT '处理人ID',
    `handled_at` DATETIME COMMENT '处理时间',
    `handle_remark` VARCHAR(500) COMMENT '处理备注',
    `org_id` BIGINT COMMENT '所属组织ID',
    `tenant_id` BIGINT COMMENT '租户ID',
    `created_by` BIGINT COMMENT '创建人ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_notification_type` (`notification_type`),
    INDEX `idx_material_id` (`material_id`),
    INDEX `idx_inventory_id` (`inventory_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_priority` (`priority`),
    INDEX `idx_org_id` (`org_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料临期/过期预警通知表，存储物料预警信息和推荐菜谱';
