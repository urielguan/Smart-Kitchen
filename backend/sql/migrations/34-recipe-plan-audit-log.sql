-- =====================================================
-- 34. 菜谱计划审批日志表
-- 用途：记录菜谱计划的多轮审批历史（提交/驳回/重新提交/通过等）
-- =====================================================

CREATE TABLE IF NOT EXISTS `recipe_plan_audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `plan_id` BIGINT NOT NULL COMMENT '计划ID',
    `round` INT NOT NULL DEFAULT 1 COMMENT '审批轮次',
    `action` VARCHAR(32) NOT NULL COMMENT '操作: submit/resubmit/approve/reject/save_draft',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作人姓名',
    `remark` TEXT COMMENT '审核意见或备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_plan_id` (`plan_id`),
    INDEX `idx_plan_round` (`plan_id`, `round`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜谱计划审批日志';
