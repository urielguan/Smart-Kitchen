-- ============================================================
-- 智慧厨房管理平台 - 数据库初始化脚本（第六部分）
-- 设备管理与告警模块（device_）
-- ============================================================

USE `smart_food_safety`;

-- ---------------------------
-- 8.1 设备信息表（device_info）
-- ---------------------------
DROP TABLE IF EXISTS `device_info`;
CREATE TABLE `device_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '设备ID',
  `device_code` VARCHAR(50) NOT NULL COMMENT '设备编码',
  `device_name` VARCHAR(100) NOT NULL COMMENT '设备名称',
  `device_type` VARCHAR(30) NOT NULL COMMENT '设备类型：camera=监控摄像头，sensor=温湿度传感器，scale=智能秤，terminal=晨检终端，pos=POS机',
  `device_model` VARCHAR(100) DEFAULT NULL COMMENT '设备型号',
  `manufacturer` VARCHAR(100) DEFAULT NULL COMMENT '生产厂商',
  `sn` VARCHAR(100) DEFAULT NULL COMMENT '设备序列号',
  `mac_address` VARCHAR(50) DEFAULT NULL COMMENT 'MAC地址',
  `ip_address` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
  `location_desc` VARCHAR(200) DEFAULT NULL COMMENT '位置描述（如：后厨1号操作台）',
  `position_x` DECIMAL(10,3) DEFAULT NULL COMMENT 'X坐标（用于3D地图定位，单位：米）',
  `position_y` DECIMAL(10,3) DEFAULT NULL COMMENT 'Y坐标（用于3D地图定位，单位：米）',
  `position_z` DECIMAL(10,3) DEFAULT NULL COMMENT 'Z坐标（用于3D地图定位，单位：米）',
  `rotation_x` DECIMAL(8,3) DEFAULT 0.000 COMMENT 'X轴旋转角度（Three.js渲染用）',
  `rotation_y` DECIMAL(8,3) DEFAULT 0.000 COMMENT 'Y轴旋转角度（Three.js渲染用）',
  `rotation_z` DECIMAL(8,3) DEFAULT 0.000 COMMENT 'Z轴旋转角度（Three.js渲染用）',
  `model_3d_url` VARCHAR(500) DEFAULT NULL COMMENT '3D模型文件URL（.gltf/.glb格式）',
  `model_3d_scale` DECIMAL(5,3) DEFAULT 1.000 COMMENT '3D模型缩放比例',
  `install_date` DATE DEFAULT NULL COMMENT '安装日期',
  `warranty_expires_at` DATE DEFAULT NULL COMMENT '保修到期日',
  `maintenance_cycle_days` INT DEFAULT NULL COMMENT '维保周期（天）',
  `manager_name` VARCHAR(50) DEFAULT NULL COMMENT '负责人姓名',
  `manager_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `last_maintenance_at` DATE DEFAULT NULL COMMENT '上次维保日期',
  `next_maintenance_at` DATE DEFAULT NULL COMMENT '下次维保日期',
  `online_status` VARCHAR(20) NOT NULL DEFAULT 'offline' COMMENT '在线状态：online=在线，offline=离线，fault=故障',
  `last_heartbeat_at` DATETIME DEFAULT NULL COMMENT '最后心跳时间',
  `config_params` JSON DEFAULT NULL COMMENT '设备配置参数（JSON格式）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active=启用，inactive=停用，maintenance=维护中',
  `remark` VARCHAR(300) DEFAULT NULL COMMENT '备注',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_code` (`device_code`, `org_id`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_di_type` (`device_type`),
  KEY `idx_di_online_status` (`online_status`),
  KEY `idx_di_status` (`status`),
  KEY `idx_di_org` (`org_id`),
  KEY `idx_di_next_maintenance` (`next_maintenance_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备信息表';

-- ---------------------------
-- 8.2 设备数据采集表（device_data_log）
-- ---------------------------
DROP TABLE IF EXISTS `device_data_log`;
CREATE TABLE `device_data_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `device_id` BIGINT NOT NULL COMMENT '设备ID',
  `device_code` VARCHAR(50) NOT NULL COMMENT '设备编码（冗余）',
  `data_type` VARCHAR(30) NOT NULL COMMENT '数据类型：temperature=温度，humidity=湿度，weight=重量，heartbeat=心跳',
  `data_value` DECIMAL(10,3) DEFAULT NULL COMMENT '数据值',
  `data_unit` VARCHAR(20) DEFAULT NULL COMMENT '数据单位（℃/%/kg等）',
  `data_json` JSON DEFAULT NULL COMMENT '完整数据（JSON格式，存储复杂数据）',
  `collected_at` DATETIME NOT NULL COMMENT '采集时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ddl_device` (`device_id`),
  KEY `idx_ddl_type` (`data_type`),
  KEY `idx_ddl_collected_at` (`collected_at`),
  KEY `idx_ddl_device_type_collected` (`device_id`, `data_type`, `collected_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备数据采集表';

-- ---------------------------
-- 8.3 告警记录表（device_alert）
-- ---------------------------
DROP TABLE IF EXISTS `device_alert`;
CREATE TABLE `device_alert` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '告警ID',
  `alert_no` VARCHAR(50) NOT NULL COMMENT '告警编号',
  `alert_type` VARCHAR(30) NOT NULL COMMENT '告警类型：device_offline=设备离线，device_fault=设备故障，temp_abnormal=温度异常，humidity_abnormal=湿度异常，ai_violation=AI违规识别，threshold_exceed=阈值超限，material=物料告警',
  `alert_rule_id` BIGINT DEFAULT NULL COMMENT '关联告警规则ID',
  `alert_level` VARCHAR(20) NOT NULL DEFAULT 'info' COMMENT '告警级别：info=提示，warning=警告，error=错误，critical=严重',
  `device_id` BIGINT DEFAULT NULL COMMENT '关联设备ID',
  `device_name` VARCHAR(100) DEFAULT NULL COMMENT '设备名称（冗余）',
  `material_id` BIGINT DEFAULT NULL COMMENT '关联物料ID（物料告警时使用）',
  `alert_content` VARCHAR(500) NOT NULL COMMENT '告警内容',
  `alert_detail` JSON DEFAULT NULL COMMENT '告警详情（JSON格式）',
  `alert_images` JSON DEFAULT NULL COMMENT '告警截图/照片（JSON数组）',
  `alert_video_url` VARCHAR(500) DEFAULT NULL COMMENT '告警视频URL',
  `recording_id` BIGINT NULL COMMENT '关联录像段ID (device_monitor_record.id)',
  `triggered_at` DATETIME NOT NULL COMMENT '触发时间',
  `assigned_to` BIGINT DEFAULT NULL COMMENT '指派处理人ID',
  `assigned_at` DATETIME DEFAULT NULL COMMENT '指派时间',
  `handled_by` BIGINT DEFAULT NULL COMMENT '实际处理人ID',
  `handled_at` DATETIME DEFAULT NULL COMMENT '处理时间',
  `handle_result` VARCHAR(500) DEFAULT NULL COMMENT '处理结果',
  `handle_images` JSON DEFAULT NULL COMMENT '处理照片（JSON数组）',
  `reviewed_by` BIGINT DEFAULT NULL COMMENT '复核人ID',
  `reviewed_at` DATETIME DEFAULT NULL COMMENT '复核时间',
  `review_result` VARCHAR(500) DEFAULT NULL COMMENT '复核结果',
  `review_remark` VARCHAR(300) DEFAULT NULL COMMENT '复核备注',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending=待处理，assigned=已指派，handling=处理中，handled=已处置，reviewed=已复核，closed=已关闭',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alert_no` (`alert_no`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_da_type` (`alert_type`),
  KEY `idx_da_level` (`alert_level`),
  KEY `idx_da_device` (`device_id`),
  KEY `idx_da_status` (`status`),
  KEY `idx_da_triggered_at` (`triggered_at`),
  KEY `idx_da_assigned_to` (`assigned_to`),
  KEY `idx_da_material_id` (`material_id`),
  KEY `idx_da_rule_id` (`alert_rule_id`),
  KEY `idx_da_org` (`org_id`),
  KEY `idx_alert_recording` (`recording_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警记录表';

-- ---------------------------
-- 8.4 告警策略配置表（device_alert_rule）
-- ---------------------------
DROP TABLE IF EXISTS `device_alert_rule`;
CREATE TABLE `device_alert_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  `rule_name` VARCHAR(100) NOT NULL COMMENT '规则名称',
  `rule_type` VARCHAR(30) NOT NULL COMMENT '规则类型：threshold=阈值告警，offline=离线告警，ai_event=AI事件告警，material=物料告警',
  `device_type` VARCHAR(30) DEFAULT NULL COMMENT '设备类型（material类型时为空）',
  `device_ids` TEXT DEFAULT NULL COMMENT '适用设备ID列表（逗号分隔，为空则该规则不对任何设备生效）',
  `material_ids` TEXT DEFAULT NULL COMMENT '适用物料ID列表（逗号分隔，rule_type=material时使用，为空则该规则不对任何物料生效）',
  `condition_json` JSON DEFAULT NULL COMMENT '触发条件（JSON格式，material类型时为空）',
  `alert_level` VARCHAR(20) NOT NULL DEFAULT 'warning' COMMENT '告警级别',
  `notify_channels` VARCHAR(200) DEFAULT NULL COMMENT '通知渠道（逗号分隔：sms,email,wechat,system）',
  `notify_users` VARCHAR(500) DEFAULT NULL COMMENT '通知用户ID列表（逗号分隔）',
  `dispatch_scope_roles` VARCHAR(500) DEFAULT NULL COMMENT '告警派单范围（角色ID列表，逗号分隔）',
  `is_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0=禁用，1=启用',
  `auto_dispatch` TINYINT NOT NULL DEFAULT 0 COMMENT '自动派单：0=关闭，1=开启',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_dar_type` (`rule_type`),
  KEY `idx_dar_device_type` (`device_type`),
  KEY `idx_dar_enabled` (`is_enabled`),
  KEY `idx_dar_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警策略配置表';

-- ---------------------------
-- 8.5 告警派单表（device_alert_dispatch）
-- ---------------------------
DROP TABLE IF EXISTS `device_alert_dispatch`;
CREATE TABLE `device_alert_dispatch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '派单ID',
  `dispatch_no` VARCHAR(50) NOT NULL COMMENT '派单编号',
  `alert_id` BIGINT NOT NULL COMMENT '告警ID',
  `alert_no` VARCHAR(50) DEFAULT NULL COMMENT '告警编号（冗余）',
  `dispatch_type` VARCHAR(20) NOT NULL COMMENT '派单方式：auto=自动，manual=人工',
  `assigner_id` BIGINT DEFAULT 0 COMMENT '派单人ID（自动派单为0）',
  `assigner_name` VARCHAR(50) DEFAULT '系统' COMMENT '派单人姓名',
  `handler_id` BIGINT NOT NULL COMMENT '处理人ID',
  `handler_name` VARCHAR(50) DEFAULT NULL COMMENT '处理人姓名',
  `deadline` DATETIME DEFAULT NULL COMMENT '处理截止时间',
  `priority` VARCHAR(20) DEFAULT 'medium' COMMENT '优先级：high/medium/low',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending=待处理，processing=处理中，completed=已处理，reviewed=已复核，cancelled=已取消',
  `handle_result` VARCHAR(500) DEFAULT NULL COMMENT '处理结果',
  `handle_attachments` JSON DEFAULT NULL COMMENT '处理附件（JSON数组，URL字符串，最多5个）',
  `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `reviewed_by` BIGINT DEFAULT NULL COMMENT '复核人ID',
  `reviewed_at` DATETIME DEFAULT NULL COMMENT '复核时间',
  `review_result` VARCHAR(500) DEFAULT NULL COMMENT '复核结果',
  `review_attachments` JSON DEFAULT NULL COMMENT '复核附件（JSON数组，格式 [{url,name}]）',
  `org_id` BIGINT NOT NULL COMMENT '所属组织ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_no` (`dispatch_no`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)),
  KEY `idx_dad_alert_id` (`alert_id`),
  KEY `idx_dad_handler_id` (`handler_id`),
  KEY `idx_dad_status` (`status`),
  KEY `idx_dad_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警派单表';

-- ---------------------------
-- 8.6 告警工单处理记录表（device_alert_work_order_record）
-- ---------------------------
DROP TABLE IF EXISTS `device_alert_work_order_record`;
CREATE TABLE `device_alert_work_order_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `dispatch_id` BIGINT NOT NULL COMMENT '派单ID',
  `alert_id` BIGINT NOT NULL COMMENT '告警ID',
  `action` VARCHAR(30) NOT NULL COMMENT '操作类型：dispatch=派单，process=处理，complete=完成，review=复核，cancel=取消',
  `action_name` VARCHAR(50) DEFAULT NULL COMMENT '操作名称',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作人姓名',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '操作内容',
  `attachments` JSON DEFAULT NULL COMMENT '附件（JSON数组，URL字符串）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_dawor_dispatch_id` (`dispatch_id`),
  KEY `idx_dawor_alert_id` (`alert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警工单处理记录表';

-- ---------------------------
-- 8.7 违规事件操作日志表（violation_operation_log）
-- ---------------------------
DROP TABLE IF EXISTS `violation_operation_log`;
CREATE TABLE `violation_operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `alert_id` BIGINT NOT NULL COMMENT '关联告警ID（device_alert.id）',
    `action` VARCHAR(50) NOT NULL COMMENT '操作类型：created/handled/batch_handled/reviewed/reopened',
    `action_name` VARCHAR(50) NOT NULL COMMENT '操作名称',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作人姓名',
    `content` VARCHAR(1000) DEFAULT NULL COMMENT '操作内容/备注',
    `terminal` VARCHAR(20) DEFAULT 'web' COMMENT '操作终端',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_alert_id` (`alert_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='违规事件操作日志';

-- ---------------------------
-- 8.8 设备监控录像索引表（device_monitor_record）
-- ---------------------------
DROP TABLE IF EXISTS `device_monitor_record`;
CREATE TABLE `device_monitor_record` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT COMMENT '录像ID',
    `device_id`       BIGINT NOT NULL COMMENT '设备ID（device_info.id）',
    `device_name`     VARCHAR(100) DEFAULT NULL COMMENT '设备名称（冗余，从device_info同步）',
    `location`        VARCHAR(200) DEFAULT NULL COMMENT '安装位置（冗余）',
    `file_path`       VARCHAR(500) NOT NULL COMMENT 'MP4文件磁盘绝对路径',
    `file_name`       VARCHAR(200) NOT NULL COMMENT '文件名（如 rec_20260508_103000.mp4）',
    `file_size`       BIGINT DEFAULT 0 COMMENT '文件大小（字节）',
    `duration`        INT DEFAULT 0 COMMENT '录像时长（秒）',
    `start_time`      DATETIME NOT NULL COMMENT '录像开始时间（时钟对齐）',
    `end_time`        DATETIME DEFAULT NULL COMMENT '录像结束时间',
    `resolution`      VARCHAR(20) DEFAULT '1280x720' COMMENT '分辨率',
    `recording_type`  VARCHAR(20) NOT NULL DEFAULT 'continuous'
                      COMMENT '录像类型：continuous=连续录像，alarm=告警录像，manual=手动录像',
    `status`          VARCHAR(20) NOT NULL DEFAULT 'recording'
                      COMMENT '状态：recording=录制中，completed=已完成，archived=已归档',
    `has_ai_marks`    TINYINT NOT NULL DEFAULT 0 COMMENT '是否关联AI违规标记：0=否，1=是',
    `thumbnail_path`  VARCHAR(500) DEFAULT NULL COMMENT '缩略图文件路径',
    `retention_days`  INT NOT NULL DEFAULT 30 COMMENT '保留天数（普通30，证据365）',
    `expires_at`      DATETIME DEFAULT NULL COMMENT '过期时间（start_time + retention_days）',
    `is_evidence`     TINYINT NOT NULL DEFAULT 0 COMMENT '是否证据录像：0=否，1=是',
    `archived_at`     DATETIME DEFAULT NULL COMMENT '归档时间',
    `evidence_reason` VARCHAR(255) DEFAULT NULL COMMENT '标记为证据的原因',
    `org_id`          BIGINT NOT NULL COMMENT '所属组织ID',
    `tenant_id`       BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `created_by`      BIGINT DEFAULT NULL COMMENT '创建人ID',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`      BIGINT DEFAULT NULL COMMENT '更新人ID',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                      COMMENT '更新时间',
    `deleted`         TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_dmr_device` (`device_id`),
    KEY `idx_dmr_start_time` (`start_time`),
    KEY `idx_dmr_device_time` (`device_id`, `start_time`, `end_time`),
    KEY `idx_dmr_org` (`org_id`),
    KEY `idx_dmr_status` (`status`),
    KEY `idx_dmr_expires` (`expires_at`, `is_evidence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备监控录像索引表';

-- ---------------------------
-- 8.9 视频片段截取记录表（device_video_clip）
-- ---------------------------
DROP TABLE IF EXISTS `device_video_clip`;
CREATE TABLE `device_video_clip` (
    `id`                BIGINT AUTO_INCREMENT   COMMENT '片段ID',
    `recording_id`      BIGINT NOT NULL         COMMENT '来源录像ID (device_monitor_record.id)',
    `device_id`         BIGINT NOT NULL         COMMENT '设备ID (冗余自来源录像)',
    `device_name`       VARCHAR(100)            COMMENT '设备名称 (冗余)',
    `org_id`            BIGINT NOT NULL         COMMENT '所属组织ID (用于@DataScope过滤)',
    `start_time_offset` INT NOT NULL            COMMENT '片段开始时间点 (秒，相对于源录像起始)',
    `end_time_offset`   INT NOT NULL            COMMENT '片段结束时间点 (秒，相对于源录像起始)',
    `clip_duration`     INT NOT NULL            COMMENT '片段时长 (秒)',
    `file_path`         VARCHAR(500)            COMMENT 'MP4文件磁盘绝对路径',
    `file_name`         VARCHAR(200)            COMMENT '文件名',
    `file_size`         BIGINT DEFAULT 0        COMMENT '文件大小 (字节)',
    `purpose_tag`       VARCHAR(30) NOT NULL    COMMENT '用途标签: violation_trace/accident_review/process_review',
    `status`            VARCHAR(20) NOT NULL DEFAULT 'processing' COMMENT '导出状态: processing/completed/failed',
    `fail_reason`       VARCHAR(500) DEFAULT NULL COMMENT '失败原因 (status=failed时)',
    `version_no`        INT NOT NULL DEFAULT 1  COMMENT '不可变版本号，重复截取时递增',
    `tenant_id`         BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `created_by`        BIGINT DEFAULT NULL     COMMENT '创建人ID',
    `created_at`        DATETIME DEFAULT NULL   COMMENT '创建时间',
    `updated_by`        BIGINT DEFAULT NULL     COMMENT '更新人ID',
    `updated_at`        DATETIME DEFAULT NULL   COMMENT '更新时间',
    `deleted`           TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    INDEX `idx_dvc_recording` (`recording_id`),
    INDEX `idx_dvc_device` (`device_id`),
    INDEX `idx_dvc_org` (`org_id`),
    INDEX `idx_dvc_status` (`status`),
    INDEX `idx_dvc_purpose` (`purpose_tag`),
    INDEX `idx_dvc_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='视频片段截取记录';

-- ---------------------------
-- 8.10 视频回放截图记录表（device_screenshot）
-- ---------------------------
DROP TABLE IF EXISTS `device_screenshot`;
CREATE TABLE `device_screenshot` (
    `id`                  BIGINT AUTO_INCREMENT   COMMENT '截图ID',
    `recording_id`        BIGINT NOT NULL         COMMENT '来源录像ID (device_monitor_record.id)',
    `device_id`           BIGINT NOT NULL         COMMENT '设备ID (冗余自来源录像)',
    `device_name`         VARCHAR(100)            COMMENT '设备名称 (冗余)',
    `org_id`              BIGINT NOT NULL         COMMENT '所属组织ID (用于@DataScope过滤)',
    `capture_time_offset` INT NOT NULL            COMMENT '抓拍时间点 (秒，相对于源录像起始)',
    `file_path`           VARCHAR(500)            COMMENT '截图文件磁盘绝对路径',
    `file_name`           VARCHAR(200)            COMMENT '文件名',
    `file_size`           BIGINT DEFAULT 0        COMMENT '文件大小 (字节)',
    `resolution`          VARCHAR(20)             COMMENT '截图分辨率 (如 1280x720)',
    `purpose_tag`         VARCHAR(30) NOT NULL    COMMENT '用途标签: violation_trace/accident_review/process_review',
    `status`              VARCHAR(20) NOT NULL DEFAULT 'completed' COMMENT '状态: completed/failed',
    `version_no`          INT NOT NULL DEFAULT 1  COMMENT '不可变版本号',
    `tenant_id`           BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `created_by`          BIGINT DEFAULT NULL     COMMENT '创建人ID',
    `created_at`          DATETIME DEFAULT NULL   COMMENT '创建时间',
    `updated_by`          BIGINT DEFAULT NULL     COMMENT '更新人ID',
    `updated_at`          DATETIME DEFAULT NULL   COMMENT '更新时间',
    `deleted`             TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    INDEX `idx_ds_recording` (`recording_id`),
    INDEX `idx_ds_device` (`device_id`),
    INDEX `idx_ds_org` (`org_id`),
    INDEX `idx_ds_purpose` (`purpose_tag`),
    INDEX `idx_ds_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='视频回放截图记录';

-- ---------------------------
-- 8.11 录像清理审计日志表（device_cleanup_audit_log）
-- ---------------------------
DROP TABLE IF EXISTS `device_cleanup_audit_log`;
CREATE TABLE `device_cleanup_audit_log` (
    `id`                          BIGINT AUTO_INCREMENT COMMENT '审计日志ID',
    `batch_id`                    VARCHAR(50) NOT NULL COMMENT '清理批次号（CLN-yyyyMMddHHmmss）',
    `recording_id`                BIGINT COMMENT '被删除的录像ID',
    `device_id`                   BIGINT COMMENT '设备ID',
    `file_path`                   VARCHAR(500) COMMENT '被删除的录像文件路径',
    `file_size`                   BIGINT DEFAULT 0 COMMENT '文件大小（字节）',
    `reason`                      VARCHAR(100) NOT NULL COMMENT '删除原因：retention_expired/orphan_clip/orphan_screenshot',
    `cascaded_clips`              INT DEFAULT 0 COMMENT '级联删除的片段数',
    `cascaded_screenshots`        INT DEFAULT 0 COMMENT '级联删除的截图数',
    `cascaded_alerts_nullified`   INT DEFAULT 0 COMMENT '置空的告警关联数',
    `created_at`                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_dcal_batch` (`batch_id`),
    INDEX `idx_dcal_recording` (`recording_id`),
    INDEX `idx_dcal_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='录像清理审计日志';

-- ---------------------------
-- 8.12 证据包导出记录表（device_evidence_package）
-- ---------------------------
DROP TABLE IF EXISTS `device_evidence_package`;
CREATE TABLE `device_evidence_package` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `package_no` VARCHAR(64) NOT NULL COMMENT '证据包编号 EP-YYYYMMDD-XXXX',
  `package_name` VARCHAR(255) NOT NULL COMMENT '证据包名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'packing' COMMENT '打包状态: packing/completed/failed/expired',
  `recording_ids` JSON COMMENT '包含的录像ID列表',
  `clip_ids` JSON COMMENT '包含的片段ID列表',
  `screenshot_ids` JSON COMMENT '包含的截图ID列表',
  `file_path` VARCHAR(500) COMMENT 'ZIP文件磁盘绝对路径',
  `file_name` VARCHAR(255) COMMENT 'ZIP文件名',
  `file_size` BIGINT COMMENT 'ZIP文件大小(字节)',
  `item_count` INT DEFAULT 0 COMMENT '包含文件数量',
  `org_id` BIGINT COMMENT '所属组织ID',
  `fail_reason` VARCHAR(500) COMMENT '失败原因',
  `expires_at` DATETIME COMMENT '下载过期时间',
  `downloaded_at` DATETIME COMMENT '最近下载时间',
  `download_count` INT DEFAULT 0 COMMENT '下载次数',
  `tenant_id` BIGINT,
  `created_by` BIGINT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_by` BIGINT,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_dep_status` (`status`),
  INDEX `idx_dep_created` (`created_at`),
  INDEX `idx_dep_org` (`org_id`),
  UNIQUE INDEX `uk_dep_package_no` (`package_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='证据包导出记录';
