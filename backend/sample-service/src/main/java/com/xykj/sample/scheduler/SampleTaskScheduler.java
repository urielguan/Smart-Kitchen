package com.xykj.sample.scheduler;

import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.service.AuditLogService;
import com.xykj.sample.entity.SampleRecord;
import com.xykj.sample.mapper.SampleRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 留样任务定时调度器
 * <p>
 * 1. 每日00:05补偿扫描最近完成但漏生成的烹饪任务，补生成留样任务（pending_sample状态）
 * 2. 每小时检查并标记超期未销样的记录为overdue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleTaskScheduler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String STATUS_PENDING_SAMPLE = "pending_sample";
    private static final String STATUS_VOIDED = "voided";
    private static final String ORIGIN_SYSTEM_BACKFILL = "system_backfill";

    private final JdbcTemplate jdbcTemplate;
    private final SampleRecordMapper sampleRecordMapper;
    private final AuditLogService auditLogService;

    /**
     * 每日00:05执行：补偿扫描最近3天已完成但缺失留样记录的烹饪任务
     */
    @Scheduled(cron = "0 5 0 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void autoGenerateSampleTasks() {
        log.info("===== 开始补偿扫描留样任务 =====");

        String sql = """
            SELECT ct.id,
                   ct.menu_id,
                   ct.menu_name,
                   ct.org_id,
                   ct.tenant_id,
                   ct.end_time,
                   rp.plan_date,
                   rp.meal_type
            FROM cook_task ct
            LEFT JOIN recipe_plan rp ON rp.id = ct.plan_id AND rp.deleted = 0
            WHERE ct.status = 'completed'
              AND ct.deleted = 0
              AND ct.end_time >= DATE_SUB(NOW(), INTERVAL 3 DAY)
              AND NOT EXISTS (
                SELECT 1 FROM sample_record sr
                WHERE sr.task_id = ct.id
                  AND sr.deleted = 0
                  AND sr.status <> 'voided'
              )
            """;

        List<Map<String, Object>> cookTasks = jdbcTemplate.queryForList(sql);
        if (cookTasks.isEmpty()) {
            log.info("无需自动生成的烹饪任务");
            return;
        }

        int count = 0;

        for (Map<String, Object> task : cookTasks) {
            Long cookTaskId = ((Number) task.get("id")).longValue();
            Long menuId = ((Number) task.get("menu_id")).longValue();
            String menuName = (String) task.get("menu_name");
            Long orgId = ((Number) task.get("org_id")).longValue();
            Object tenantIdObj = task.get("tenant_id");
            Long tenantId = tenantIdObj != null ? ((Number) tenantIdObj).longValue() : 1L;
            java.sql.Date planDateObj = (java.sql.Date) task.get("plan_date");
            java.sql.Timestamp endTimeObj = (java.sql.Timestamp) task.get("end_time");
            LocalDateTime completedAt = endTimeObj != null ? endTimeObj.toLocalDateTime() : LocalDateTime.now();
            LocalDate sampleDate = planDateObj != null ? planDateObj.toLocalDate() : completedAt.toLocalDate();
            String mealType = (String) task.get("meal_type");
            if (mealType == null || mealType.isBlank()) {
                mealType = resolveMealType(completedAt);
            }

            SampleRecord record = new SampleRecord();
            record.setTaskId(cookTaskId);
            record.setMenuId(menuId);
            record.setMenuName(menuName);
            record.setSampleDate(sampleDate);
            record.setMealType(mealType);
            record.setStatus(STATUS_PENDING_SAMPLE);
            record.setOrgId(orgId);
            record.setTenantId(tenantId);
            record.setRecordOriginType(ORIGIN_SYSTEM_BACKFILL);

            String datePart = sampleDate.format(DATE_FMT);
            int seq = sampleRecordMapper.nextSeqForDate(sampleDate);
            record.setSampleNo("SP-" + datePart + String.format("%03d", seq));

            sampleRecordMapper.insert(record);
            jdbcTemplate.update(
                    """
                    INSERT INTO sample_operation_log
                    (record_id, action, action_name, operator_id, operator_name, content, terminal)
                    VALUES (?, 'auto_create', '系统自动生成', NULL, NULL, ?, 'system')
                    """,
                    record.getId(),
                    "定时补偿扫描生成待留样任务"
            );
            count++;
            log.info("补偿生成留样任务: cookTaskId={}, sampleNo={}", cookTaskId, record.getSampleNo());
        }

        log.info("===== 补偿扫描留样任务完成，共生成 {} 条 =====", count);
    }

    /**
     * 每5分钟执行：补偿扫描已从 completed 回滚为未完结状态的烹饪任务，
     * 将其关联留样/销样链路统一作废并隔离，兜底覆盖人工纠错、运维修正等非标准入口。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void isolateRolledBackCookTaskSamples() {
        String sql = """
            SELECT sr.id,
                   sr.sample_no,
                   sr.status,
                   sr.task_id,
                   sr.disposal_remark,
                   sr.void_reason,
                   ct.task_no,
                   ct.status AS task_status
            FROM sample_record sr
            INNER JOIN cook_task ct ON ct.id = sr.task_id AND ct.deleted = 0
            WHERE sr.deleted = 0
              AND COALESCE(sr.rollback_isolated, 0) = 0
              AND ct.status IN ('pending', 'in_progress', 'cancelled')
            ORDER BY sr.id ASC
            """;

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql);
        if (records.isEmpty()) {
            return;
        }

        Map<Long, String> taskNoMap = new LinkedHashMap<>();
        Map<Long, String> taskStatusMap = new LinkedHashMap<>();
        Map<Long, List<String>> taskSampleNoMap = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map<String, Object> row : records) {
            Long recordId = ((Number) row.get("id")).longValue();
            SampleRecord record = sampleRecordMapper.selectById(recordId);
            if (record == null) {
                continue;
            }

            String taskNo = (String) row.get("task_no");
            String taskStatus = (String) row.get("task_status");
            String previousSampleStatus = record.getStatus();
            String rollbackReason = buildRollbackIsolationReason(taskNo, taskStatus, "补偿扫描发现烹饪任务已回滚");

            if (!STATUS_VOIDED.equals(record.getStatus())) {
                record.setStatus(STATUS_VOIDED);
                record.setVoidReason(rollbackReason);
            }
            record.setRollbackIsolated(1);
            record.setRollbackIsolatedAt(now);
            record.setRollbackIsolationReason(rollbackReason);
            record.setDisposalRemark(appendRollbackIsolationRemark(record.getDisposalRemark(), previousSampleStatus, taskStatus, rollbackReason));
            sampleRecordMapper.updateById(record);

            jdbcTemplate.update(
                    """
                    INSERT INTO sample_operation_log
                    (record_id, action, action_name, operator_id, operator_name, content, terminal)
                    VALUES (?, 'rollback_void', '回滚联动作废', NULL, '系统补偿', ?, 'system')
                    """,
                    record.getId(),
                    buildRollbackOperationContent(previousSampleStatus, taskStatus, rollbackReason, true)
            );

            auditLogService.log(
                    AuditModule.SAMPLE_RECORD,
                    AuditOperationType.STATUS_CHANGE,
                    record.getId(),
                    record.getSampleNo(),
                    "系统补偿扫描发现关联烹饪任务已回滚，联动作废并隔离留样记录：" + record.getSampleNo()
            );

            if (record.getTaskId() != null) {
                taskNoMap.put(record.getTaskId(), taskNo);
                taskStatusMap.put(record.getTaskId(), taskStatus);
                taskSampleNoMap.computeIfAbsent(record.getTaskId(), key -> new ArrayList<>()).add(record.getSampleNo());
            }
        }

        for (Map.Entry<Long, List<String>> entry : taskSampleNoMap.entrySet()) {
            Long taskId = entry.getKey();
            String taskNo = taskNoMap.get(taskId);
            String taskStatus = taskStatusMap.get(taskId);
            auditLogService.log(
                    AuditModule.COOK_TASK,
                    AuditOperationType.STATUS_CHANGE,
                    taskId,
                    taskNo,
                    "系统补偿扫描发现烹饪任务已回滚为" + resolveTaskStatusLabel(taskStatus)
                            + "，联动作废并隔离留样记录：" + String.join("、", entry.getValue())
            );
        }

        log.info("补偿隔离烹饪任务回滚关联留样记录 {} 条", records.size());
    }

    /**
     * 每小时30分执行：检查并标记超期未销样的记录
     * <p>
     * 超过disposalDueAt仍未销样的记录自动标记为overdue
     */
    @Scheduled(cron = "0 30 * * * ?")
    public void markOverdueRecords() {
        String sql = """
            UPDATE sample_record
            SET status = 'overdue', updated_at = NOW()
            WHERE status IN ('sampled', 'evaluated', 'pending_disposal')
              AND disposal_due_at IS NOT NULL
              AND disposal_due_at < NOW()
              AND deleted = 0
            """;

        int count = jdbcTemplate.update(sql);
        if (count > 0) {
            log.info("标记过期留样记录 {} 条", count);
        }
    }

    private String buildRollbackIsolationReason(String taskNo, String taskStatus, String triggerPrefix) {
        return triggerPrefix
                + "：关联烹饪任务["
                + (taskNo != null ? taskNo : "-")
                + "]当前状态为"
                + resolveTaskStatusLabel(taskStatus)
                + "，系统已联动作废并隔离对应留样/销样历史";
    }

    private String appendRollbackIsolationRemark(String existingRemark,
                                                 String previousSampleStatus,
                                                 String taskStatus,
                                                 String rollbackReason) {
        String linkageRemark = "[烹饪任务回滚] 留样状态"
                + resolveSampleStatusLabel(previousSampleStatus)
                + " -> "
                + resolveSampleStatusLabel(STATUS_VOIDED)
                + "；烹饪任务当前状态："
                + resolveTaskStatusLabel(taskStatus)
                + "；[提醒关停] 已终止留样/销样待办、到期预警、逾期考核与合规统计；[原因] "
                + rollbackReason;
        if (existingRemark == null || existingRemark.isBlank()) {
            return linkageRemark;
        }
        return existingRemark.trim() + "；" + linkageRemark;
    }

    private String buildRollbackOperationContent(String previousSampleStatus,
                                                 String taskStatus,
                                                 String rollbackReason,
                                                 boolean compensation) {
        return (compensation ? "系统补偿" : "系统联动")
                + "将留样记录从"
                + resolveSampleStatusLabel(previousSampleStatus)
                + "回写为"
                + resolveSampleStatusLabel(STATUS_VOIDED)
                + "；关联烹饪任务当前状态："
                + resolveTaskStatusLabel(taskStatus)
                + "；"
                + rollbackReason;
    }

    private String resolveTaskStatusLabel(String status) {
        if ("pending".equals(status)) {
            return "待烹饪";
        }
        if ("in_progress".equals(status)) {
            return "烹饪中";
        }
        if ("completed".equals(status)) {
            return "已完成";
        }
        if ("cancelled".equals(status)) {
            return "已取消";
        }
        return status != null ? status : "-";
    }

    private String resolveSampleStatusLabel(String status) {
        if ("pending_sample".equals(status)) {
            return "待留样";
        }
        if ("sampled".equals(status)) {
            return "已留样";
        }
        if ("evaluated".equals(status)) {
            return "已评估";
        }
        if ("pending_disposal".equals(status)) {
            return "待销样";
        }
        if ("overdue".equals(status)) {
            return "超期未销";
        }
        if ("disposed".equals(status)) {
            return "已销样";
        }
        if ("archived".equals(status)) {
            return "已归档";
        }
        if ("voided".equals(status)) {
            return "已作废";
        }
        return status != null ? status : "-";
    }

    private String resolveMealType(LocalDateTime completedAt) {
        int hour = completedAt != null ? completedAt.getHour() : LocalDateTime.now().getHour();
        if (hour < 10) {
            return "breakfast";
        }
        if (hour < 15) {
            return "lunch";
        }
        if (hour < 21) {
            return "dinner";
        }
        return "supper";
    }
}
