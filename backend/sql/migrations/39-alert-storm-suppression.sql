-- 39: 告警风暴抑制字段
-- 用于同一设备 + 同类型告警在时间窗口内高频触发时的合并与抑制
ALTER TABLE device_alert
    ADD COLUMN storm_group_id VARCHAR(50) NULL COMMENT '风暴批次标识' AFTER recording_id,
    ADD COLUMN suppressed TINYINT DEFAULT 0 COMMENT '是否被抑制: 0=正常, 1=被抑制' AFTER storm_group_id,
    ADD COLUMN suppressed_count INT DEFAULT 0 COMMENT '该告警代表的被抑制告警数' AFTER suppressed;

CREATE INDEX idx_da_storm_group ON device_alert(storm_group_id);
CREATE INDEX idx_da_suppressed ON device_alert(suppressed);
