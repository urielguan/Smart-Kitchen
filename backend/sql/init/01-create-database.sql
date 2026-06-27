-- ============================================================
-- 智慧厨房管理平台 - 数据库初始化脚本
-- 版本: v1.0
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `smart_food_safety`
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE `smart_food_safety`;

-- ============================================================
-- 第一部分：认证授权模块（auth_）
-- ============================================================

-- ---------------------------
-- 1.1 用户表（auth_user）
-- ---------------------------
DROP TABLE IF EXISTS `auth_user`;
CREATE TABLE `auth_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  `avatar_url` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
  `gender` TINYINT DEFAULT 0 COMMENT '性别：0=未知，1=男，2=女',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active=启用，inactive=禁用，locked=锁定',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
  `login_fail_count` INT DEFAULT 0 COMMENT '登录失败次数',
  `locked_until` DATETIME DEFAULT NULL COMMENT '锁定截止时间',
  `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID（多租户隔离）',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `password_changed` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已修改过密码：0=否，1=是（首次登录强制改密）',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  UNIQUE KEY `uk_user_email` (`email`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  UNIQUE KEY `uk_user_phone` (`phone`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_user_org` (`org_id`),
  KEY `idx_user_tenant` (`tenant_id`),
  KEY `idx_user_status` (`status`),
  KEY `idx_user_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 初始化管理员账号（用户名: admin，密码: admin）
-- BCrypt 哈希由 Spring Security BCryptPasswordEncoder 生成
INSERT INTO `auth_user` (
  `username`, `password`, `real_name`, `status`, `org_id`, `tenant_id`, `password_changed`
) VALUES (
  'admin', '$2a$10$mJtYiyjj9zzRt2FDc6mMBOXNh9zINT9ySQgZrfSHVXbCGgRiHNoEG', '系统管理员', 'active', 0, 1, 1
);

-- ---------------------------
-- 1.2 JWT令牌表（auth_token）
-- ---------------------------
DROP TABLE IF EXISTS `auth_token`;
CREATE TABLE `auth_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '令牌ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `access_token` VARCHAR(500) NOT NULL COMMENT 'JWT访问令牌',
  `refresh_token` VARCHAR(500) NOT NULL COMMENT 'JWT刷新令牌',
  `access_token_expires_at` DATETIME NOT NULL COMMENT '访问令牌过期时间',
  `refresh_token_expires_at` DATETIME NOT NULL COMMENT '刷新令牌过期时间',
  `device_type` VARCHAR(20) DEFAULT NULL COMMENT '设备类型：web/mobile/tablet',
  `device_id` VARCHAR(100) DEFAULT NULL COMMENT '设备唯一标识',
  `ip_address` VARCHAR(50) DEFAULT NULL COMMENT '登录IP地址',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '用户代理信息',
  `is_revoked` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已撤销：0=否，1=是',
  `revoked_at` DATETIME DEFAULT NULL COMMENT '撤销时间',
  `revoked_reason` VARCHAR(200) DEFAULT NULL COMMENT '撤销原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_access` (`access_token`(255)),
  UNIQUE KEY `uk_token_refresh` (`refresh_token`(255)),
  KEY `idx_token_user` (`user_id`),
  KEY `idx_token_expires` (`access_token_expires_at`),
  KEY `idx_token_revoked` (`is_revoked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JWT令牌表';

-- ---------------------------
-- 1.3 角色分组表（auth_role_group）
-- ---------------------------
DROP TABLE IF EXISTS `auth_role_group`;
CREATE TABLE `auth_role_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分组ID',
  `group_name` VARCHAR(50) NOT NULL COMMENT '分组名称',
  `sort_order` INT DEFAULT 0 COMMENT '排序序号',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID（NULL=全局分组）',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_role_group_org` (`org_id`),
  KEY `idx_role_group_tenant` (`tenant_id`),
  KEY `idx_role_group_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色分组表';

-- 初始化角色分组数据
INSERT INTO `auth_role_group` (`id`, `group_name`, `sort_order`, `remark`) VALUES
(1, '系统管理组', 1, '负责系统配置与权限管理'),
(2, '采购管理组', 2, '负责采购相关业务操作'),
(3, '仓储管理组', 3, '负责仓库、库存、出入库'),
(4, '厨房管理组', 4, '负责菜谱计划与烹饪管理');

-- ---------------------------
-- 1.4 角色表（auth_role）
-- ---------------------------
DROP TABLE IF EXISTS `auth_role`;
CREATE TABLE `auth_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码（唯一标识）',
  `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
  `role_desc` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
  `group_id` BIGINT DEFAULT NULL COMMENT '所属分组ID',
  `role_type` VARCHAR(20) NOT NULL DEFAULT 'custom' COMMENT '角色类型：system=系统预设，custom=自定义',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active=启用，inactive=禁用',
  `sort_order` INT DEFAULT 0 COMMENT '排序序号',
  `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID（NULL=全局角色）',
  `data_scope` VARCHAR(30) NOT NULL DEFAULT 'all' COMMENT '数据权限范围：all/custom/dept/dept_and_child/self',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`, `tenant_id`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_role_group` (`group_id`),
  KEY `idx_role_org` (`org_id`),
  KEY `idx_role_tenant` (`tenant_id`),
  KEY `idx_role_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ---------------------------
-- 1.5 权限表（auth_permission）
-- ---------------------------
DROP TABLE IF EXISTS `auth_permission`;
CREATE TABLE `auth_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `permission_code` VARCHAR(100) NOT NULL COMMENT '权限编码（如：scm:supplier:create）',
  `permission_name` VARCHAR(50) NOT NULL COMMENT '权限名称',
  `permission_type` VARCHAR(20) NOT NULL COMMENT '权限类型：module=模块，menu=菜单，button=按钮，api=接口',
  `parent_id` BIGINT DEFAULT 0 COMMENT '父权限ID（0=顶级）',
  `module_code` VARCHAR(50) NOT NULL COMMENT '所属模块编码',
  `resource_path` VARCHAR(200) DEFAULT NULL COMMENT '资源路径（菜单路由/API路径）',
  `icon` VARCHAR(100) DEFAULT NULL COMMENT '图标',
  `sort_order` INT DEFAULT 0 COMMENT '排序序号',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  KEY `idx_permission_parent` (`parent_id`),
  KEY `idx_permission_module` (`module_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- ---------------------------
-- 1.6 用户角色关联表（auth_user_role）
-- ---------------------------
DROP TABLE IF EXISTS `auth_user_role`;
CREATE TABLE `auth_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_ur_user` (`user_id`),
  KEY `idx_ur_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ---------------------------
-- 1.7 角色权限关联表（auth_role_permission）
-- ---------------------------
DROP TABLE IF EXISTS `auth_role_permission`;
CREATE TABLE `auth_role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `permission_id` BIGINT NOT NULL COMMENT '权限ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_rp_role` (`role_id`),
  KEY `idx_rp_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ---------------------------
-- 1.8 角色数据权限组织关联表（auth_role_data_scope_org）
-- ---------------------------
DROP TABLE IF EXISTS `auth_role_data_scope_org`;
CREATE TABLE `auth_role_data_scope_org` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `org_id` BIGINT NOT NULL COMMENT '组织ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_data_scope_org` (`role_id`, `org_id`),
  KEY `idx_rdso_role` (`role_id`),
  KEY `idx_rdso_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色数据权限组织关联表';

-- ---------------------------
-- 1.9 权限初始化数据（菜单+按钮）
-- ---------------------------
INSERT INTO `auth_permission` (`id`, `permission_code`, `permission_name`, `permission_type`, `parent_id`, `module_code`, `resource_path`, `icon`, `sort_order`, `status`) VALUES
(1, 'module:dashboard', '数据概览', 'module', 0, 'dashboard', '', '📊', 1, 'active'),
(2, 'dashboard', '数据看板', 'menu', 1, 'dashboard', '/dashboard', '📊', 1, 'active'),
(10, 'module:scm', '采购管理', 'module', 0, 'scm', '', '🛒', 10, 'active'),
(11, 'supplier', '供应商管理', 'menu', 10, 'scm', '/supplier', '🏢', 11, 'active'),
(12, 'supplier:create', '新增供应商', 'button', 11, 'scm', '', '', 1, 'active'),
(13, 'supplier:edit', '编辑供应商', 'button', 11, 'scm', '', '', 2, 'active'),
(14, 'supplier:approve', '审核供应商', 'button', 11, 'scm', '', '', 3, 'active'),
(15, 'supplier:delete', '删除供应商', 'button', 11, 'scm', '', '', 4, 'active'),
(16, 'supplier:cancel', '注销供应商', 'button', 11, 'scm', '', '', 5, 'active'),
(17, 'supplier:status', '启用/禁用供应商', 'button', 11, 'scm', '', '', 6, 'active'),
(20, 'purchasePlan', '采购计划', 'menu', 10, 'scm', '/purchase-plan', '📑', 20, 'active'),
(21, 'purchasePlan:create', '新增采购计划', 'button', 20, 'scm', '', '', 1, 'active'),
(22, 'purchasePlan:edit', '编辑采购计划', 'button', 20, 'scm', '', '', 2, 'active'),
(23, 'purchasePlan:delete', '删除采购计划', 'button', 20, 'scm', '', '', 3, 'active'),
(24, 'purchasePlan:approve', '审核采购计划', 'button', 20, 'scm', '', '', 4, 'active'),
(25, 'purchasePlan:generateOrder', '生成采购订单', 'button', 20, 'scm', '', '', 5, 'active'),
(30, 'purchase', '采购订单', 'menu', 10, 'scm', '/purchase', '📋', 30, 'active'),
(31, 'purchase:create', '新增采购订单', 'button', 30, 'scm', '', '', 1, 'active'),
(32, 'purchase:edit', '编辑采购订单', 'button', 30, 'scm', '', '', 2, 'active'),
(33, 'purchase:delete', '删除采购订单', 'button', 30, 'scm', '', '', 3, 'active'),
(34, 'purchase:approve', '审核采购订单', 'button', 30, 'scm', '', '', 4, 'active'),
(35, 'purchase:void', '作废采购订单', 'button', 30, 'scm', '', '', 5, 'active'),
(36, 'purchase:void-audit', '作废审核', 'button', 30, 'scm', '', '', 6, 'active'),
(37, 'purchase:logistics', '维护物流', 'button', 30, 'scm', '', '', 7, 'active'),
(38, 'purchase:inspection', '检测报告', 'button', 30, 'scm', '', '', 8, 'active'),
(39, 'purchase:traceability', '溯源信息', 'button', 30, 'scm', '', '', 9, 'active'),
(40, 'module:wms', '仓储管理', 'module', 0, 'wms', '', '🏭', 40, 'active'),
(41, 'warehouse', '仓库信息管理', 'menu', 40, 'wms', '/warehouse', '🏭', 41, 'active'),
(42, 'warehouse:create', '新增仓库', 'button', 41, 'wms', '', '', 1, 'active'),
(43, 'warehouse:edit', '编辑仓库', 'button', 41, 'wms', '', '', 2, 'active'),
(44, 'warehouse:delete', '删除仓库', 'button', 41, 'wms', '', '', 3, 'active'),
(45, 'warehouse:location:create', '新增仓位', 'button', 41, 'wms', '', '', 4, 'active'),
(46, 'warehouse:location:edit', '编辑仓位', 'button', 41, 'wms', '', '', 5, 'active'),
(47, 'warehouse:location:delete', '删除仓位', 'button', 41, 'wms', '', '', 6, 'active'),
(50, 'material', '物料信息管理', 'menu', 40, 'wms', '/material', '📦', 50, 'active'),
(51, 'material:create', '新增物料', 'button', 50, 'wms', '', '', 1, 'active'),
(52, 'material:edit', '编辑物料', 'button', 50, 'wms', '', '', 2, 'active'),
(53, 'material:delete', '删除物料', 'button', 50, 'wms', '', '', 3, 'active'),
(54, 'material:status', '启用/停用物料', 'button', 50, 'wms', '', '', 4, 'active'),
(55, 'material:import', '导入物料', 'button', 50, 'wms', '', '', 5, 'active'),
(56, 'material:export', '导出物料', 'button', 50, 'wms', '', '', 6, 'active'),
(60, 'inventory', '库存汇总', 'menu', 40, 'wms', '/inventory', '📊', 60, 'active'),
(61, 'inventory:distribution', '库存分布', 'button', 60, 'wms', '', '', 1, 'active'),
(62, 'inventory:ioRecord', '出入库记录', 'button', 60, 'wms', '', '', 2, 'active'),
(70, 'inbound', '入库管理', 'menu', 40, 'wms', '/inbound', '📥', 70, 'active'),
(71, 'inbound:create', '新增入库单', 'button', 70, 'wms', '', '', 1, 'active'),
(72, 'inbound:edit', '编辑入库单', 'button', 70, 'wms', '', '', 2, 'active'),
(73, 'inbound:delete', '删除入库单', 'button', 70, 'wms', '', '', 3, 'active'),
(74, 'inbound:submit', '提交入库单', 'button', 70, 'wms', '', '', 4, 'active'),
(75, 'inbound:cancel', '取消入库单', 'button', 70, 'wms', '', '', 5, 'active'),
(76, 'inbound:audit', '审核/驳回入库单', 'button', 70, 'wms', '', '', 6, 'active'),
(80, 'outbound', '出库管理', 'menu', 40, 'wms', '/outbound', '📤', 80, 'active'),
(81, 'outbound:create', '新增出库单', 'button', 80, 'wms', '', '', 1, 'active'),
(82, 'outbound:edit', '编辑出库单', 'button', 80, 'wms', '', '', 2, 'active'),
(83, 'outbound:delete', '删除出库单', 'button', 80, 'wms', '', '', 3, 'active'),
(84, 'outbound:submit', '提交出库单', 'button', 80, 'wms', '', '', 4, 'active'),
(85, 'outbound:withdraw', '撤回出库单', 'button', 80, 'wms', '', '', 5, 'active'),
(86, 'outbound:audit', '审核/驳回出库单', 'button', 80, 'wms', '', '', 6, 'active'),
(87, 'outbound:reverse', '反审核出库单', 'button', 80, 'wms', '', '', 8, 'active'),
(88, 'outbound:execute', '出库执行', 'button', 80, 'wms', '', '', 9, 'active'),
(90, 'stocktake', '盘点管理', 'menu', 40, 'wms', '/stocktake', '📝', 90, 'active'),
(91, 'stocktake:create', '新增盘点', 'button', 90, 'wms', '', '', 1, 'active'),
(92, 'stocktake:edit', '编辑盘点', 'button', 90, 'wms', '', '', 2, 'active'),
(93, 'stocktake:submit', '提交审核/重新提交', 'button', 90, 'wms', '', '', 3, 'active'),
(94, 'stocktake:approve', '审核盘点', 'button', 90, 'wms', '', '', 4, 'active'),
(95, 'stocktake:void', '作废盘点', 'button', 90, 'wms', '', '', 5, 'active'),
(96, 'stocktake:export', '导出盘点', 'button', 90, 'wms', '', '', 6, 'active'),
(100, 'module:cook-nutrition', '菜谱营养', 'module', 0, 'cook', '', '🍽️', 100, 'active'),
(101, 'recipe', '菜谱库管理', 'menu', 100, 'cook', '/recipe', '🍽️', 101, 'active'),
(102, 'recipe:create', '新增菜谱', 'button', 101, 'cook', '', '', 1, 'active'),
(103, 'recipe:edit', '编辑菜谱', 'button', 101, 'cook', '', '', 2, 'active'),
(104, 'recipe:delete', '删除菜谱', 'button', 101, 'cook', '', '', 3, 'active'),
(110, 'plan', '菜谱计划', 'menu', 100, 'cook', '/plan', '📅', 110, 'active'),
(111, 'plan:create', '新增菜谱计划', 'button', 110, 'cook', '', '', 1, 'active'),
(112, 'plan:edit', '编辑菜谱计划', 'button', 110, 'cook', '', '', 2, 'active'),
(113, 'plan:delete', '删除菜谱计划', 'button', 110, 'cook', '', '', 3, 'active'),
(114, 'plan:submit', '提交菜谱计划', 'button', 110, 'cook', '', '', 4, 'active'),
(115, 'plan:approve', '审核菜谱计划', 'button', 110, 'cook', '', '', 5, 'active'),
(116, 'plan:adjust', '申请计划调整', 'button', 110, 'cook', '', '', 6, 'active'),
(120, 'plan-adjustment', '计划调整申请', 'menu', 100, 'cook', '/plan-adjustment', '📝', 120, 'active'),
(121, 'plan-adjustment:approve', '审核调整申请', 'button', 120, 'cook', '', '', 1, 'active'),
(130, 'module:cook-backend', '后厨管理', 'module', 0, 'cook', '', '👨‍🍳', 130, 'active'),
(131, 'cook', '烹饪记录', 'menu', 130, 'cook', '/cook', '👨‍🍳', 131, 'active'),
(140, 'sample', '留样管理', 'menu', 130, 'cook', '/sample', '🧪', 140, 'active'),
(141, 'sample:create', '新增留样', 'button', 140, 'cook', '', '', 1, 'active'),
(142, 'sample:edit', '编辑留样', 'button', 140, 'cook', '', '', 2, 'active'),
(143, 'sample:dispose', '销样', 'button', 140, 'cook', '', '', 3, 'active'),
(144, 'sample:void', '作废', 'button', 140, 'cook', '', '', 4, 'active'),
(145, 'sample:archive', '归档', 'button', 140, 'cook', '', '', 5, 'active'),
(146, 'sample:aiEvaluate', 'AI评估', 'button', 140, 'cook', '', '', 6, 'active'),
(147, 'sample:export', '导出留样', 'button', 140, 'cook', '', '', 7, 'active'),
(148, 'sample:historySupplement', '历史留样补录', 'button', 140, 'cook', '', '', 8, 'active'),
(149, 'sample:manualDisposalSupplement', '销样手工补录', 'button', 140, 'cook', '', '', 9, 'active'),
(150, 'morning-check', '智能人脸晨检', 'menu', 130, 'cook', '/morning-check', '🤖', 150, 'active'),
(151, 'morning-check:faceEnroll', '人脸录入', 'button', 150, 'cook', '', '', 1, 'active'),
(152, 'morning-check:start', '开始晨检', 'button', 150, 'cook', '', '', 2, 'active'),
(153, 'morning-check:archive', '归档晨检记录', 'button', 150, 'cook', '', '', 3, 'active'),
(154, 'morning-check:cert:create', '录入健康证', 'button', 150, 'cook', '', '', 4, 'active'),
(155, 'morning-check:cert:edit', '编辑健康证', 'button', 150, 'cook', '', '', 5, 'active'),
(156, 'morning-check:cert:delete', '删除健康证', 'button', 150, 'cook', '', '', 6, 'active'),
(157, 'morning-check:cert:export', '导出健康证', 'button', 150, 'cook', '', '', 7, 'active'),
(160, 'video-monitor', '视频监控管理', 'menu', 130, 'cook', '/video-monitor', '📷', 160, 'active'),
(161, 'video-monitor:config', '监控配置', 'button', 160, 'cook', '', '', 1, 'active'),
(170, 'video-playback', '视频回放', 'menu', 130, 'cook', '/video-playback', '📼', 170, 'active'),
(171, 'video-playback:download', '下载回放', 'button', 170, 'cook', '', '', 1, 'active'),
(172, 'video-playback:delete', '删除回放', 'button', 170, 'cook', '', '', 2, 'active'),
(173, 'video-playback:clipExtract', '提取片段', 'button', 170, 'cook', '', '', 3, 'active'),
(174, 'video-playback:clipDelete', '删除片段', 'button', 170, 'cook', '', '', 4, 'active'),
(175, 'video-playback:screenshotCapture', '截图', 'button', 170, 'cook', '', '', 5, 'active'),
(176, 'video-playback:screenshotDelete', '删除截图', 'button', 170, 'cook', '', '', 6, 'active'),
(180, 'violation', 'AI违规识别', 'menu', 130, 'cook', '/violation', '🚨', 180, 'active'),
(181, 'violation:handle', '处理违规', 'button', 180, 'cook', '', '', 1, 'active'),
(182, 'violation:review', '复核违规', 'button', 180, 'cook', '', '', 2, 'active'),
(190, 'behavior-analysis', 'AI人员行为分析', 'menu', 130, 'cook', '/behavior-analysis', '📊', 190, 'active'),
(200, 'device', '设备管理', 'menu', 130, 'cook', '/device', '🔧', 200, 'active'),
(201, 'device:create', '新增设备', 'button', 200, 'cook', '', '', 1, 'active'),
(202, 'device:edit', '编辑设备', 'button', 200, 'cook', '', '', 2, 'active'),
(203, 'device:delete', '删除设备', 'button', 200, 'cook', '', '', 3, 'active'),
(210, 'alert', '告警管理', 'menu', 130, 'cook', '/alert', '🔔', 210, 'active'),
(211, 'alert:list', '告警列表', 'button', 210, 'cook', '', '', 1, 'active'),
(212, 'alert:dispatch', '自动/人工派单', 'button', 211, 'cook', '', '', 1, 'active'),
(213, 'alert:close', '关闭告警', 'button', 211, 'cook', '', '', 2, 'active'),
(214, 'alert:export', '导出告警', 'button', 211, 'cook', '', '', 3, 'active'),
(220, 'alert-rule', '策略配置', 'button', 210, 'cook', '', '', 2, 'active'),
(221, 'alert-rule:create', '新增告警规则', 'button', 220, 'cook', '', '', 1, 'active'),
(222, 'alert-rule:edit', '编辑告警规则', 'button', 220, 'cook', '', '', 2, 'active'),
(223, 'alert-rule:delete', '删除告警规则', 'button', 220, 'cook', '', '', 3, 'active'),
(224, 'alert-rule:status', '启用/停用告警规则', 'button', 220, 'cook', '', '', 4, 'active'),
(230, 'alert:work-order', '告警处理', 'button', 210, 'cook', '', '', 3, 'active'),
(231, 'alert:process', '处理告警', 'button', 230, 'cook', '', '', 1, 'active'),
(232, 'alert:review', '复核告警', 'button', 230, 'cook', '', '', 2, 'active'),
(240, 'module:sys', '系统管理', 'module', 0, 'sys', '', '⚙️', 240, 'active'),
(241, 'org', '组织管理', 'menu', 240, 'sys', '/org', '🏗️', 241, 'active'),
(242, 'org:create', '新增组织', 'button', 241, 'sys', '', '', 1, 'active'),
(243, 'org:edit', '编辑组织', 'button', 241, 'sys', '', '', 2, 'active'),
(244, 'org:delete', '删除组织', 'button', 241, 'sys', '', '', 3, 'active'),
(245, 'org:status', '启用/停用组织', 'button', 241, 'sys', '', '', 4, 'active'),
(246, 'org:import', '导入组织', 'button', 241, 'sys', '', '', 5, 'active'),
(247, 'org:export', '导出组织', 'button', 241, 'sys', '', '', 6, 'active'),
(250, 'employee', '员工管理', 'menu', 240, 'sys', '/employee', '👤', 250, 'active'),
(251, 'employee:create', '新增员工', 'button', 250, 'sys', '', '', 1, 'active'),
(252, 'employee:edit', '编辑员工', 'button', 250, 'sys', '', '', 2, 'active'),
(253, 'employee:delete', '删除员工', 'button', 250, 'sys', '', '', 3, 'active'),
(254, 'employee:status', '启用/禁用员工', 'button', 250, 'sys', '', '', 4, 'active'),
(255, 'employee:import', '导入员工', 'button', 250, 'sys', '', '', 5, 'active'),
(256, 'employee:export', '导出员工', 'button', 250, 'sys', '', '', 6, 'active'),
(260, 'dict-category', '字典分类维护', 'menu', 240, 'sys', '/dict-category', '🗂️', 260, 'active'),
(261, 'dict-category:create', '新增字典项', 'button', 260, 'sys', '', '', 1, 'active'),
(262, 'dict-category:edit', '编辑字典项', 'button', 260, 'sys', '', '', 2, 'active'),
(263, 'dict-category:delete', '删除字典项', 'button', 260, 'sys', '', '', 3, 'active'),
(264, 'dict-category:status', '启用/禁用字典项', 'button', 260, 'sys', '', '', 4, 'active'),
(270, 'role', '角色权限管理', 'menu', 240, 'sys', '/role', '🔐', 270, 'active'),
(271, 'role:create', '新增角色', 'button', 270, 'sys', '', '', 1, 'active'),
(272, 'role:edit', '编辑角色', 'button', 270, 'sys', '', '', 2, 'active'),
(273, 'role:delete', '删除角色', 'button', 270, 'sys', '', '', 3, 'active'),
(274, 'role:group:create', '新增角色分组', 'button', 270, 'sys', '', '', 4, 'active'),
(275, 'role:group:edit', '编辑角色分组', 'button', 270, 'sys', '', '', 5, 'active'),
(276, 'role:group:delete', '删除角色分组', 'button', 270, 'sys', '', '', 6, 'active'),
(277, 'role:member:add', '添加角色成员', 'button', 270, 'sys', '', '', 7, 'active'),
(278, 'role:member:remove', '移除角色成员', 'button', 270, 'sys', '', '', 8, 'active'),
(280, 'evaluation', '评价管理', 'menu', 240, 'sys', '/evaluation', '⭐', 280, 'active'),
(281, 'evaluation:reply', '回复评价', 'button', 280, 'sys', '', '', 1, 'active'),
(282, 'evaluation:export', '导出评价', 'button', 280, 'sys', '', '', 2, 'active'),
(283, 'complaint:export', '导出投诉', 'button', 280, 'sys', '', '', 3, 'active'),
(284, 'evaluation:dispatch', '自动/人工派单', 'button', 280, 'sys', '', '', 4, 'active'),
(285, 'evaluation:process', '处理工单', 'button', 280, 'sys', '', '', 5, 'active'),
(290, 'notification', '消息中心', 'menu', 240, 'sys', '/notification', '🔔', 290, 'active'),
(291, 'notification:markRead', '标记已读', 'button', 290, 'sys', '', '', 1, 'active'),
(292, 'notification:delete', '删除消息', 'button', 290, 'sys', '', '', 2, 'active'),
(300, 'ai-config', 'AI接口管理', 'menu', 240, 'sys', '/ai-config', '🤖', 300, 'active'),
(310, 'integration-management', '第三方接入管理', 'menu', 240, 'sys', '/integration-management', '🔌', 310, 'active'),
(311, 'integration-management:create', '新增第三方接入配置', 'button', 310, 'sys', '', '', 1, 'active'),
(312, 'integration-management:edit', '编辑第三方接入配置', 'button', 310, 'sys', '', '', 2, 'active'),
(313, 'integration-management:delete', '删除第三方接入配置', 'button', 310, 'sys', '', '', 3, 'active'),
(314, 'integration-management:status', '启用/停用第三方接入配置', 'button', 310, 'sys', '', '', 4, 'active'),
(315, 'integration-management:test', '测试第三方连接', 'button', 310, 'sys', '', '', 5, 'active'),
(316, 'integration-management:sync', '触发第三方同步', 'button', 310, 'sys', '', '', 6, 'active'),
(317, 'integration-management:retry', '重试第三方同步', 'button', 310, 'sys', '', '', 7, 'active'),
(318, 'integration-management:view-log', '查看第三方同步日志', 'button', 310, 'sys', '', '', 8, 'active'),
(319, 'integration-management:view-callback', '查看第三方回调日志', 'button', 310, 'sys', '', '', 9, 'active');

-- ---------------------------
-- 1.10 系统管理员角色与默认授权
-- ---------------------------
INSERT INTO `auth_role` (
  `role_code`, `role_name`, `role_desc`, `group_id`, `role_type`, `status`, `sort_order`, `data_scope`, `tenant_id`, `created_by`
) VALUES (
  'SUPER_ADMIN', '超级管理员', '系统内置管理员，拥有全部功能权限和数据权限', 1, 'system', 'active', 1, 'all', 1, 1
);

-- -- admin 用户绑定 SUPER_ADMIN 角色
-- INSERT INTO `auth_user_role` (`user_id`, `role_id`, `created_by`)
-- SELECT u.id, r.id, u.id
-- FROM `auth_user` u
-- JOIN `auth_role` r ON r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
-- WHERE u.username = 'admin' AND u.deleted = 0
--   AND NOT EXISTS (
--     SELECT 1 FROM `auth_user_role` ur WHERE ur.user_id = u.id AND ur.role_id = r.id
--   );

-- SUPER_ADMIN 角色授予全部有效权限（排除 module 分组节点）
INSERT INTO `auth_role_permission` (`role_id`, `permission_id`, `created_by`)
SELECT r.id, p.id, 1
FROM `auth_role` r
JOIN `auth_permission` p ON p.status = 'active' AND p.permission_type <> 'module'
LEFT JOIN `auth_role_permission` rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0 AND rp.id IS NULL;


-- ============================================================
-- 第二部分：组织管理模块（sys_）
-- ============================================================

-- ---------------------------
-- 2.1 组织表（sys_organization）
-- ---------------------------
DROP TABLE IF EXISTS `sys_organization`;
CREATE TABLE `sys_organization` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '组织ID',
  `org_code` VARCHAR(50) NOT NULL COMMENT '组织编码',
  `org_name` VARCHAR(100) NOT NULL COMMENT '组织名称',
  `org_type` VARCHAR(20) NOT NULL COMMENT '组织类型：group=集团，company=分公司，canteen=食堂，dept=部门',
  `parent_id` BIGINT DEFAULT 0 COMMENT '父组织ID（0=顶级）',
  `level` INT NOT NULL DEFAULT 1 COMMENT '组织层级（1=顶级）',
  `path` VARCHAR(1000) DEFAULT NULL COMMENT '组织路径（如：/智慧食安集团总部/华东区分公司/）',
  `leader_name` VARCHAR(50) DEFAULT NULL COMMENT '负责人姓名',
  `contact_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `address` VARCHAR(200) DEFAULT NULL COMMENT '地址',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active=启用，inactive=停用',
  `sort_order` INT DEFAULT 0 COMMENT '排序序号',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_code` (`org_code`, `tenant_id`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_org_parent` (`parent_id`),
  KEY `idx_org_tenant` (`tenant_id`),
  KEY `idx_org_status` (`status`),
  KEY `idx_org_path` (`path`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织表';
