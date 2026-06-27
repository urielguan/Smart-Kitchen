package com.xykj.cook.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.cook.dto.*;
import com.xykj.cook.entity.CookRecord;
import com.xykj.cook.entity.CookTask;
import com.xykj.cook.entity.CookTemperatureRecord;
import com.xykj.cook.mapper.CookRecordMapper;
import com.xykj.cook.mapper.CookTaskMapper;
import com.xykj.cook.mapper.CookTemperatureRecordMapper;
import com.xykj.cook.service.CookTaskService;
import com.xykj.cook.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 烹饪任务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CookTaskServiceImpl implements CookTaskService {

    private static final DateTimeFormatter TASK_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_IN_PROGRESS = "in_progress";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String STATUS_ARCHIVED = "archived";
    private static final String MATERIAL_PREPARED = "prepared";
    private static final String COLLECTION_STATUS_NORMAL = "normal";
    private static final String COLLECTION_STATUS_INTERRUPTED = "interrupted";
    private static final String SYNC_STATUS_NORMAL = "normal";
    private static final String SYNC_STATUS_FAILED = "sync_failed";
    private static final String COMPENSATION_STATUS_NONE = "none";
    private static final String COMPENSATION_STATUS_PENDING = "pending";
    private static final String SAMPLE_STATUS_VOIDED = "voided";
    private static final String SAMPLE_ORIGIN_AUTO = "auto";
    private static final String[] EXPORT_HEADERS = {
        "任务编号", "实施日期", "餐次", "菜谱名称", "状态",
        "计划份数", "实际份数", "烹饪人", "标准时长(分钟)", "实际时长(分钟)",
        "目标温度(℃)", "当前温度(℃)", "温度异常", "AI违规次数", "质量评分",
        "开始时间", "完成时间", "食材", "备注"
    };

    private static final int[] EXPORT_COLUMN_WIDTHS = {
        20, 12, 10, 24, 12,
        10, 10, 14, 14, 14,
        14, 14, 10, 10, 10,
        20, 20, 30, 24
    };

    private static final DateTimeFormatter EXPORT_DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, String> EXPORT_STATUS_LABEL_MAP = new HashMap<>(Map.of(
        "pending", "待开始",
        "in_progress", "进行中",
        "completed", "已完成",
        "cancelled", "已取消"
    ));
    static { EXPORT_STATUS_LABEL_MAP.put("archived", "已归档"); }

    private static final Map<String, String> EXPORT_MEAL_TYPE_LABEL_MAP = Map.of(
        "breakfast", "早餐",
        "lunch", "午餐",
        "dinner", "晚餐",
        "supper", "夜宵"
    );

    private final CookTaskMapper cookTaskMapper;
    private final CookTemperatureRecordMapper temperatureRecordMapper;
    private final CookRecordMapper cookRecordMapper;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public CookDashboardVO getDashboard(CookTaskQueryDTO query) {
        // mealType 过滤通过预查 recipe_plan 得到 mealTypePlanIds
        Set<Long> mealTypePlanIds = resolveMealTypePlanIds(query);

        if (mealTypePlanIds != null && mealTypePlanIds.isEmpty()) {
            return CookDashboardVO.builder()
                    .totalDishes(0L).pendingCount(0L).inProgressCount(0L)
                    .completedCount(0L).abnormalTemperatureCount(0L)
                    .durationAbnormalCount(0L).completionRate(BigDecimal.ZERO)
                    .build();
        }

        LambdaQueryWrapper<CookTask> wrapper = buildQueryWrapper(query, mealTypePlanIds);
        List<CookTask> tasks = cookTaskMapper.selectList(wrapper);

        long total = tasks.size();
        long pending = tasks.stream().filter(t -> STATUS_PENDING.equals(t.getStatus())).count();
        long inProgress = tasks.stream().filter(t -> STATUS_IN_PROGRESS.equals(t.getStatus())).count();
        long completed = tasks.stream().filter(t -> STATUS_COMPLETED.equals(t.getStatus())).count();
        long abnormal = tasks.stream().filter(this::hasTemperatureAbnormal).count();

        // 计算超时任务数（实际时长 > 标准时长）
        Map<Long, Integer> stdDurationMap = batchQueryStandardDuration(tasks);
        long durationAbnormal = tasks.stream()
                .filter(t -> t.getCookingDuration() != null)
                .filter(t -> {
                    Integer std = stdDurationMap.get(t.getMenuId());
                    return std != null && t.getCookingDuration() > std;
                })
                .count();

        BigDecimal completionRate = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        return CookDashboardVO.builder()
                .totalDishes(total)
                .pendingCount(pending)
                .inProgressCount(inProgress)
                .completedCount(completed)
                .abnormalTemperatureCount(abnormal)
                .durationAbnormalCount(durationAbnormal)
                .completionRate(completionRate)
                .build();
    }

    @Override
    public PageResult<CookTaskVO> getTaskPage(CookTaskQueryDTO query) {
        // mealType 过滤通过预查 recipe_plan 得到 mealTypePlanIds
        Set<Long> mealTypePlanIds = resolveMealTypePlanIds(query);

        LambdaQueryWrapper<CookTask> wrapper = buildQueryWrapper(query, mealTypePlanIds);
        wrapper.orderByDesc(CookTask::getCreatedAt);

        List<CookTask> allTasks = cookTaskMapper.selectList(wrapper);
        List<CookTaskVO> voList = allTasks.stream()
                .map(this::buildTaskVO)
                .collect(Collectors.toList());

        int pageNum = query.getPageNum() != null ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 20;
        int fromIndex = Math.min((pageNum - 1) * pageSize, voList.size());
        int toIndex = Math.min(fromIndex + pageSize, voList.size());
        List<CookTaskVO> pageList = voList.subList(fromIndex, toIndex);

        return PageResult.of(pageList, (long) pageNum, (long) pageSize, (long) voList.size());
    }

    @Override
    public CookTaskDetailVO getTaskDetail(Long id) {
        CookTask task = getRequiredTask(id);
        return buildTaskDetailVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.COOK_TASK,
            operationType = AuditOperationType.STATUS_CHANGE,
            targetId = "#id",
            targetNo = "#entity.taskNo",
            desc = "'开始烹饪任务：' + #entity.taskNo",
            mapper = CookTaskMapper.class
    )
    public CookTaskVO startTask(Long id, CookTaskStartDTO dto) {
        CookTask task = getRequiredTask(id);

        if (!STATUS_PENDING.equals(task.getStatus())) {
            throw BizException.validationFailed("当前任务状态不允许开始烹饪");
        }

        validateTaskCanStart(task, dto != null ? dto.getDeviceId() : null);

        LocalDateTime now = LocalDateTime.now();
        task.setStatus(STATUS_IN_PROGRESS);
        task.setStartTime(now);
        task.setCollectionStatus(COLLECTION_STATUS_NORMAL);
        task.setSyncStatus(SYNC_STATUS_NORMAL);
        task.setSyncRetryCount(0);
        task.setSyncRetryLimitReached(0);
        task.setLatestSyncFailureReason(null);
        task.setCompensationStatus(COMPENSATION_STATUS_NONE);

        // 记录启动人
        task.setInitiatorId(UserContext.getUserId());
        task.setInitiatorName(UserContext.getRealName());

        if (dto != null) {
            if (dto.getChefId() != null) {
                task.setAssignedChefId(dto.getChefId());
            }
            if (dto.getChefName() != null && !dto.getChefName().isBlank()) {
                task.setAssignedChefName(dto.getChefName());
            }
            if (dto.getRemark() != null && !dto.getRemark().isBlank()) {
                task.setRemark(dto.getRemark());
            }
            if (dto.getDeviceId() != null) {
                applyDeviceInfo(task, dto.getDeviceId());
            }
        }

        cookTaskMapper.updateById(task);

        // 记录操作日志
        saveCookRecord(task.getId(), "start", "开始烹饪", task.getAssignedChefId(), task.getAssignedChefName());

        return buildTaskVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.COOK_TASK,
            operationType = AuditOperationType.STATUS_CHANGE,
            targetId = "#id",
            targetNo = "#entity.taskNo",
            desc = "'完成烹饪任务：' + #entity.taskNo",
            mapper = CookTaskMapper.class
    )
    public CookTaskVO completeTask(Long id, CookTaskCompleteDTO dto) {
        CookTask task = getRequiredTask(id);

        if (!STATUS_PENDING.equals(task.getStatus()) && !STATUS_IN_PROGRESS.equals(task.getStatus())) {
            throw BizException.validationFailed("当前任务状态不允许完成烹饪");
        }

        LocalDateTime now = LocalDateTime.now();

        // 如果任务还是pending状态，自动设置开始时间
        if (STATUS_PENDING.equals(task.getStatus())) {
            task.setStartTime(now);
            task.setAssignedChefName("系统");
        }

        task.setStatus(STATUS_COMPLETED);
        task.setEndTime(now);
        task.setCollectionStatus(resolveCollectionStatusOnComplete(task, now));

        // 记录完成人
        task.setCompleterId(UserContext.getUserId());
        task.setCompleterName(UserContext.getRealName());

        if (task.getStartTime() != null) {
            task.setCookingDuration((int) Duration.between(task.getStartTime(), now).toMinutes());
        }

        if (dto != null) {
            if (dto.getActualQty() != null) {
                task.setActualQty(dto.getActualQty());
            } else {
                task.setActualQty(task.getPlannedQty());
            }
            if (dto.getQualityScore() != null) {
                task.setQualityScore(dto.getQualityScore());
            }
            if (dto.getRemark() != null && !dto.getRemark().isBlank()) {
                task.setRemark(dto.getRemark());
            }
        } else {
            task.setActualQty(task.getPlannedQty());
        }

        // 主状态先落库，后续补偿失败不能把完成态回滚掉
        cookTaskMapper.updateById(task);

        // 食安判定：检查温度记录中是否存在任何异常记录
        List<CookTemperatureRecord> allTempRecords = temperatureRecordMapper.selectByTaskId(task.getId());
        boolean hasAbnormalTemp = allTempRecords.stream()
                .anyMatch(r -> Boolean.TRUE.equals(r.getAbnormal()));
        task.setFoodSafetyPass(!hasAbnormalTemp);
        cookTaskMapper.updateById(task);

        try {
            autoCreatePendingSampleRecord(task);
            clearCompletionExceptionState(task.getId());
            task.setSyncStatus(SYNC_STATUS_NORMAL);
            task.setLatestSyncFailureReason(null);
            task.setCompensationStatus(COMPENSATION_STATUS_NONE);
        } catch (Exception e) {
            handleCompletionLinkageFailure(task, e);
            task.setSyncStatus(SYNC_STATUS_FAILED);
            task.setLatestSyncFailureReason(buildCompletionLinkageFailureReason(e));
            task.setCompensationStatus(COMPENSATION_STATUS_PENDING);
        }

        // 记录操作日志
        saveCookRecord(task.getId(), "complete", "完成烹饪", task.getAssignedChefId(), task.getAssignedChefName());

        return buildTaskVO(getRequiredTask(task.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.COOK_TASK,
            operationType = AuditOperationType.STATUS_CHANGE,
            targetId = "#id",
            targetNo = "#entity.taskNo",
            desc = "'取消烹饪任务：' + #entity.taskNo + '，原因：' + (#dto != null ? #dto.reason : '')",
            mapper = CookTaskMapper.class
    )
    public CookTaskVO cancelTask(Long id, CookTaskCancelDTO dto) {
        CookTask task = getRequiredTask(id);

        if (STATUS_COMPLETED.equals(task.getStatus())) {
            throw new RuntimeException("已完成的任务不能取消");
        }

        task.setStatus(STATUS_CANCELLED);
        if (dto != null && dto.getReason() != null && !dto.getReason().isBlank()) {
            task.setRemark(dto.getReason());
        }

        cookTaskMapper.updateById(task);

        // 记录操作日志
        String reason = dto != null ? dto.getReason() : null;
        saveCookRecord(task.getId(), "cancel", "取消烹饪：" + reason, null, null);

        return buildTaskVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.COOK_TASK,
            operationType = AuditOperationType.STATUS_CHANGE,
            targetId = "#id",
            targetNo = "#entity.taskNo",
            desc = "'归档烹饪任务：' + #entity.taskNo",
            mapper = CookTaskMapper.class
    )
    public CookTaskVO archiveTask(Long id, CookTaskArchiveDTO dto) {
        CookTask task = getRequiredTask(id);

        if (!STATUS_COMPLETED.equals(task.getStatus())) {
            throw new RuntimeException("只有已完成的任务才能归档");
        }

        task.setStatus(STATUS_ARCHIVED);
        LocalDateTime archivedAt = LocalDateTime.now();
        task.setArchivedAt(archivedAt);

        if (dto != null) {
            if (dto.getHandoffStatus() != null) {
                task.setHandoffStatus(dto.getHandoffStatus());
            }
            if (dto.getHandoffRemark() != null) {
                task.setHandoffRemark(dto.getHandoffRemark());
            }
        }

        cookTaskMapper.updateById(task);

        // archived_at 字段尚未在数据库中（迁移31待执行），通过 JdbcTemplate 直接写入
        try {
            jdbcTemplate.update(
                "UPDATE cook_task SET archived_at = ? WHERE id = ? AND deleted = 0",
                archivedAt, task.getId()
            );
        } catch (Exception e) {
            log.warn("写入archived_at失败（列可能尚未创建）: {}", e.getMessage());
        }

        saveCookRecord(task.getId(), "archive", "归档任务", UserContext.getUserId(), UserContext.getRealName());

        return buildTaskVO(task);
    }

    @Override
    @AuditLog(module = AuditModule.COOK_TASK, operationType = AuditOperationType.EXPORT, desc = "导出烹饪记录Excel")
    public void exportTasks(CookTaskQueryDTO query, HttpServletResponse response) {
        // mealType 过滤通过预查 recipe_plan 得到 mealTypePlanIds
        Set<Long> mealTypePlanIds = resolveMealTypePlanIds(query);

        LambdaQueryWrapper<CookTask> wrapper = buildQueryWrapper(query, mealTypePlanIds);
        wrapper.orderByDesc(CookTask::getCreatedAt);

        List<CookTask> tasks = cookTaskMapper.selectList(wrapper);
        List<CookTaskVO> voList = tasks.stream()
                .map(this::buildTaskVO)
                .collect(Collectors.toList());

        // 设置响应头
        String filename = "烹饪记录_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("烹饪记录");

            // 创建样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle contentStyle = createContentStyle(workbook);

            // 创建表头行
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, EXPORT_COLUMN_WIDTHS[i] * 256);
            }

            // 创建数据行
            for (int rowIndex = 0; rowIndex < voList.size(); rowIndex++) {
                CookTaskVO vo = voList.get(rowIndex);
                Row dataRow = sheet.createRow(rowIndex + 1);

                setCellValueWithStyle(sheet, rowIndex + 1, 0, vo.getTaskNo() != null ? vo.getTaskNo() : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 1,
                    vo.getPlanDate() != null ? vo.getPlanDate().toString() : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 2, resolveExportMealTypeLabel(vo.getMealType()), contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 3, vo.getMenuName() != null ? vo.getMenuName() : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 4, resolveExportStatusLabel(vo.getStatus()), contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 5,
                    vo.getPlannedQty() != null ? String.valueOf(vo.getPlannedQty()) : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 6,
                    vo.getActualQty() != null ? String.valueOf(vo.getActualQty()) : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 7,
                    vo.getAssignedChefName() != null ? vo.getAssignedChefName() : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 8,
                    vo.getStandardDuration() != null ? String.valueOf(vo.getStandardDuration()) : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 9,
                    vo.getActualDuration() != null ? String.valueOf(vo.getActualDuration()) : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 10,
                    vo.getTargetTemp() != null ? String.valueOf(vo.getTargetTemp()) : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 11,
                    vo.getCurrentTemp() != null ? String.valueOf(vo.getCurrentTemp()) : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 12,
                    Boolean.TRUE.equals(vo.getTemperatureAbnormal()) ? "是" : "否", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 13,
                    vo.getAiViolationCount() != null ? String.valueOf(vo.getAiViolationCount()) : "0", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 14,
                    vo.getQualityScore() != null ? String.valueOf(vo.getQualityScore()) : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 15,
                    vo.getStartTime() != null ? vo.getStartTime().format(EXPORT_DATETIME_FMT) : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 16,
                    vo.getEndTime() != null ? vo.getEndTime().format(EXPORT_DATETIME_FMT) : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 17,
                    vo.getIngredients() != null ? String.join(",", vo.getIngredients()) : "", contentStyle);
                setCellValueWithStyle(sheet, rowIndex + 1, 18,
                    vo.getRemark() != null ? vo.getRemark() : "", contentStyle);
            }

            workbook.write(response.getOutputStream());
        } catch (Exception e) {
            log.error("导出烹饪记录Excel失败", e);
            throw new RuntimeException("导出烹饪记录Excel失败: " + e.getMessage());
        }
    }

    @Override
    public List<CookTemperaturePointVO> getTemperatureRecords(Long taskId) {
        List<CookTemperatureRecord> records = temperatureRecordMapper.selectByTaskId(taskId);
        return records.stream().map(record -> {
            CookTemperaturePointVO vo = new CookTemperaturePointVO();
            vo.setId(record.getId());
            vo.setRecordTime(record.getRecordTime());
            vo.setTemperature(record.getTemperature());
            vo.setAbnormal(record.getAbnormal());
            vo.setRemark(record.getRemark());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<CookTemperaturePointVO> getTemperatureRecordsSince(Long taskId, Long sinceId) {
        List<CookTemperatureRecord> records = temperatureRecordMapper.selectByTaskIdSince(taskId, sinceId);
        return records.stream().map(record -> {
            CookTemperaturePointVO vo = new CookTemperaturePointVO();
            vo.setId(record.getId());
            vo.setRecordTime(record.getRecordTime());
            vo.setTemperature(record.getTemperature());
            vo.setAbnormal(record.getAbnormal());
            vo.setRemark(record.getRemark());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportTemperature(TemperatureRecordDTO dto) {
        CookTask task = getRequiredTask(dto.getTaskId());

        CookTemperatureRecord record = new CookTemperatureRecord();
        record.setTaskId(dto.getTaskId());
        record.setRecordTime(LocalDateTime.now());
        record.setTemperature(dto.getTemperature());
        record.setAbnormal(dto.getAbnormal() != null ? dto.getAbnormal() : false);
        record.setRemark(dto.getRemark());
        record.setOrgId(task.getOrgId());
        record.setTenantId(task.getTenantId());

        temperatureRecordMapper.insert(record);

        task.setLastTemperatureRecordAt(record.getRecordTime());
        task.setCollectionStatus(COLLECTION_STATUS_NORMAL);
        cookTaskMapper.updateById(task);

        // 检查温度异常
        if (Boolean.TRUE.equals(record.getAbnormal())) {
            log.warn("烹饪任务[{}]温度异常: {}°C", task.getTaskNo(), dto.getTemperature());
        }
    }

    @Override
    public List<CookAIMonitorVO> getAiMonitorRecords(Long taskId) {
        CookTask task = getRequiredTask(taskId);
        List<CookAIMonitorVO> records = parseAiMonitorRecords(task.getRemark());
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setAlertIndex(i);
        }
        return records;
    }

    @Override
    public void acknowledgeAlert(Long taskId, Integer alertIndex, String operatorName) {
        CookTask task = getRequiredTask(taskId);
        List<CookAIMonitorVO> records = parseAiMonitorRecords(task.getRemark());
        if (alertIndex < 0 || alertIndex >= records.size()) {
            throw new RuntimeException("无效的预警记录索引");
        }
        CookAIMonitorVO record = records.get(alertIndex);
        if (Boolean.TRUE.equals(record.getAcknowledged())) {
            return;
        }
        record.setAcknowledged(true);
        record.setAcknowledgedBy(operatorName);
        record.setAcknowledgedAt(java.time.LocalDateTime.now().toString());
        try {
            String updatedJson = objectMapper.writeValueAsString(records);
            jdbcTemplate.update("UPDATE cook_task SET remark = ? WHERE id = ?", updatedJson, taskId);
        } catch (Exception e) {
            throw new RuntimeException("更新预警确认状态失败", e);
        }
    }

    @Override
    public List<CookTimelineEventVO> getTaskTimeline(Long taskId) {
        getRequiredTask(taskId);

        List<CookTimelineEventVO> timeline = new ArrayList<>();

        // 1. Temperature records
        List<CookTemperatureRecord> tempRecords = temperatureRecordMapper.selectByTaskId(taskId);
        for (CookTemperatureRecord rec : tempRecords) {
            CookTimelineEventVO event = new CookTimelineEventVO();
            event.setEventType("temperature");
            event.setEventTime(rec.getRecordTime());
            event.setTitle("温度采样: " + rec.getTemperature() + "°C");
            event.setDetail(rec.getRemark());
            event.setLevel(Boolean.TRUE.equals(rec.getAbnormal()) ? "warning" : "normal");
            event.setTemperature(rec.getTemperature());
            event.setAbnormal(rec.getAbnormal());
            timeline.add(event);
        }

        // 2. AI alerts
        List<CookAIMonitorVO> aiRecords = getAiMonitorRecords(taskId);
        for (CookAIMonitorVO ai : aiRecords) {
            CookTimelineEventVO event = new CookTimelineEventVO();
            event.setEventType("ai_alert");
            if (ai.getSnapshotTime() != null) {
                try {
                    event.setEventTime(LocalDateTime.parse(ai.getSnapshotTime().replace(" ", "T")));
                } catch (Exception ignored) {
                    // Parse failure — leave null
                }
            }
            event.setTitle(ai.getViolationName() != null ? ai.getViolationName() : "AI预警");
            event.setDetail(ai.getDescription());
            event.setLevel(ai.getLevel());
            event.setViolationType(ai.getViolationType());
            timeline.add(event);
        }

        // 3. Status change records
        List<CookRecord> records = cookRecordMapper.selectByTaskId(taskId);
        for (CookRecord rec : records) {
            CookTimelineEventVO event = new CookTimelineEventVO();
            event.setEventType("status_change");
            event.setEventTime(rec.getRecordTime());
            event.setTitle(rec.getContent() != null ? rec.getContent() : "状态变更");
            event.setDetail(rec.getContent());
            event.setLevel("normal");
            event.setOperatorName(rec.getOperatorName());
            timeline.add(event);
        }

        // Sort by eventTime descending (newest first)
        timeline.sort((a, b) -> {
            if (a.getEventTime() == null && b.getEventTime() == null) return 0;
            if (a.getEventTime() == null) return 1;
            if (b.getEventTime() == null) return -1;
            return b.getEventTime().compareTo(a.getEventTime());
        });

        return timeline;
    }

    @Override
    public List<CookTask> getTasksByPlanId(Long planId) {
        return cookTaskMapper.selectByPlanId(planId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CookTask> generateTasksFromPlan(Long planId) {
        log.info("为计划[{}]生成烹饪任务", planId);

        // 检查是否已有任务
        List<CookTask> existingTasks = cookTaskMapper.selectByPlanId(planId);
        if (!existingTasks.isEmpty()) {
            log.info("计划[{}]已有{}个烹饪任务，跳过生成", planId, existingTasks.size());
            return existingTasks;
        }

        // 生成模拟任务（实际应该从recipe-service获取计划详情）
        CookTask task = new CookTask();
        task.setTaskNo(generateTaskNo());
        task.setPlanId(planId);
        task.setMenuId(1L);
        task.setMenuName("示例菜品");
        task.setPlannedQty(50);
        task.setActualQty(0);
        task.setStatus(STATUS_PENDING);
        task.setOrgId(UserContext.getOrgId() != null ? UserContext.getOrgId() : 1L);
        task.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L);
        task.setDeleted(0);

        cookTaskMapper.insert(task);
        log.info("生成烹饪任务[{}]", task.getTaskNo());

        return List.of(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTasksByPlanId(Long planId) {
        log.info("取消计划[{}]的所有烹饪任务", planId);

        List<CookTask> tasks = cookTaskMapper.selectByPlanId(planId);
        for (CookTask task : tasks) {
            if (!STATUS_COMPLETED.equals(task.getStatus()) && !STATUS_CANCELLED.equals(task.getStatus())) {
                task.setStatus(STATUS_CANCELLED);
                cookTaskMapper.updateById(task);
                log.info("取消烹饪任务[{}]", task.getTaskNo());

                // 级联作废该任务的留样记录
                try {
                    int voided = jdbcTemplate.update(
                        "UPDATE sample_record SET status = 'voided', supplement_remark = CONCAT(COALESCE(supplement_remark, ''), ?) " +
                        "WHERE task_id = ? AND status NOT IN ('voided', 'disposed', 'archived') AND deleted = 0",
                        " [系统级联作废: 烹饪任务取消]", task.getId());
                    if (voided > 0) {
                        log.info("烹饪任务[{}]取消，级联作废{}条留样记录", task.getTaskNo(), voided);
                    }
                } catch (Exception e) {
                    log.warn("级联作废烹饪任务[{}]的留样记录失败: {}", task.getTaskNo(), e.getMessage());
                }
            }
        }

        auditLogService.log(AuditModule.COOK_TASK, AuditOperationType.STATUS_CHANGE,
                null, null, "取消计划[" + planId + "]的所有烹饪任务，共" + tasks.size() + "条",
                null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CookTaskVO confirmTempAbnormal(Long id) {
        CookTask task = getRequiredTask(id);

        // 验证当前任务确实存在温度异常
        List<CookTemperatureRecord> records = temperatureRecordMapper.selectByTaskId(id);
        boolean hasAbnormal = records.stream()
                .anyMatch(r -> Boolean.TRUE.equals(r.getAbnormal()));
        if (!hasAbnormal) {
            throw new RuntimeException("当前任务无温度异常记录，无需确认");
        }

        // 幂等：已确认则直接返回
        if (Integer.valueOf(1).equals(task.getTempAbnormalConfirmed())) {
            return buildTaskVO(task);
        }

        task.setTempAbnormalConfirmed(1);
        task.setTempAbnormalConfirmedBy(UserContext.getUserId());
        task.setTempAbnormalConfirmedAt(LocalDateTime.now());
        cookTaskMapper.updateById(task);

        saveCookRecord(task.getId(), "confirm_temp_abnormal", "主管确认温度异常",
                UserContext.getUserId(), UserContext.getRealName());

        return buildTaskVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.COOK_TASK,
            operationType = AuditOperationType.DISPATCH,
            targetId = "#id",
            targetNo = "#entity.taskNo",
            desc = "'主管分派厨师：' + #entity.taskNo",
            mapper = CookTaskMapper.class
    )
    public CookTaskVO assignChef(Long id, CookTaskAssignDTO dto) {
        CookTask task = getRequiredTask(id);

        // 仅 pending / in_progress 可分派
        String status = task.getStatus();
        if (!STATUS_PENDING.equals(status) && !STATUS_IN_PROGRESS.equals(status)) {
            throw new RuntimeException("当前任务状态不允许分派厨师（仅待烹饪和烹饪中可操作）");
        }

        if (dto == null) {
            dto = new CookTaskAssignDTO();
        }

        if (dto.getChefId() == null) {
            throw new RuntimeException("请指定厨师");
        }

        // 校验员工存在且在职
        try {
            Map<String, Object> employee = jdbcTemplate.queryForMap(
                    "SELECT id, real_name, status FROM sys_employee WHERE id = ? AND deleted = 0",
                    dto.getChefId()
            );
            String empStatus = (String) employee.get("status");
            if (!"active".equals(empStatus)) {
                throw new RuntimeException("该员工已离职，无法分派");
            }
            // 以数据库实际姓名为准
            dto.setChefName((String) employee.get("real_name"));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("已离职") || e.getMessage().contains("请指定")) {
                throw e;
            }
            throw new RuntimeException("指定的厨师不存在");
        }

        String oldChef = task.getAssignedChefName();
        task.setAssignedChefId(dto.getChefId());
        task.setAssignedChefName(dto.getChefName());
        cookTaskMapper.updateById(task);

        // 操作日志
        String content = oldChef != null
                ? "主管更换厨师：" + oldChef + " → " + dto.getChefName()
                : "主管分派厨师：" + dto.getChefName();
        saveCookRecord(task.getId(), "assign_chef", content, UserContext.getUserId(), UserContext.getRealName());

        return buildTaskVO(task);
    }

    // ==================== 私有方法 ====================

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createContentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void setCellValueWithStyle(Sheet sheet, int rowIndex, int colIndex, String value, CellStyle style) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private String resolveExportStatusLabel(String status) {
        if (status == null) return "";
        return EXPORT_STATUS_LABEL_MAP.getOrDefault(status, status);
    }

    private String resolveExportMealTypeLabel(String mealType) {
        if (mealType == null) return "";
        return EXPORT_MEAL_TYPE_LABEL_MAP.getOrDefault(mealType, mealType);
    }

    private LambdaQueryWrapper<CookTask> buildQueryWrapper(CookTaskQueryDTO query, Set<Long> mealTypePlanIds) {
        LambdaQueryWrapper<CookTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(CookTask::getStatus, STATUS_CANCELLED);

        if (query == null) {
            return wrapper;
        }

        // 日期过滤：优先按 taskDate 直接过滤，fallback 到 planDate
        LocalDate dateFilter = query.getTaskDate() != null ? query.getTaskDate() : query.getPlanDate();
        if (dateFilter != null) {
            wrapper.eq(CookTask::getTaskDate, dateFilter);
        }

        // 数据权限过滤：orgId范围内 OR 分配给当前用户的任务（厨师应始终能看到自己的任务）
        Long currentUserId = UserContext.getUserId();
        if (query.getOrgId() != null) {
            if (currentUserId != null) {
                wrapper.and(w -> w.eq(CookTask::getOrgId, query.getOrgId())
                        .or(orW -> orW.eq(CookTask::getAssignedChefId, currentUserId)));
            } else {
                wrapper.eq(CookTask::getOrgId, query.getOrgId());
            }
        } else if (query.getOrgIds() != null && !query.getOrgIds().isEmpty()) {
            if (currentUserId != null) {
                wrapper.and(w -> w.in(CookTask::getOrgId, query.getOrgIds())
                        .or(orW -> orW.eq(CookTask::getAssignedChefId, currentUserId)));
            } else {
                wrapper.in(CookTask::getOrgId, query.getOrgIds());
            }
        }
        wrapper.eq(query.getStatus() != null && !query.getStatus().isBlank(), CookTask::getStatus, query.getStatus())
                .like(query.getTaskNo() != null && !query.getTaskNo().isBlank(), CookTask::getTaskNo, query.getTaskNo())
                .like(query.getChefName() != null && !query.getChefName().isBlank(), CookTask::getAssignedChefName, query.getChefName())
                .like(query.getKeyword() != null && !query.getKeyword().isBlank(), CookTask::getMenuName, query.getKeyword())
                .like(query.getDeviceLocation() != null && !query.getDeviceLocation().isBlank(), CookTask::getDeviceLocation, query.getDeviceLocation());

        // P1-9: 预警级别筛选
        if (query.getAlertLevel() != null && !query.getAlertLevel().isBlank()) {
            if ("any".equals(query.getAlertLevel())) {
                wrapper.gt(CookTask::getAiViolationCount, 0);
            } else {
                wrapper.apply("remark LIKE {0}", "%\"level\":\"" + query.getAlertLevel() + "\"%");
            }
        }

        // mealType 通过预查 recipe_plan 得到 mealTypePlanIds
        if (mealTypePlanIds != null && !mealTypePlanIds.isEmpty()) {
            wrapper.in(CookTask::getPlanId, mealTypePlanIds);
        } else if (mealTypePlanIds != null && mealTypePlanIds.isEmpty()) {
            // mealType 过滤无匹配结果
            wrapper.isNull(CookTask::getId);
        }

        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            // DataScope不允许任何org，但仍应能看到分配给当前用户的任务
            if (currentUserId != null) {
                wrapper.eq(CookTask::getAssignedChefId, currentUserId);
            } else {
                wrapper.isNull(CookTask::getId);
            }
        }

        return wrapper;
    }

    /**
     * 解析 mealType 过滤条件，返回匹配的 recipe_plan ID 集合
     * 日期过滤已改为直接按 cook_task.task_date 查询，不再通过 recipe_plan 间接匹配
     * 返回 null 表示无需过滤，返回空集合表示无匹配结果
     */
    private Set<Long> resolveMealTypePlanIds(CookTaskQueryDTO query) {
        if (query == null) return null;
        boolean hasMealType = query.getMealType() != null && !query.getMealType().isBlank();
        if (!hasMealType) return null;

        try {
            StringBuilder sql = new StringBuilder("SELECT id FROM recipe_plan WHERE deleted = 0");
            List<Object> params = new ArrayList<>();
            sql.append(" AND meal_type = ?");
            params.add(query.getMealType());
            List<Long> ids = jdbcTemplate.queryForList(sql.toString(), params.toArray(), Long.class);
            return new HashSet<>(ids);
        } catch (Exception e) {
            log.warn("查询recipe_plan过滤失败: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 批量查询标准烹饪时长（recipe.target_cook_time），用于计算超时任务数
     */
    private Map<Long, Integer> batchQueryStandardDuration(List<CookTask> tasks) {
        if (tasks.isEmpty()) return Collections.emptyMap();
        List<Long> menuIds = tasks.stream()
                .map(CookTask::getMenuId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (menuIds.isEmpty()) return Collections.emptyMap();

        try {
            String placeholders = menuIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT id, target_cook_time FROM recipe WHERE id IN (" + placeholders + ") AND deleted = 0";
            Map<Long, Integer> result = new HashMap<>();
            jdbcTemplate.query(sql, menuIds.toArray(), rs -> {
                result.put(rs.getLong("id"), rs.getInt("target_cook_time"));
            });
            return result;
        } catch (Exception e) {
            log.warn("批量查询标准烹饪时长失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private CookTask getRequiredTask(Long id) {
        CookTask task = cookTaskMapper.selectById(id);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw BizException.notFound("烹饪任务不存在");
        }
        return task;
    }

    private void validateTaskCanStart(CookTask task, Long overrideDeviceId) {
        if (task.getAllowStartTime() != null && task.getAllowEndTime() != null) {
            LocalTime now = LocalTime.now();
            if (now.isBefore(task.getAllowStartTime()) || now.isAfter(task.getAllowEndTime())) {
                throw BizException.validationFailed(
                        "当前不在允许烹饪时间范围内（" + task.getAllowStartTime() + " - " + task.getAllowEndTime() + "）");
            }
        }

        if (task.getAssignedChefId() != null) {
            Long currentUserId = UserContext.getUserId();
            if (!task.getAssignedChefId().equals(currentUserId)) {
                throw BizException.forbidden("该任务已指定给 " + task.getAssignedChefName() + "，您无权操作");
            }
        }

        Long deviceId = overrideDeviceId != null ? overrideDeviceId : task.getDeviceId();
        if (deviceId != null) {
            assertDeviceReady(deviceId);
        }
    }

    private void assertDeviceReady(Long deviceId) {
        try {
            Map<String, Object> deviceInfo = jdbcTemplate.queryForMap(
                    "SELECT id, device_name, location_desc, status, online_status FROM device_info WHERE id = ? AND deleted = 0",
                    deviceId
            );
            String status = (String) deviceInfo.get("status");
            if (!"active".equals(status)) {
                throw BizException.validationFailed("当前烹饪设备已停用或不可用，无法开始烹饪");
            }
            String onlineStatus = (String) deviceInfo.get("online_status");
            if (!"online".equals(onlineStatus)) {
                throw BizException.validationFailed("当前烹饪设备离线，无法开始烹饪");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.validationFailed("当前烹饪设备不存在，无法开始烹饪");
        }
    }

    private void applyDeviceInfo(CookTask task, Long deviceId) {
        task.setDeviceId(deviceId);
        try {
            Map<String, Object> deviceInfo = jdbcTemplate.queryForMap(
                    "SELECT device_name, location_desc FROM device_info WHERE id = ? AND deleted = 0",
                    deviceId);
            task.setDeviceName((String) deviceInfo.get("device_name"));
            task.setDeviceLocation((String) deviceInfo.get("location_desc"));
        } catch (Exception e) {
            log.warn("查询设备信息失败, deviceId={}: {}", deviceId, e.getMessage());
        }
    }

    private String resolveCollectionStatusOnComplete(CookTask task, LocalDateTime completedAt) {
        LocalDateTime lastRecordAt = task.getLastTemperatureRecordAt();
        if (lastRecordAt == null) {
            return COLLECTION_STATUS_INTERRUPTED;
        }
        long secondsSinceLastRecord = Duration.between(lastRecordAt, completedAt).getSeconds();
        return secondsSinceLastRecord > 45 ? COLLECTION_STATUS_INTERRUPTED : COLLECTION_STATUS_NORMAL;
    }

    private void handleCompletionLinkageFailure(CookTask task, Exception e) {
        String failureReason = buildCompletionLinkageFailureReason(e);
        jdbcTemplate.update(
                "UPDATE cook_task SET sync_status = ?, latest_sync_failure_reason = ?, compensation_status = ? WHERE id = ? AND deleted = 0",
                SYNC_STATUS_FAILED,
                failureReason,
                COMPENSATION_STATUS_PENDING,
                task.getId()
        );
        saveCookRecord(task.getId(), "completion_linkage_failed", "完成后下游处理失败：" + failureReason,
                UserContext.getUserId(), UserContext.getRealName());
        log.warn("烹饪任务[{}]完成后下游处理失败，但完成态已保留: {}", task.getTaskNo(), failureReason, e);
    }

    private void clearCompletionExceptionState(Long taskId) {
        jdbcTemplate.update(
                "UPDATE cook_task SET sync_status = ?, latest_sync_failure_reason = NULL, compensation_status = ? WHERE id = ? AND deleted = 0",
                SYNC_STATUS_NORMAL,
                COMPENSATION_STATUS_NONE,
                taskId
        );
    }

    private String buildCompletionLinkageFailureReason(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "烹饪完成后留样联动失败，请稍后重试补偿";
        }
        return "烹饪完成后留样联动失败：" + message;
    }

    private CookTaskVO buildTaskVO(CookTask task) {
        CookTaskVO vo = new CookTaskVO();
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setPlanId(task.getPlanId());
        vo.setTaskDate(task.getTaskDate());
        vo.setMenuId(task.getMenuId());
        vo.setMenuName(task.getMenuName());
        vo.setStatus(task.getStatus());
        vo.setPlannedQty(task.getPlannedQty());
        vo.setActualQty(task.getActualQty());
        vo.setAssignedChefId(task.getAssignedChefId());
        vo.setAssignedChefName(task.getAssignedChefName());
        vo.setChefName(task.getAssignedChefName());
        vo.setDeviceId(task.getDeviceId());
        vo.setDeviceName(task.getDeviceName());
        vo.setDeviceLocation(task.getDeviceLocation());
        vo.setMaterialPrepStatus(task.getMaterialPrepStatus());
        vo.setAllowStartTime(task.getAllowStartTime());
        vo.setAllowEndTime(task.getAllowEndTime());
        vo.setActualDuration(task.getCookingDuration());
        vo.setAiViolationCount(task.getAiViolationCount() != null ? task.getAiViolationCount() : 0);
        vo.setQualityScore(task.getQualityScore());
        vo.setStartTime(task.getStartTime());
        vo.setEndTime(task.getEndTime());
        vo.setRemark(task.getRemark());
        vo.setInitiatorId(task.getInitiatorId());
        vo.setInitiatorName(task.getInitiatorName());
        vo.setCompleterId(task.getCompleterId());
        vo.setCompleterName(task.getCompleterName());
        vo.setHandoffStatus(task.getHandoffStatus());
        vo.setHandoffRemark(task.getHandoffRemark());
        vo.setFoodSafetyPass(task.getFoodSafetyPass());
        vo.setTempAbnormalConfirmed(Integer.valueOf(1).equals(task.getTempAbnormalConfirmed()));
        vo.setTempAbnormalConfirmedBy(task.getTempAbnormalConfirmedBy());
        vo.setTempAbnormalConfirmedAt(task.getTempAbnormalConfirmedAt());
        vo.setCollectionStatus(task.getCollectionStatus());
        vo.setLastTemperatureRecordAt(task.getLastTemperatureRecordAt());
        vo.setSyncStatus(task.getSyncStatus());
        vo.setHasSyncException(task.getSyncStatus() != null && !SYNC_STATUS_NORMAL.equals(task.getSyncStatus()));
        vo.setSyncRetryCount(task.getSyncRetryCount());
        vo.setSyncRetryLimitReached(Integer.valueOf(1).equals(task.getSyncRetryLimitReached()));
        vo.setLatestSyncFailureReason(task.getLatestSyncFailureReason());
        vo.setCompensationStatus(task.getCompensationStatus());
        vo.setHasCompensationPending(COMPENSATION_STATUS_PENDING.equals(task.getCompensationStatus()));

        // 获取当前温度
        List<CookTemperatureRecord> records = temperatureRecordMapper.selectByTaskId(task.getId());
        if (!records.isEmpty()) {
            CookTemperatureRecord lastRecord = records.get(records.size() - 1);
            vo.setCurrentTemp(lastRecord.getTemperature());
            vo.setTemperatureAbnormal(lastRecord.getAbnormal());
        }

        // 补充 recipe_plan / recipe 关联字段
        enrichTaskVOFromRecipePlan(vo, task);

        return vo;
    }

    private CookTaskDetailVO buildTaskDetailVO(CookTask task) {
        CookTaskDetailVO detail = new CookTaskDetailVO();
        detail.setId(task.getId());
        detail.setTaskNo(task.getTaskNo());
        detail.setPlanId(task.getPlanId());
        detail.setRecipeId(task.getMenuId());
        detail.setMenuName(task.getMenuName());
        detail.setStatus(task.getStatus());
        detail.setPlannedQty(task.getPlannedQty());
        detail.setActualQty(task.getActualQty());
        detail.setAssignedChefId(task.getAssignedChefId());
        detail.setAssignedChefName(task.getAssignedChefName());
        detail.setChefName(task.getAssignedChefName());
        detail.setDeviceId(task.getDeviceId());
        detail.setDeviceName(task.getDeviceName());
        detail.setDeviceLocation(task.getDeviceLocation());
        detail.setMaterialPrepStatus(task.getMaterialPrepStatus());
        detail.setAllowStartTime(task.getAllowStartTime());
        detail.setAllowEndTime(task.getAllowEndTime());
        detail.setActualDuration(task.getCookingDuration());
        detail.setAiViolationCount(task.getAiViolationCount() != null ? task.getAiViolationCount() : 0);
        detail.setQualityScore(task.getQualityScore());
        detail.setRemark(task.getRemark());
        detail.setStartTime(task.getStartTime());
        detail.setEndTime(task.getEndTime());
        detail.setInitiatorId(task.getInitiatorId());
        detail.setInitiatorName(task.getInitiatorName());
        detail.setCompleterId(task.getCompleterId());
        detail.setCompleterName(task.getCompleterName());
        detail.setHandoffStatus(task.getHandoffStatus());
        detail.setHandoffRemark(task.getHandoffRemark());
        detail.setFoodSafetyPass(task.getFoodSafetyPass());
        detail.setTempAbnormalConfirmed(Integer.valueOf(1).equals(task.getTempAbnormalConfirmed()));
        detail.setTempAbnormalConfirmedBy(task.getTempAbnormalConfirmedBy());
        detail.setTempAbnormalConfirmedAt(task.getTempAbnormalConfirmedAt());
        detail.setCollectionStatus(task.getCollectionStatus());
        detail.setLastTemperatureRecordAt(task.getLastTemperatureRecordAt());
        detail.setSyncStatus(task.getSyncStatus());
        detail.setHasSyncException(task.getSyncStatus() != null && !SYNC_STATUS_NORMAL.equals(task.getSyncStatus()));
        detail.setSyncRetryCount(task.getSyncRetryCount());
        detail.setSyncRetryLimitReached(Integer.valueOf(1).equals(task.getSyncRetryLimitReached()));
        detail.setLatestSyncFailureReason(task.getLatestSyncFailureReason());
        detail.setCompensationStatus(task.getCompensationStatus());
        detail.setHasCompensationPending(COMPENSATION_STATUS_PENDING.equals(task.getCompensationStatus()));

        // 温度记录
        detail.setTemperatureRecords(getTemperatureRecords(task.getId()));

        // AI监控记录
        detail.setAiMonitorRecords(getAiMonitorRecords(task.getId()));

        // 获取当前温度和温度异常状态
        List<CookTemperatureRecord> records = temperatureRecordMapper.selectByTaskId(task.getId());
        if (!records.isEmpty()) {
            CookTemperatureRecord lastRecord = records.get(records.size() - 1);
            detail.setCurrentTemp(lastRecord.getTemperature());
            detail.setTemperatureAbnormal(lastRecord.getAbnormal());
        }

        // 补充 recipe_plan / recipe / recipe_ingredient 关联字段
        enrichTaskDetailFromRecipePlan(detail, task);

        return detail;
    }

    private boolean hasTemperatureAbnormal(CookTask task) {
        List<CookTemperatureRecord> records = temperatureRecordMapper.selectByTaskId(task.getId());
        return records.stream().anyMatch(r -> Boolean.TRUE.equals(r.getAbnormal()));
    }

    private void saveCookRecord(Long taskId, String recordType, String content, Long operatorId, String operatorName) {
        CookRecord record = new CookRecord();
        record.setTaskId(taskId);
        record.setRecordType(recordType);
        record.setContent(content);
        record.setRecordTime(LocalDateTime.now());
        record.setOperatorId(operatorId != null ? operatorId : UserContext.getUserId());
        record.setOperatorName(operatorName != null ? operatorName : UserContext.getRealName());
        cookRecordMapper.insert(record);
    }

    private void autoCreatePendingSampleRecord(CookTask task) {
        if (task == null || task.getId() == null) {
            return;
        }
        if (task.getMenuId() == null || task.getMenuName() == null || task.getMenuName().isBlank()) {
            throw new RuntimeException("烹饪任务缺少菜品信息，无法自动生成留样任务");
        }

        Long existingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sample_record WHERE task_id = ? AND deleted = 0 AND status <> 'voided'",
                Long.class,
                task.getId()
        );
        if (existingCount != null && existingCount > 0) {
            log.info("烹饪任务已存在留样记录，跳过自动生成, taskId={}", task.getId());
            return;
        }

        Map<String, Object> planInfo = loadRecipePlanInfo(task.getPlanId());
        LocalDate sampleDate = resolveSampleDate(task, planInfo);
        String mealType = resolveMealType(planInfo, task.getEndTime());
        int seq = nextSampleSeq(sampleDate);
        String datePart = sampleDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String sampleNo = "SP-" + datePart + String.format("%03d", seq);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        String insertSql = """
                INSERT INTO sample_record
                (sample_no, task_id, menu_id, menu_name, sample_date, meal_type, status, org_id, tenant_id, record_origin_type)
                VALUES (?, ?, ?, ?, ?, ?, 'pending_sample', ?, ?, ?)
                """;

        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sampleNo);
            ps.setLong(2, task.getId());
            ps.setLong(3, task.getMenuId());
            ps.setString(4, task.getMenuName());
            ps.setDate(5, java.sql.Date.valueOf(sampleDate));
            ps.setString(6, mealType);
            ps.setLong(7, task.getOrgId() != null ? task.getOrgId() : 1L);
            ps.setLong(8, task.getTenantId() != null ? task.getTenantId() : 1L);
            ps.setString(9, SAMPLE_ORIGIN_AUTO);
            return ps;
        }, keyHolder);

        if (inserted <= 0) {
            throw new RuntimeException("自动生成留样任务失败");
        }

        Long recordId = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
        if (recordId != null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO sample_operation_log
                    (record_id, action, action_name, operator_id, operator_name, content, terminal)
                    VALUES (?, 'auto_create', '系统自动生成', NULL, NULL, ?, 'system')
                    """,
                    recordId,
                    "烹饪任务完成后系统自动生成待留样任务"
            );
            auditLogService.log(
                    AuditModule.SAMPLE_RECORD,
                    AuditOperationType.CREATE,
                    recordId,
                    sampleNo,
                    "系统自动生成留样任务：" + sampleNo
            );
        }
    }

    private RollbackLinkageResult handleCookTaskRollbackLinkage(CookTask task,
                                                                String beforeTaskStatus,
                                                                String afterTaskStatus,
                                                                String triggerReason) {
        if (task == null || task.getId() == null) {
            return RollbackLinkageResult.empty();
        }
        if (!STATUS_COMPLETED.equals(beforeTaskStatus) || STATUS_COMPLETED.equals(afterTaskStatus)) {
            return RollbackLinkageResult.empty();
        }

        List<Map<String, Object>> sampleRecords = jdbcTemplate.queryForList(
                """
                SELECT id,
                       sample_no,
                       status,
                       void_reason,
                       disposal_remark
                FROM sample_record
                WHERE task_id = ?
                  AND deleted = 0
                  AND COALESCE(rollback_isolated, 0) = 0
                ORDER BY id ASC
                """,
                task.getId()
        );
        if (sampleRecords.isEmpty()) {
            return RollbackLinkageResult.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        RollbackLinkageResult result = new RollbackLinkageResult();
        String rollbackReason = buildSampleRollbackIsolationReason(task.getTaskNo(), beforeTaskStatus, afterTaskStatus, triggerReason);

        for (Map<String, Object> row : sampleRecords) {
            Long recordId = ((Number) row.get("id")).longValue();
            String sampleNo = (String) row.get("sample_no");
            String beforeSampleStatus = (String) row.get("status");
            String existingVoidReason = (String) row.get("void_reason");
            String existingDisposalRemark = (String) row.get("disposal_remark");
            String updatedVoidReason = SAMPLE_STATUS_VOIDED.equals(beforeSampleStatus)
                    && existingVoidReason != null
                    && !existingVoidReason.isBlank()
                    ? existingVoidReason
                    : rollbackReason;
            String updatedDisposalRemark = appendRollbackIsolationRemark(
                    existingDisposalRemark,
                    beforeSampleStatus,
                    afterTaskStatus,
                    rollbackReason
            );

            jdbcTemplate.update(
                    """
                    UPDATE sample_record
                    SET status = CASE WHEN status = 'voided' THEN status ELSE 'voided' END,
                        void_reason = CASE
                            WHEN status = 'voided' AND void_reason IS NOT NULL AND TRIM(void_reason) <> '' THEN void_reason
                            ELSE ?
                        END,
                        rollback_isolated = 1,
                        rollback_isolated_at = ?,
                        rollback_isolation_reason = ?,
                        disposal_remark = ?,
                        updated_by = ?,
                        updated_at = ?
                    WHERE id = ?
                      AND deleted = 0
                    """,
                    updatedVoidReason,
                    Timestamp.valueOf(now),
                    rollbackReason,
                    updatedDisposalRemark,
                    UserContext.getUserId(),
                    Timestamp.valueOf(now),
                    recordId
            );

            jdbcTemplate.update(
                    """
                    INSERT INTO sample_operation_log
                    (record_id, action, action_name, operator_id, operator_name, content, terminal)
                    VALUES (?, 'rollback_void', '回滚联动作废', ?, ?, ?, 'web')
                    """,
                    recordId,
                    UserContext.getUserId(),
                    UserContext.getRealName(),
                    buildSampleRollbackOperationContent(beforeSampleStatus, afterTaskStatus, rollbackReason)
            );

            auditLogService.log(
                    AuditModule.SAMPLE_RECORD,
                    AuditOperationType.STATUS_CHANGE,
                    recordId,
                    sampleNo,
                    "烹饪任务回滚联动作废留样记录：" + sampleNo
                            + "；任务状态：" + resolveTaskStatusLabel(beforeTaskStatus)
                            + " -> " + resolveTaskStatusLabel(afterTaskStatus)
                            + "；触发原因：" + triggerReason,
                    toJsonQuietly(buildAuditSnapshot(
                            "sampleNo", sampleNo,
                            "status", beforeSampleStatus,
                            "voidReason", existingVoidReason,
                            "rollbackIsolated", false
                    )),
                    toJsonQuietly(buildAuditSnapshot(
                            "sampleNo", sampleNo,
                            "status", SAMPLE_STATUS_VOIDED,
                            "voidReason", updatedVoidReason,
                            "rollbackIsolated", true,
                            "rollbackIsolationReason", rollbackReason
                    ))
            );

            result.addSampleNo(sampleNo);
        }
        return result;
    }

    private String buildRollbackTriggerReason(String triggerSource, String remark) {
        if (remark == null || remark.isBlank()) {
            return triggerSource;
        }
        return triggerSource + "：" + remark.trim();
    }

    private String buildSampleRollbackIsolationReason(String taskNo,
                                                      String beforeTaskStatus,
                                                      String afterTaskStatus,
                                                      String triggerReason) {
        return "关联烹饪任务[" + (taskNo != null ? taskNo : "-") + "]状态回滚："
                + resolveTaskStatusLabel(beforeTaskStatus)
                + " -> "
                + resolveTaskStatusLabel(afterTaskStatus)
                + "；触发原因："
                + triggerReason
                + "；系统已联动作废并隔离对应留样/销样历史";
    }

    private String appendRollbackIsolationRemark(String existingRemark,
                                                 String beforeSampleStatus,
                                                 String taskStatus,
                                                 String rollbackReason) {
        String linkageRemark = "[烹饪任务回滚] 留样状态"
                + resolveSampleStatusLabel(beforeSampleStatus)
                + " -> "
                + resolveSampleStatusLabel(SAMPLE_STATUS_VOIDED)
                + "；烹饪任务当前状态："
                + resolveTaskStatusLabel(taskStatus)
                + "；[提醒关停] 已终止留样/销样待办、到期预警、逾期考核与合规统计；[原因] "
                + rollbackReason;
        if (existingRemark == null || existingRemark.isBlank()) {
            return linkageRemark;
        }
        return existingRemark.trim() + "；" + linkageRemark;
    }

    private String buildSampleRollbackOperationContent(String beforeSampleStatus,
                                                       String taskStatus,
                                                       String rollbackReason) {
        return "系统将留样记录从"
                + resolveSampleStatusLabel(beforeSampleStatus)
                + "回写为"
                + resolveSampleStatusLabel(SAMPLE_STATUS_VOIDED)
                + "；关联烹饪任务当前状态："
                + resolveTaskStatusLabel(taskStatus)
                + "；"
                + rollbackReason;
    }

    private String resolveTaskStatusLabel(String status) {
        if (STATUS_PENDING.equals(status)) {
            return "待烹饪";
        }
        if (STATUS_IN_PROGRESS.equals(status)) {
            return "烹饪中";
        }
        if (STATUS_COMPLETED.equals(status)) {
            return "已完成";
        }
        if (STATUS_CANCELLED.equals(status)) {
            return "已取消";
        }
        if (STATUS_ARCHIVED.equals(status)) {
            return "已归档";
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
        if (SAMPLE_STATUS_VOIDED.equals(status)) {
            return "已作废";
        }
        return status != null ? status : "-";
    }

    private String toJsonQuietly(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("序列化回滚联动审计快照失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildAuditSnapshot(Object... keyValues) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (keyValues == null) {
            return snapshot;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key == null) {
                continue;
            }
            snapshot.put(String.valueOf(key), keyValues[i + 1]);
        }
        return snapshot;
    }

    private Map<String, Object> loadRecipePlanInfo(Long planId) {
        if (planId == null) {
            return Collections.emptyMap();
        }
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT plan_date, meal_type FROM recipe_plan WHERE id = ? AND deleted = 0",
                    planId
            );
        } catch (Exception e) {
            log.warn("查询菜谱计划失败, planId={}", planId, e);
            return Collections.emptyMap();
        }
    }

    private LocalDate resolveSampleDate(CookTask task, Map<String, Object> planInfo) {
        Object planDate = planInfo.get("plan_date");
        if (planDate instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (task.getEndTime() != null) {
            return task.getEndTime().toLocalDate();
        }
        return LocalDate.now();
    }

    private String resolveMealType(Map<String, Object> planInfo, LocalDateTime endTime) {
        Object mealType = planInfo.get("meal_type");
        if (mealType instanceof String mealTypeValue && !mealTypeValue.isBlank()) {
            return mealTypeValue;
        }
        int hour = endTime != null ? endTime.getHour() : LocalDateTime.now().getHour();
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

    private int nextSampleSeq(LocalDate sampleDate) {
        Integer seq = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) + 1 FROM sample_record WHERE sample_date = ? AND deleted = 0",
                Integer.class,
                java.sql.Date.valueOf(sampleDate)
        );
        return seq != null ? seq : 1;
    }

    private String generateTaskNo() {
        String timestamp = LocalDateTime.now().format(TASK_NO_FORMATTER);
        String random = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "CT" + timestamp + random;
    }

    private static final class RollbackLinkageResult {
        private final List<String> sampleNos = new ArrayList<>();

        static RollbackLinkageResult empty() {
            return new RollbackLinkageResult();
        }

        void addSampleNo(String sampleNo) {
            if (sampleNo != null && !sampleNo.isBlank()) {
                sampleNos.add(sampleNo);
            }
        }

        boolean hasLinkedRecords() {
            return !sampleNos.isEmpty();
        }

        String joinedSampleNos() {
            return String.join("、", sampleNos);
        }

        List<String> getSampleNos() {
            return sampleNos;
        }
    }

    private List<CookAIMonitorVO> parseAiMonitorRecords(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<CookAIMonitorVO>>() {});
        } catch (Exception e) {
            log.warn("解析AI监控记录失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 补充 CookTaskVO 中的 recipe_plan / recipe 关联字段
     */
    private void enrichTaskVOFromRecipePlan(CookTaskVO vo, CookTask task) {
        if (task.getPlanId() == null && task.getMenuId() == null) return;

        // 查 recipe_plan → planDate, mealType
        if (task.getPlanId() != null) {
            try {
                Map<String, Object> plan = jdbcTemplate.queryForMap(
                        "SELECT plan_date, start_date, end_date, meal_type FROM recipe_plan WHERE id = ? AND deleted = 0", task.getPlanId());
                if (plan.get("plan_date") != null) {
                    vo.setPlanDate(((java.sql.Date) plan.get("plan_date")).toLocalDate());
                }
                if (plan.get("start_date") != null) {
                    vo.setStartDate(((java.sql.Date) plan.get("start_date")).toLocalDate());
                }
                if (plan.get("end_date") != null) {
                    vo.setEndDate(((java.sql.Date) plan.get("end_date")).toLocalDate());
                }
                vo.setMealType((String) plan.get("meal_type"));
            } catch (Exception e) {
                log.warn("查询recipe_plan失败, planId={}: {}", task.getPlanId(), e.getMessage());
            }
        }

        // 查 recipe → standardDuration, targetTemp
        if (task.getMenuId() != null) {
            try {
                Map<String, Object> recipe = jdbcTemplate.queryForMap(
                        "SELECT target_cook_time, target_temp_min FROM recipe WHERE id = ? AND deleted = 0", task.getMenuId());
                Object cookTime = recipe.get("target_cook_time");
                if (cookTime != null) {
                    vo.setStandardDuration(((Number) cookTime).intValue());
                }
                Object tempMin = recipe.get("target_temp_min");
                if (tempMin != null) {
                    vo.setTargetTemp(((Number) tempMin).intValue());
                }
            } catch (Exception e) {
                log.warn("查询recipe失败, menuId={}: {}", task.getMenuId(), e.getMessage());
            }

            // 查 recipe_ingredient → ingredients (名称列表)
            try {
                List<String> names = jdbcTemplate.queryForList(
                        "SELECT material_name FROM recipe_ingredient WHERE recipe_id = ? AND deleted = 0 ORDER BY is_main DESC",
                        Collections.singletonList(task.getMenuId()).toArray(), String.class);
                vo.setIngredients(names);
            } catch (Exception e) {
                log.warn("查询recipe_ingredient失败, menuId={}: {}", task.getMenuId(), e.getMessage());
            }
        }
    }

    /**
     * 补充 CookTaskDetailVO 中的 recipe_plan / recipe / recipe_ingredient 关联字段
     */
    private void enrichTaskDetailFromRecipePlan(CookTaskDetailVO detail, CookTask task) {
        if (task.getPlanId() == null && task.getMenuId() == null) return;

        // 查 recipe_plan → planCode, planDate, mealType
        if (task.getPlanId() != null) {
            try {
                Map<String, Object> plan = jdbcTemplate.queryForMap(
                        "SELECT plan_code, plan_date, start_date, end_date, meal_type FROM recipe_plan WHERE id = ? AND deleted = 0", task.getPlanId());
                detail.setPlanCode((String) plan.get("plan_code"));
                if (plan.get("plan_date") != null) {
                    detail.setPlanDate(((java.sql.Date) plan.get("plan_date")).toLocalDate());
                }
                if (plan.get("start_date") != null) {
                    detail.setStartDate(((java.sql.Date) plan.get("start_date")).toLocalDate());
                }
                if (plan.get("end_date") != null) {
                    detail.setEndDate(((java.sql.Date) plan.get("end_date")).toLocalDate());
                }
                detail.setMealType((String) plan.get("meal_type"));
            } catch (Exception e) {
                log.warn("查询recipe_plan失败, planId={}: {}", task.getPlanId(), e.getMessage());
            }
        }

        // 查 recipe → recipeCode, description, cookingSteps, standardDuration, targetTempMin, targetTempMax
        if (task.getMenuId() != null) {
            try {
                Map<String, Object> recipe = jdbcTemplate.queryForMap(
                        "SELECT recipe_code, description, cooking_steps, target_cook_time, target_temp_min, target_temp_max " +
                                "FROM recipe WHERE id = ? AND deleted = 0", task.getMenuId());
                detail.setRecipeCode((String) recipe.get("recipe_code"));
                detail.setRecipeDescription((String) recipe.get("description"));
                detail.setCookingSteps((String) recipe.get("cooking_steps"));
                Object cookTime = recipe.get("target_cook_time");
                if (cookTime != null) {
                    detail.setStandardDuration(((Number) cookTime).intValue());
                }
                Object tempMin = recipe.get("target_temp_min");
                if (tempMin != null) {
                    detail.setTargetTempMin(((Number) tempMin).intValue());
                }
                Object tempMax = recipe.get("target_temp_max");
                if (tempMax != null) {
                    detail.setTargetTempMax(((Number) tempMax).intValue());
                }
            } catch (Exception e) {
                log.warn("查询recipe失败, menuId={}: {}", task.getMenuId(), e.getMessage());
            }

            // 查 recipe_ingredient → 完整食材列表
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT material_id, material_name, material_spec, quantity, unit, is_main " +
                                "FROM recipe_ingredient WHERE recipe_id = ? AND deleted = 0 ORDER BY is_main DESC",
                        task.getMenuId());
                List<CookTaskDetailVO.CookIngredientVO> ingredients = rows.stream().map(row -> {
                    CookTaskDetailVO.CookIngredientVO ing = new CookTaskDetailVO.CookIngredientVO();
                    ing.setMaterialId(row.get("material_id") != null ? ((Number) row.get("material_id")).longValue() : null);
                    ing.setMaterialName((String) row.get("material_name"));
                    ing.setMaterialSpec((String) row.get("material_spec"));
                    ing.setQuantity(row.get("quantity") != null ? (BigDecimal) row.get("quantity") : null);
                    ing.setUnit((String) row.get("unit"));
                    Object isMain = row.get("is_main");
                    ing.setMain(isMain != null && (isMain instanceof Boolean ? (Boolean) isMain : ((Number) isMain).intValue() == 1));
                    return ing;
                }).collect(Collectors.toList());
                detail.setIngredients(ingredients);
            } catch (Exception e) {
                log.warn("查询recipe_ingredient失败, menuId={}: {}", task.getMenuId(), e.getMessage());
            }
        }

        // 查 wms_outbound_order + wms_outbound_order_item + wms_inventory → 出库单号、批次号、追溯码
        if (task.getPlanId() != null) {
            enrichOutboundTraceInfo(detail, task.getPlanId());
        }
    }

    /**
     * 通过 planId 关联查询出库单信息，注入到 detail VO 中
     */
    private void enrichOutboundTraceInfo(CookTaskDetailVO detail, Long planId) {
        try {
            // 1. 查出库单
            List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                    "SELECT id, outbound_no FROM wms_outbound_order WHERE source_order_id = ? AND deleted = 0 LIMIT 1",
                    planId);
            if (orders.isEmpty()) return;

            Map<String, Object> order = orders.get(0);
            Long orderId = ((Number) order.get("id")).longValue();
            String outboundNo = (String) order.get("outbound_no");
            detail.setOutboundOrderNo(outboundNo);

            // 2. 查出库单物料明细
            List<Map<String, Object>> items = jdbcTemplate.queryForList(
                    "SELECT material_id, batch_no FROM wms_outbound_order_item WHERE outbound_order_id = ? AND deleted = 0",
                    orderId);

            // material_id → batch_no 映射
            java.util.Map<Long, String> materialBatchMap = new java.util.HashMap<>();
            java.util.Set<String> batchNos = new java.util.HashSet<>();
            for (Map<String, Object> item : items) {
                Long materialId = item.get("material_id") != null ? ((Number) item.get("material_id")).longValue() : null;
                String batchNo = (String) item.get("batch_no");
                if (materialId != null && batchNo != null) {
                    materialBatchMap.put(materialId, batchNo);
                    batchNos.add(batchNo);
                }
            }

            // 3. 查 inventory → trace_batch_id
            java.util.Map<String, String> batchTraceMap = new java.util.HashMap<>();
            if (!batchNos.isEmpty()) {
                String placeholders = String.join(",", batchNos.stream().map(b -> "?").toArray(String[]::new));
                List<Map<String, Object>> inventories = jdbcTemplate.queryForList(
                        "SELECT batch_no, trace_batch_id FROM wms_inventory WHERE batch_no IN (" + placeholders + ") AND deleted = 0",
                        batchNos.toArray());
                for (Map<String, Object> inv : inventories) {
                    String bn = (String) inv.get("batch_no");
                    String traceId = (String) inv.get("trace_batch_id");
                    if (bn != null && traceId != null) {
                        batchTraceMap.put(bn, traceId);
                    }
                }
            }

            // 4. 注入到 ingredients
            if (detail.getIngredients() != null) {
                for (CookTaskDetailVO.CookIngredientVO ing : detail.getIngredients()) {
                    if (ing.getMaterialId() != null && materialBatchMap.containsKey(ing.getMaterialId())) {
                        String batchNo = materialBatchMap.get(ing.getMaterialId());
                        ing.setBatchNo(batchNo);
                        ing.setTraceBatchId(batchTraceMap.get(batchNo));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询出库单追溯信息失败, planId={}: {}", planId, e.getMessage());
        }
    }
}
