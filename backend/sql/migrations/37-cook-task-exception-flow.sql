-- ============================================================
-- 37-cook-task-exception-flow.sql
-- cook_task 新增采集/同步/补偿异常状态字段
-- ============================================================

ALTER TABLE cook_task
  ADD COLUMN collection_status VARCHAR(32) DEFAULT 'normal' COMMENT '采集运行状态：normal=正常, interrupted=采集中断, pending_recovery=待恢复' AFTER temp_abnormal_confirmed_at,
  ADD COLUMN last_temperature_record_at DATETIME DEFAULT NULL COMMENT '最近一次采样时间' AFTER collection_status,
  ADD COLUMN sync_status VARCHAR(32) DEFAULT 'normal' COMMENT '同步状态：normal=正常, sync_failed=同步失败, conflict_pending=冲突待处理' AFTER last_temperature_record_at,
  ADD COLUMN sync_retry_count INT DEFAULT 0 COMMENT '同步重试次数' AFTER sync_status,
  ADD COLUMN sync_retry_limit_reached TINYINT DEFAULT 0 COMMENT '是否达到自动重试上限：0=未达到，1=已达到' AFTER sync_retry_count,
  ADD COLUMN latest_sync_failure_reason VARCHAR(500) DEFAULT NULL COMMENT '最近同步失败原因' AFTER sync_retry_limit_reached,
  ADD COLUMN compensation_status VARCHAR(32) DEFAULT 'none' COMMENT '补偿状态：none=无, pending=待处理, resolved=已处理' AFTER latest_sync_failure_reason;
