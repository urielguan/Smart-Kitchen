CREATE TABLE IF NOT EXISTS `sys_regulatory_report_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_name` VARCHAR(100) NOT NULL COMMENT '模板名称',
  `scope_desc` VARCHAR(200) NOT NULL COMMENT '适用范围说明',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID，为空表示全局',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_srrt_org` (`org_id`),
  KEY `idx_srrt_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监管看板报表模板配置表';

CREATE TABLE IF NOT EXISTS `sys_regulatory_external_share` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `target_name` VARCHAR(100) NOT NULL COMMENT '共享对象',
  `share_mode` VARCHAR(100) NOT NULL COMMENT '共享方式',
  `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间，为空表示长期',
  `status` VARCHAR(20) NOT NULL DEFAULT '生效中' COMMENT '状态',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID，为空表示全局',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_srse_org` (`org_id`),
  KEY `idx_srse_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监管看板外部共享配置表';

CREATE TABLE IF NOT EXISTS `sys_regulatory_api_subscription` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_code` VARCHAR(100) NOT NULL COMMENT '应用编码',
  `api_path` VARCHAR(200) NOT NULL COMMENT '接口路径',
  `rate_limit_desc` VARCHAR(100) NOT NULL COMMENT '限流说明',
  `status` VARCHAR(20) NOT NULL DEFAULT '正常' COMMENT '状态',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID，为空表示全局',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_sras_org` (`org_id`),
  KEY `idx_sras_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监管看板API订阅配置表';

INSERT INTO `sys_regulatory_report_template` (`template_name`, `scope_desc`, `sort_order`, `org_id`, `tenant_id`, `deleted`)
SELECT * FROM (
  SELECT '集团监管日报', '组织 + 食堂 + 近1日', 10, NULL, 1, 0
  UNION ALL
  SELECT '食安专项周报', '食品安全专项 + 近7日', 20, NULL, 1, 0
  UNION ALL
  SELECT '告警整改月度复盘', '告警与整改 + 近30日', 30, NULL, 1, 0
) seed
WHERE NOT EXISTS (SELECT 1 FROM `sys_regulatory_report_template` WHERE deleted = 0);

INSERT INTO `sys_regulatory_external_share` (`target_name`, `share_mode`, `expire_at`, `status`, `sort_order`, `org_id`, `tenant_id`, `deleted`)
SELECT * FROM (
  SELECT '市场监管部门', '二维码 / 外链', DATE_ADD(NOW(), INTERVAL 7 DAY), '生效中', 10, NULL, 1, 0
  UNION ALL
  SELECT '集团运营中心', '内部分享', NULL, '生效中', 20, NULL, 1, 0
) seed
WHERE NOT EXISTS (SELECT 1 FROM `sys_regulatory_external_share` WHERE deleted = 0);

INSERT INTO `sys_regulatory_api_subscription` (`app_code`, `api_path`, `rate_limit_desc`, `status`, `sort_order`, `org_id`, `tenant_id`, `deleted`)
SELECT * FROM (
  SELECT 'regulator-openapi', '/openapi/v1/dashboard/overview', '120次/分钟', '正常', 10, NULL, 1, 0
  UNION ALL
  SELECT 'group-bi-sync', '/openapi/v1/dashboard/risk-events', '60次/分钟', '正常', 20, NULL, 1, 0
) seed
WHERE NOT EXISTS (SELECT 1 FROM `sys_regulatory_api_subscription` WHERE deleted = 0);
