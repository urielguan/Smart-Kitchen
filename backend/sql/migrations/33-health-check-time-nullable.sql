-- 健康晨检：check_time 改为允许 NULL
-- 待检记录不应设置 checkTime，只有实际执行晨检时才记录时间
ALTER TABLE health_check_record MODIFY COLUMN check_time DATETIME DEFAULT NULL COMMENT '晨检时间';

-- 修复历史数据：将 pending_check 状态且 check_time 为当天零点的记录清空
UPDATE health_check_record SET check_time = NULL WHERE status = 'pending_check' AND check_time IS NOT NULL AND TIME(check_time) = '00:00:00';
