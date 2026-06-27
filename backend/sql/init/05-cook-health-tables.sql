-- ============================================================
-- 智慧厨房管理平台 - 数据库初始化脚本（第五部分）
-- 烹饪与留样管理模块（cook_/sample_）
-- 健康晨检模块（health_）
-- ============================================================

USE `smart_food_safety`;

-- ---------------------------
-- 6.1 烹饪任务表（cook_task）
-- ---------------------------
DROP TABLE IF EXISTS `cook_task`;
CREATE TABLE `cook_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `task_no` VARCHAR(50) NOT NULL COMMENT '任务编号',
  `plan_id` BIGINT NOT NULL COMMENT '关联菜谱计划ID',
  `menu_id` BIGINT NOT NULL COMMENT '菜谱ID',
  `menu_name` VARCHAR(100) NOT NULL COMMENT '菜谱名称（冗余）',
  `planned_qty` INT NOT NULL COMMENT '计划份数',
  `actual_qty` INT DEFAULT NULL COMMENT '实际完成份数',
  `assigned_chef_id` BIGINT DEFAULT NULL COMMENT '指派厨师ID',
  `assigned_chef_name` VARCHAR(50) DEFAULT NULL COMMENT '厨师姓名（冗余）',
  `device_id` BIGINT DEFAULT NULL COMMENT '烹饪设备ID（关联device_info表）',
  `device_name` VARCHAR(100) DEFAULT NULL COMMENT '烹饪设备名称（冗余）',
  `device_location` VARCHAR(200) DEFAULT NULL COMMENT '设备位置描述（冗余）',
  `material_prep_status` VARCHAR(32) DEFAULT NULL COMMENT '备料状态：pending_prep=待备料, prepared=已备料',
  `start_time` DATETIME DEFAULT NULL COMMENT '开始烹饪时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '完成时间',
  `cooking_duration` INT DEFAULT NULL COMMENT '实际烹饪时长（分钟）',
  `temperature_records` JSON DEFAULT NULL COMMENT '温度采集记录（JSON数组）',
  `ai_violation_count` INT DEFAULT 0 COMMENT 'AI识别违规次数',
  `violation_details` JSON DEFAULT NULL COMMENT '违规详情（JSON数组）',
  `quality_score` DECIMAL(5,2) DEFAULT NULL COMMENT '质量评分（0-100）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending=待开始，in_progress=进行中，completed=已完成，cancelled=已取消',
  `review_status` VARCHAR(32) DEFAULT NULL COMMENT '复核状态：null/pending_review/approved/rework_required',
  `review_remark` VARCHAR(500) DEFAULT NULL COMMENT '复核备注',
  `reviewer_id` BIGINT DEFAULT NULL COMMENT '复核人ID',
  `reviewer_name` VARCHAR(64) DEFAULT NULL COMMENT '复核人姓名',
  `completer_id` BIGINT DEFAULT NULL COMMENT '完成人ID',
  `completer_name` VARCHAR(64) DEFAULT NULL COMMENT '完成人姓名',
  `initiator_id` BIGINT DEFAULT NULL COMMENT '启动人ID',
  `initiator_name` VARCHAR(64) DEFAULT NULL COMMENT '启动人姓名',
  `handoff_status` VARCHAR(32) DEFAULT NULL COMMENT '交接状态',
  `handoff_remark` VARCHAR(500) DEFAULT NULL COMMENT '交接备注',
  `allow_start_time` TIME DEFAULT NULL COMMENT '允许开始烹饪时间',
  `allow_end_time` TIME DEFAULT NULL COMMENT '允许结束烹饪时间',
  `food_safety_pass` TINYINT DEFAULT NULL COMMENT '食安判定：0=不达标，1=达标，NULL=未判定',
  `temp_abnormal_confirmed` TINYINT DEFAULT 0 COMMENT '温度异常已确认：0=未确认，1=已确认',
  `temp_abnormal_confirmed_by` BIGINT DEFAULT NULL COMMENT '确认人ID',
  `temp_abnormal_confirmed_at` DATETIME DEFAULT NULL COMMENT '确认时间',
  `task_date` DATE DEFAULT NULL COMMENT '任务日期（周期计划按天拆分）',
  `remark` VARCHAR(300) DEFAULT NULL COMMENT '备注',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_ct_plan` (`plan_id`),
  KEY `idx_ct_menu` (`menu_id`),
  KEY `idx_ct_chef` (`assigned_chef_id`),
  KEY `idx_ct_status` (`status`),
  KEY `idx_ct_start_time` (`start_time`),
  KEY `idx_ct_org` (`org_id`),
  KEY `idx_cook_task_task_date` (`task_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='烹饪任务表';

-- ---------------------------
-- 6.2 烹饪温度记录表（cook_temperature_record）
-- ---------------------------
DROP TABLE IF EXISTS `cook_temperature_record`;
CREATE TABLE `cook_temperature_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `task_id` BIGINT NOT NULL COMMENT '关联烹饪任务ID',
  `record_time` DATETIME NOT NULL COMMENT '记录时间',
  `temperature` INT NOT NULL COMMENT '温度值（摄氏度）',
  `abnormal` TINYINT DEFAULT 0 COMMENT '是否异常',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ctr_task` (`task_id`),
  KEY `idx_ctr_time` (`record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='烹饪温度记录表';

-- ---------------------------
-- 6.3 烹饪记录表（cook_record）
-- ---------------------------
DROP TABLE IF EXISTS `cook_record`;
CREATE TABLE `cook_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `task_id` BIGINT NOT NULL COMMENT '关联烹饪任务ID',
  `record_type` VARCHAR(20) NOT NULL COMMENT '记录类型',
  `content` TEXT COMMENT '记录内容',
  `record_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作人姓名',
  `org_id` BIGINT DEFAULT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_cr_task` (`task_id`),
  KEY `idx_cr_type` (`record_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='烹饪记录表';

-- ---------------------------
-- 6.4 留样记录表（sample_record）
-- ---------------------------
DROP TABLE IF EXISTS `sample_record`;
CREATE TABLE `sample_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '留样ID',
  `sample_no` VARCHAR(50) NOT NULL COMMENT '留样编号',
  `task_id` BIGINT DEFAULT NULL COMMENT '关联烹饪任务ID',
  `menu_id` BIGINT NOT NULL COMMENT '菜谱ID',
  `menu_name` VARCHAR(100) NOT NULL COMMENT '菜谱名称（冗余）',
  `sample_date` DATE NOT NULL COMMENT '留样日期',
  `meal_type` VARCHAR(20) NOT NULL COMMENT '餐次：breakfast/lunch/dinner/supper',
  `sample_weight` DECIMAL(8,2) DEFAULT NULL COMMENT '留样重量（克）',
  `sample_images` JSON DEFAULT NULL COMMENT '留样照片（JSON数组，存储URL）',
  `ai_quality_score` DECIMAL(5,2) DEFAULT NULL COMMENT 'AI质量评分（0-100）',
  `ai_analysis_result` JSON DEFAULT NULL COMMENT 'AI分析结果（JSON格式）',
  `storage_location` VARCHAR(100) DEFAULT NULL COMMENT '存储位置',
  `storage_temp` DECIMAL(5,1) DEFAULT NULL COMMENT '存储温度（℃）',
  `sampled_by` BIGINT DEFAULT NULL COMMENT '留样人ID',
  `sampled_at` DATETIME DEFAULT NULL COMMENT '留样时间',
  `disposal_due_at` DATETIME DEFAULT NULL COMMENT '应销样时间（留样后48小时）',
  `disposal_by` BIGINT DEFAULT NULL COMMENT '销样人ID',
  `disposal_at` DATETIME DEFAULT NULL COMMENT '实际销样时间',
  `disposal_images` JSON DEFAULT NULL COMMENT '销样照片（JSON数组）',
  `disposal_remark` VARCHAR(300) DEFAULT NULL COMMENT '销样备注',
  `void_reason` VARCHAR(500) DEFAULT NULL COMMENT '作废原因',
  `archived_at` DATETIME DEFAULT NULL COMMENT '归档时间',
  `evaluated_at` DATETIME DEFAULT NULL COMMENT 'AI评估时间',
  `lock_status` VARCHAR(20) DEFAULT 'none' COMMENT '锁定状态：none/investigation/accident',
  `trace_batch_id` VARCHAR(100) DEFAULT NULL COMMENT '追溯批次ID',
  `food_safety_ledger_no` VARCHAR(50) DEFAULT NULL COMMENT '食品台账编号',
  `record_origin_type` VARCHAR(30) DEFAULT NULL COMMENT '留样来源类型：auto/manual_daily/manual_history/offline_delayed/system_backfill',
  `supplement_reason` VARCHAR(500) DEFAULT NULL COMMENT '补录原因',
  `supplement_remark` VARCHAR(500) DEFAULT NULL COMMENT '补录备注',
  `rollback_isolated` TINYINT NOT NULL DEFAULT 0 COMMENT '是否因烹饪任务回滚隔离：0否 1是',
  `rollback_isolated_at` DATETIME DEFAULT NULL COMMENT '烹饪任务回滚隔离时间',
  `rollback_isolation_reason` VARCHAR(500) DEFAULT NULL COMMENT '烹饪任务回滚隔离原因',
  `status` VARCHAR(20) NOT NULL DEFAULT 'sampled' COMMENT '状态：pending_sample=待留样，sampled=已留样，evaluated=已评估，pending_disposal=待销样，disposed=已销样，overdue=超期未销，voided=已作废，archived=已归档',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sample_no` (`sample_no`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_sr_task` (`task_id`),
  KEY `idx_sr_menu` (`menu_id`),
  KEY `idx_sr_date` (`sample_date`),
  KEY `idx_sr_status` (`status`),
  KEY `idx_sr_disposal_due` (`disposal_due_at`),
  KEY `idx_sr_lock` (`lock_status`),
  KEY `idx_sr_origin_type` (`record_origin_type`),
  KEY `idx_sr_rollback_isolated` (`rollback_isolated`, `status`),
  KEY `idx_sr_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='留样记录表';

-- ---------------------------
-- 6.5 留样操作日志表（sample_operation_log）
-- ---------------------------
DROP TABLE IF EXISTS `sample_operation_log`;
CREATE TABLE `sample_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `record_id` BIGINT NOT NULL COMMENT '留样记录ID',
  `action` VARCHAR(50) NOT NULL COMMENT '操作类型：auto_create/manual_create/history_supplement_create/offline_delayed_supplement/register/update/dispose/manual_disposal_supplement/void/archive/ai_evaluate',
  `action_name` VARCHAR(50) NOT NULL COMMENT '操作名称',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作人姓名',
  `content` VARCHAR(1000) DEFAULT NULL COMMENT '操作内容/详情',
  `terminal` VARCHAR(20) DEFAULT 'web' COMMENT '操作终端：web/mobile/system',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sol_record` (`record_id`),
  KEY `idx_sol_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='留样操作日志表';

-- ---------------------------
-- 6.6 销样手工补录元数据表（sample_disposal_supplement）
-- ---------------------------
DROP TABLE IF EXISTS `sample_disposal_supplement`;
CREATE TABLE `sample_disposal_supplement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sample_record_id` BIGINT NOT NULL COMMENT '留样记录ID',
  `sample_no` VARCHAR(50) NOT NULL COMMENT '留样编号',
  `task_id` BIGINT DEFAULT NULL COMMENT '关联烹饪任务ID',
  `disposal_source_type` VARCHAR(30) NOT NULL DEFAULT 'manual_exception_supplement' COMMENT '销样来源类型：manual_exception_supplement',
  `supplement_scene` VARCHAR(50) NOT NULL COMMENT '补录场景：system_missing/interface_sync_exception/device_offline/history_migration_fix/ops_closure_repair',
  `supplement_remark` VARCHAR(200) NOT NULL COMMENT '补录备注',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sds_sample_record` (`sample_record_id`, `deleted`),
  KEY `idx_sds_scene` (`supplement_scene`),
  KEY `idx_sds_org` (`org_id`),
  KEY `idx_sds_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销样手工补录元数据表';

-- ---------------------------
-- 6.7 留样操作锁表（sample_record_operation_lock）
-- ---------------------------
DROP TABLE IF EXISTS `sample_record_operation_lock`;
CREATE TABLE `sample_record_operation_lock` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sample_record_id` BIGINT NOT NULL COMMENT '留样记录ID',
  `sample_no` VARCHAR(50) DEFAULT NULL COMMENT '留样编号',
  `lock_token` VARCHAR(64) DEFAULT NULL COMMENT '锁令牌',
  `operation_type` VARCHAR(50) DEFAULT NULL COMMENT '操作类型：register/edit/dispose/manual_disposal_supplement/void/archive/ai_evaluate',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作人姓名',
  `source_terminal` VARCHAR(20) DEFAULT 'web' COMMENT '操作终端：web/mobile/system',
  `active` TINYINT NOT NULL DEFAULT 0 COMMENT '是否激活：0否 1是',
  `acquired_at` DATETIME DEFAULT NULL COMMENT '抢占时间',
  `last_heartbeat_at` DATETIME DEFAULT NULL COMMENT '最近续租时间',
  `expires_at` DATETIME DEFAULT NULL COMMENT '锁过期时间',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_srol_sample_record` (`sample_record_id`),
  KEY `idx_srol_active` (`active`, `expires_at`),
  KEY `idx_srol_operator` (`operator_id`),
  KEY `idx_srol_org` (`org_id`),
  KEY `idx_srol_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='留样操作互斥锁表';

-- ---------------------------
-- 6.8 餐次时间窗口配置表（cook_meal_time_config）
-- ---------------------------
DROP TABLE IF EXISTS `cook_meal_time_config`;
CREATE TABLE `cook_meal_time_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `meal_type` VARCHAR(20) NOT NULL COMMENT '餐次: breakfast/lunch/dinner/supper',
  `allow_start_time` TIME NOT NULL COMMENT '允许开始时间',
  `allow_end_time` TIME NOT NULL COMMENT '允许结束时间',
  `org_id` BIGINT DEFAULT NULL COMMENT '组织ID，NULL表示全局默认',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meal_org` (`meal_type`, `org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='餐次时间窗口配置';

-- 插入默认餐次时间配置
INSERT INTO `cook_meal_time_config` (`meal_type`, `allow_start_time`, `allow_end_time`) VALUES
('breakfast', '06:00:00', '09:00:00'),
('lunch', '10:30:00', '13:30:00'),
('dinner', '16:00:00', '19:30:00'),
('supper', '20:00:00', '22:00:00');

-- ---------------------------
-- 7.1 健康证表（health_certificate）
-- ---------------------------
DROP TABLE IF EXISTS `health_certificate`;
CREATE TABLE `health_certificate` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '健康证ID',
  `employee_id` BIGINT NOT NULL COMMENT '员工ID',
  `employee_name` VARCHAR(50) NOT NULL COMMENT '员工姓名（冗余）',
  `certificate_no` VARCHAR(100) DEFAULT NULL COMMENT '健康证编号',
  `issue_date` DATE DEFAULT NULL COMMENT '发证日期',
  `expiry_date` DATE DEFAULT NULL COMMENT '到期日期',
  `certificate_images` VARCHAR(500) DEFAULT NULL COMMENT '健康证照片URL',
  `issuing_authority` VARCHAR(100) DEFAULT NULL COMMENT '发证机构',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending=未办理，valid=有效，expiring=即将过期，expired=已过期',
  `warning_days` INT DEFAULT 30 COMMENT '到期前预警天数',
  `remark` VARCHAR(300) DEFAULT NULL COMMENT '备注',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_hc_employee` (`employee_id`),
  KEY `idx_hc_status` (`status`),
  KEY `idx_hc_expiry_date` (`expiry_date`),
  KEY `idx_hc_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='健康证表';

-- ---------------------------
-- 7.2 晨检记录表（health_check_record）
-- ---------------------------
DROP TABLE IF EXISTS `health_check_record`;
CREATE TABLE `health_check_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '晨检ID',
  `check_no` VARCHAR(50) NOT NULL COMMENT '晨检编号',
  `employee_id` BIGINT NOT NULL COMMENT '员工ID',
  `employee_name` VARCHAR(50) NOT NULL COMMENT '员工姓名（冗余）',
  `check_date` DATE NOT NULL COMMENT '晨检日期',
  `check_time` DATETIME NOT NULL COMMENT '晨检时间',
  `temperature` DECIMAL(4,1) DEFAULT NULL COMMENT '体温（℃）',
  `face_image_url` VARCHAR(500) DEFAULT NULL COMMENT '人脸照片URL',
  `face_match_score` DECIMAL(5,2) DEFAULT NULL COMMENT '人脸匹配度（0-100）',
  `certificate_status` VARCHAR(20) DEFAULT NULL COMMENT '健康证状态（冗余）',
  `hand_hygiene` VARCHAR(20) DEFAULT NULL COMMENT '手部卫生：pass=合格，fail=不合格',
  `uniform_check` VARCHAR(20) DEFAULT NULL COMMENT '着装检查：pass=合格，fail=不合格',
  `health_status` VARCHAR(20) DEFAULT NULL COMMENT '健康状况：normal=正常，abnormal=异常',
  `check_result` VARCHAR(20) NOT NULL DEFAULT 'pass' COMMENT '晨检结果：pass=通过，fail=不通过',
  `fail_reason` VARCHAR(300) DEFAULT NULL COMMENT '不通过原因',
  `checker_id` BIGINT DEFAULT NULL COMMENT '晨检员ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending_check' COMMENT '状态：pending_check=待晨检，checking=晨检中，completed_normal=正常，completed_abnormal=异常，archived=已归档',
  `remark` VARCHAR(300) DEFAULT NULL COMMENT '备注',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hcr_check_no` (`check_no`),
  KEY `idx_hcr_employee` (`employee_id`),
  KEY `idx_hcr_date` (`check_date`),
  KEY `idx_hcr_result` (`check_result`),
  KEY `idx_hcr_status` (`status`),
  KEY `idx_hcr_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='晨检记录表';

-- ---------------------------
-- 7.2.1 晨检任务联动状态表（health_check_task_linkage）
-- ---------------------------
DROP TABLE IF EXISTS `health_check_task_linkage`;
CREATE TABLE `health_check_task_linkage` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '联动状态ID',
  `record_id` BIGINT DEFAULT NULL COMMENT '关联晨检记录ID',
  `employee_id` BIGINT NOT NULL COMMENT '员工ID',
  `employee_name` VARCHAR(50) NOT NULL COMMENT '员工姓名（冗余）',
  `check_date` DATE NOT NULL COMMENT '晨检日期',
  `should_check` TINYINT NOT NULL DEFAULT 1 COMMENT '是否应检：1=应检，0=剔除',
  `duty_type` VARCHAR(20) NOT NULL DEFAULT 'formal' COMMENT '任务标签：formal=正式在岗，substitute=临时替班',
  `linkage_status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '联动状态：active=有效，excluded=已剔除',
  `base_org_id` BIGINT DEFAULT NULL COMMENT '任务基准组织ID',
  `base_org_name` VARCHAR(100) DEFAULT NULL COMMENT '任务基准组织名称',
  `base_position` VARCHAR(100) DEFAULT NULL COMMENT '任务基准岗位',
  `current_org_id` BIGINT DEFAULT NULL COMMENT '当前归属组织ID',
  `current_org_name` VARCHAR(100) DEFAULT NULL COMMENT '当前归属组织名称',
  `current_position` VARCHAR(100) DEFAULT NULL COMMENT '当前归属岗位',
  `reason_code` VARCHAR(50) DEFAULT NULL COMMENT '最近一次联动原因编码',
  `reason_desc` VARCHAR(255) DEFAULT NULL COMMENT '最近一次联动原因说明',
  `last_signature` VARCHAR(255) DEFAULT NULL COMMENT '最近一次处理签名',
  `last_event_time` DATETIME DEFAULT NULL COMMENT '最近一次联动时间',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hctl_employee_date` (`employee_id`, `check_date`),
  KEY `idx_hctl_record` (`record_id`),
  KEY `idx_hctl_should_check` (`should_check`),
  KEY `idx_hctl_current_org` (`current_org_id`),
  KEY `idx_hctl_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='晨检任务联动状态表';

-- ---------------------------
-- 7.2.2 晨检任务联动留痕表（health_check_task_linkage_log）
-- ---------------------------
DROP TABLE IF EXISTS `health_check_task_linkage_log`;
CREATE TABLE `health_check_task_linkage_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '联动日志ID',
  `linkage_id` BIGINT DEFAULT NULL COMMENT '联动状态ID',
  `record_id` BIGINT DEFAULT NULL COMMENT '晨检记录ID',
  `employee_id` BIGINT NOT NULL COMMENT '员工ID',
  `employee_name` VARCHAR(50) NOT NULL COMMENT '员工姓名（冗余）',
  `check_date` DATE NOT NULL COMMENT '晨检日期',
  `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
  `event_name` VARCHAR(100) NOT NULL COMMENT '事件名称',
  `reason_code` VARCHAR(50) DEFAULT NULL COMMENT '原因编码',
  `reason_desc` VARCHAR(255) DEFAULT NULL COMMENT '原因说明',
  `before_snapshot` JSON DEFAULT NULL COMMENT '变更前快照',
  `after_snapshot` JSON DEFAULT NULL COMMENT '变更后快照',
  `event_signature` VARCHAR(255) DEFAULT NULL COMMENT '事件签名',
  `org_id` BIGINT DEFAULT NULL COMMENT '当前组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_hctll_record` (`record_id`),
  KEY `idx_hctll_employee_date` (`employee_id`, `check_date`),
  KEY `idx_hctll_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='晨检任务联动留痕表';

-- ---------------------------
-- 7.3 人脸特征表（health_face_feature）
-- ---------------------------
DROP TABLE IF EXISTS `health_face_feature`;
CREATE TABLE `health_face_feature` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `employee_id` BIGINT NOT NULL COMMENT '员工ID',
  `face_image_url` VARCHAR(500) DEFAULT NULL COMMENT '人脸照片URL（加密存储）',
  `face_feature_vector` TEXT DEFAULT NULL COMMENT '人脸特征向量（加密存储，用于AI识别）',
  `feature_version` VARCHAR(20) DEFAULT 'v1.0' COMMENT '特征提取算法版本',
  `quality_score` DECIMAL(5,2) DEFAULT NULL COMMENT '照片质量评分（0-100）',
  `is_active` TINYINT DEFAULT 1 COMMENT '是否启用：0=否，1=是',
  `enrolled_at` DATETIME DEFAULT NULL COMMENT '录入时间',
  `last_used_at` DATETIME DEFAULT NULL COMMENT '最后使用时间',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hff_employee` (`employee_id`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_hff_active` (`is_active`),
  KEY `idx_hff_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人脸特征表';
