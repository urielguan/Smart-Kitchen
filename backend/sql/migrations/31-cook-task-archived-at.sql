-- 31: cook_task 新增 archived_at 列（PRD要求归档后写入归档时间）
ALTER TABLE cook_task
    ADD COLUMN archived_at DATETIME DEFAULT NULL COMMENT '归档时间：复核通过后归档时写入';