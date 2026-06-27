package com.xykj.sample.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.FileStorageService;
import com.xykj.sample.dto.SampleHistoryTaskQueryDTO;
import com.xykj.sample.dto.SampleManualDisposalSupplementDTO;
import com.xykj.sample.dto.SampleOperationLockAcquireDTO;
import com.xykj.sample.dto.SampleOperationLockRefreshDTO;
import com.xykj.sample.dto.SampleOperationLockReleaseDTO;
import com.xykj.sample.dto.SampleRecordCreateDTO;
import com.xykj.sample.dto.SampleRecordDisposeDTO;
import com.xykj.sample.dto.SampleRecordHistoryCreateDTO;
import com.xykj.sample.dto.SampleRecordQueryDTO;
import com.xykj.sample.dto.SampleRecordRegisterDTO;
import com.xykj.sample.dto.SampleRecordUpdateDTO;
import com.xykj.sample.entity.SampleDisposalSupplement;
import com.xykj.sample.entity.SampleOperationLog;
import com.xykj.sample.entity.SampleRecord;
import com.xykj.sample.entity.SampleRecordOperationLock;
import com.xykj.sample.mapper.SampleDisposalSupplementMapper;
import com.xykj.sample.mapper.SampleOperationLogMapper;
import com.xykj.sample.mapper.SampleRecordMapper;
import com.xykj.sample.mapper.SampleRecordOperationLockMapper;
import com.xykj.sample.service.SampleRecordService;
import com.xykj.sample.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 留样记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleRecordServiceImpl implements SampleRecordService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter EXPORT_FILE_NAME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter EXPORT_DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String STATUS_PENDING_SAMPLE = "pending_sample";
    private static final String STATUS_SAMPLED = "sampled";
    private static final String STATUS_PENDING_DISPOSAL = "pending_disposal";
    private static final String STATUS_DISPOSED = "disposed";
    private static final String STATUS_OVERDUE = "overdue";
    private static final String STATUS_VOIDED = "voided";
    private static final String STATUS_ARCHIVED = "archived";
    private static final String STATUS_EVALUATED = "evaluated";
    private static final String LOCK_NONE = "none";
    private static final String LOCK_INVESTIGATION = "investigation";
    private static final String LOCK_ACCIDENT = "accident";
    private static final String TASK_STATUS_PENDING = "pending";
    private static final String TASK_STATUS_IN_PROGRESS = "in_progress";
    private static final String TASK_STATUS_CANCELLED = "cancelled";
    private static final String TASK_STATUS_COMPLETED = "completed";
    private static final String TASK_STATUS_ARCHIVED = "archived";
    private static final String ORIGIN_AUTO = "auto";
    private static final String ORIGIN_MANUAL_DAILY = "manual_daily";
    private static final String ORIGIN_MANUAL_HISTORY = "manual_history";
    private static final String ORIGIN_OFFLINE_DELAYED = "offline_delayed";
    private static final String ORIGIN_SYSTEM_BACKFILL = "system_backfill";
    private static final String SOURCE_AUTO = "系统自动生成";
    private static final String SOURCE_MANUAL_DAILY = "日常手工补录";
    private static final String SOURCE_MANUAL_HISTORY = "历史异常补录";
    private static final String SOURCE_OFFLINE_DELAYED = "离线迟传补录";
    private static final String SOURCE_SYSTEM_BACKFILL = "系统漏单回溯补录";
    private static final String DISPOSAL_SOURCE_SYSTEM_AUTO = "system_auto";
    private static final String DISPOSAL_SOURCE_MANUAL_EXCEPTION_SUPPLEMENT = "manual_exception_supplement";
    private static final String DISPOSAL_SOURCE_SYSTEM_AUTO_LABEL = "系统自动生成";
    private static final String DISPOSAL_SOURCE_MANUAL_EXCEPTION_LABEL = "手工异常补录";
    private static final String SAMPLE_LEDGER_SHEET_NAME = "留样台账";
    private static final String[] SAMPLE_LEDGER_EXPORT_HEADERS = {
            "留样编号",
            "所属组织",
            "菜谱名称",
            "烹饪任务编号",
            "留样时间",
            "留样人",
            "留样状态",
            "创建时间",
            "数据来源",
            "作废状态",
            "作废原因",
            "备注说明",
            "销样任务编号",
            "实际销样时间",
            "销样人",
            "销样状态",
            "是否超期",
            "超期时长",
            "销样作废状态",
            "销样数据来源",
            "到期提醒状态"
    };
    private static final int[] SAMPLE_LEDGER_EXPORT_COLUMN_WIDTHS = {
            18, 22, 24, 20, 20, 14, 14, 20, 18, 12, 24, 36, 18, 20, 14, 14, 10, 16, 14, 18, 14
    };
    private static final String ACTION_AUTO_CREATE = "auto_create";
    private static final String ACTION_MANUAL_CREATE = "manual_create";
    private static final String ACTION_HISTORY_SUPPLEMENT_CREATE = "history_supplement_create";
    private static final String ACTION_OFFLINE_DELAYED_SUPPLEMENT = "offline_delayed_supplement";
    private static final String ACTION_REGISTER = "register";
    private static final String ACTION_MANUAL_DISPOSAL_SUPPLEMENT = "manual_disposal_supplement";
    private static final String ACTION_ROLLBACK_VOID = "rollback_void";
    private static final String OPERATION_REGISTER = "register";
    private static final String OPERATION_EDIT = "edit";
    private static final String OPERATION_DISPOSE = "dispose";
    private static final String OPERATION_VOID = "void";
    private static final String OPERATION_ARCHIVE = "archive";
    private static final String OPERATION_AI_EVALUATE = "ai_evaluate";
    private static final String OPERATION_MANUAL_DISPOSAL_SUPPLEMENT = "manual_disposal_supplement";
    private static final Set<String> SUPPORTED_OPERATION_LOCK_TYPES = Set.of(
            OPERATION_REGISTER,
            OPERATION_EDIT,
            OPERATION_DISPOSE,
            OPERATION_VOID,
            OPERATION_ARCHIVE,
            OPERATION_AI_EVALUATE,
            OPERATION_MANUAL_DISPOSAL_SUPPLEMENT
    );
    private static final Map<String, String> OPERATION_LOCK_TYPE_LABELS = Map.of(
            OPERATION_REGISTER, "留样登记处理中",
            OPERATION_EDIT, "编辑处理中",
            OPERATION_DISPOSE, "销样处理中",
            OPERATION_VOID, "作废处理中",
            OPERATION_ARCHIVE, "归档处理中",
            OPERATION_AI_EVALUATE, "AI评估处理中",
            OPERATION_MANUAL_DISPOSAL_SUPPLEMENT, "销样手工补录处理中"
    );
    private static final String HEADER_OPERATION_LOCK_TOKEN = "X-Sample-Lock-Token";
    private static final int OPERATION_LOCK_ACTIVE = 1;
    private static final int OPERATION_LOCK_INACTIVE = 0;
    private static final long OPERATION_LOCK_TIMEOUT_SECONDS = 120L;
    private static final String PERMISSION_HISTORY_SUPPLEMENT = "sample:historySupplement";
    private static final String PERMISSION_MANUAL_DISPOSAL_SUPPLEMENT = "sample:manualDisposalSupplement";
    private static final Set<String> HISTORY_SUPPLEMENT_ADMIN_ROLE_CODES = Set.of(
            "SUPER_ADMIN", "OPS_ADMIN", "OPERATION_ADMIN", "OPERATION_MAINTAIN_ADMIN", "MAINTAIN_ADMIN"
    );
    private static final List<String> HISTORY_SUPPLEMENT_ADMIN_ROLE_NAME_KEYWORDS = List.of(
            "系统管理员", "超级管理员", "运维管理员", "运维"
    );
    private static final Set<String> MANUAL_DISPOSAL_SUPPLEMENT_ADMIN_ROLE_CODES = Set.of(
            "SUPER_ADMIN", "OPS_ADMIN", "OPERATION_ADMIN", "OPERATION_MAINTAIN_ADMIN", "MAINTAIN_ADMIN",
            "CANTEEN_SUPER_ADMIN", "DINING_SUPER_ADMIN", "KITCHEN_SUPER_ADMIN"
    );
    private static final List<String> MANUAL_DISPOSAL_SUPPLEMENT_ADMIN_ROLE_NAME_KEYWORDS = List.of(
            "系统管理员", "超级管理员", "运维管理员", "运维", "食堂超级管理员"
    );
    private static final Set<String> MANUAL_DISPOSAL_ALLOWED_SCENES = Set.of(
            "system_missing",
            "interface_sync_exception",
            "device_offline",
            "history_migration_fix",
            "ops_closure_repair"
    );
    private static final Set<String> MANUAL_DISPOSAL_ELIGIBLE_STATUSES = Set.of(
            STATUS_SAMPLED, STATUS_EVALUATED, STATUS_PENDING_DISPOSAL, STATUS_OVERDUE
    );
    private static final Map<String, String> MANUAL_DISPOSAL_SCENE_LABELS = Map.of(
            "system_missing", "系统自动生成销样漏单",
            "interface_sync_exception", "接口异常未同步",
            "device_offline", "硬件离线断网漏传",
            "history_migration_fix", "历史数据迁移纠错",
            "ops_closure_repair", "运维排查补闭环"
    );

    /** 合法状态转换矩阵 */
    private static final Map<String, Set<String>> STATE_TRANSITIONS = new HashMap<>();
    static {
        STATE_TRANSITIONS.put(STATUS_PENDING_SAMPLE, Set.of(STATUS_SAMPLED, STATUS_VOIDED));
        STATE_TRANSITIONS.put(STATUS_SAMPLED, Set.of(STATUS_EVALUATED, STATUS_PENDING_DISPOSAL, STATUS_DISPOSED, STATUS_OVERDUE, STATUS_VOIDED));
        STATE_TRANSITIONS.put(STATUS_EVALUATED, Set.of(STATUS_PENDING_DISPOSAL, STATUS_DISPOSED, STATUS_OVERDUE, STATUS_VOIDED));
        STATE_TRANSITIONS.put(STATUS_PENDING_DISPOSAL, Set.of(STATUS_DISPOSED, STATUS_OVERDUE, STATUS_VOIDED));
        STATE_TRANSITIONS.put(STATUS_OVERDUE, Set.of(STATUS_DISPOSED, STATUS_VOIDED));
        STATE_TRANSITIONS.put(STATUS_DISPOSED, Set.of(STATUS_ARCHIVED, STATUS_VOIDED));
        STATE_TRANSITIONS.put(STATUS_ARCHIVED, Set.of());
        STATE_TRANSITIONS.put(STATUS_VOIDED, Set.of());
    }

    private static final List<String> ALLOWED_UPLOAD_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "video/mp4", "application/pdf"
    );
    private static final long MAX_UPLOAD_SIZE = 50 * 1024 * 1024; // 50MB

    private final SampleRecordMapper sampleRecordMapper;
    private final SampleDisposalSupplementMapper sampleDisposalSupplementMapper;
    private final SampleOperationLogMapper operationLogMapper;
    private final SampleRecordOperationLockMapper sampleRecordOperationLockMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final DataScopeService dataScopeService;
    private final FileStorageService fileStorageService;
    private final ConcurrentMap<Long, ReentrantLock> manualDisposalLocks = new ConcurrentHashMap<>();

    @Override
    @DataScope
    public SampleDashboardVO getDashboard(SampleRecordQueryDTO query) {
        LambdaQueryWrapper<SampleRecord> wrapper = buildQueryWrapper(query, false);
        List<SampleRecord> records = sampleRecordMapper.selectList(wrapper);

        long total = records.size();
        long pendingDisposal = records.stream().filter(r ->
                STATUS_SAMPLED.equals(r.getStatus()) ||
                STATUS_PENDING_DISPOSAL.equals(r.getStatus()) ||
                STATUS_OVERDUE.equals(r.getStatus()) ||
                STATUS_EVALUATED.equals(r.getStatus())
        ).count();
        long disposed = records.stream().filter(r -> STATUS_DISPOSED.equals(r.getStatus())).count();
        long overdue = records.stream().filter(r -> STATUS_OVERDUE.equals(r.getStatus())).count();
        long todaySampled = records.stream()
                .filter(r -> r.getSampledAt() != null && LocalDate.now().equals(r.getSampledAt().toLocalDate()))
                .count();

        return SampleDashboardVO.builder()
                .totalSamples(total)
                .pendingDisposal(pendingDisposal)
                .disposed(disposed)
                .overdue(overdue)
                .todaySampled(todaySampled)
                .build();
    }

    @Override
    @DataScope
    public PageResult<SampleRecordVO> getRecordPage(SampleRecordQueryDTO query) {
        LambdaQueryWrapper<SampleRecord> wrapper = buildQueryWrapper(query, true);
        wrapper.orderByDesc(SampleRecord::getCreatedAt);

        List<SampleRecord> allRecords = sampleRecordMapper.selectList(wrapper);
        Map<Long, CookTaskSnapshot> taskSnapshotMap = loadCookTaskSnapshotMap(
                allRecords.stream()
                        .map(SampleRecord::getTaskId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        );
        Map<Long, SampleDisposalSupplement> disposalSupplementMap = loadDisposalSupplementMap(
                allRecords.stream().map(SampleRecord::getId).collect(Collectors.toSet())
        );
        Map<Long, SampleRecordOperationLock> operationLockMap = loadActiveOperationLockMap(
                allRecords.stream().map(SampleRecord::getId).collect(Collectors.toSet())
        );
        List<SampleRecordVO> voList = allRecords.stream()
                .map(record -> buildRecordVO(
                        record,
                        taskSnapshotMap.get(record.getTaskId()),
                        disposalSupplementMap.get(record.getId()),
                        operationLockMap.get(record.getId())
                ))
                .collect(Collectors.toList());

        int pageNum = query.getPageNum() != null ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 20;
        int fromIndex = Math.min((pageNum - 1) * pageSize, voList.size());
        int toIndex = Math.min(fromIndex + pageSize, voList.size());
        List<SampleRecordVO> pageList = voList.subList(fromIndex, toIndex);

        return PageResult.of(pageList, (long) pageNum, (long) pageSize, (long) voList.size());
    }

    @Override
    public List<SampleAvailableCookTaskVO> getAvailableCookTasks() {
        StringBuilder sql = new StringBuilder("""
                SELECT ct.id,
                       ct.task_no,
                       ct.menu_id,
                       ct.menu_name,
                       ct.status,
                       ct.end_time,
                       ct.org_id,
                       ct.tenant_id,
                       rp.plan_date,
                       rp.meal_type
                FROM cook_task ct
                LEFT JOIN recipe_plan rp ON rp.id = ct.plan_id AND rp.deleted = 0
                WHERE ct.deleted = 0
                  AND ct.tenant_id = ?
                  AND ct.status = 'completed'
                  AND COALESCE(rp.plan_date, DATE(ct.end_time)) = CURDATE()
                  AND NOT EXISTS (
                      SELECT 1
                      FROM sample_record sr
                      WHERE sr.task_id = ct.id
                        AND sr.deleted = 0
                        AND sr.status <> 'voided'
                  )
                """);
        List<Object> params = new ArrayList<>();
        params.add(getCurrentTenantId());

        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (scope.isRestricted()) {
            if (scope.getOrgIds().isEmpty()) {
                return Collections.emptyList();
            }
            sql.append(" AND ct.org_id IN (")
                    .append(scope.getOrgIds().stream().map(id -> "?").collect(Collectors.joining(",")))
                    .append(")");
            params.addAll(scope.getOrgIds());
        }
        sql.append(" ORDER BY ct.end_time DESC, ct.id DESC");

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            SampleAvailableCookTaskVO vo = new SampleAvailableCookTaskVO();
            vo.setId(rs.getLong("id"));
            vo.setTaskNo(rs.getString("task_no"));
            vo.setMenuId(rs.getLong("menu_id"));
            if (rs.wasNull()) {
                vo.setMenuId(null);
            }
            vo.setMenuName(rs.getString("menu_name"));
            vo.setTaskStatus(rs.getString("status"));
            java.sql.Date planDate = rs.getDate("plan_date");
            java.sql.Timestamp completedAt = rs.getTimestamp("end_time");
            LocalDateTime completedTime = completedAt != null ? completedAt.toLocalDateTime() : null;
            vo.setSampleDate(planDate != null
                    ? planDate.toLocalDate()
                    : (completedTime != null ? completedTime.toLocalDate() : LocalDate.now()));
            vo.setMealType(rs.getString("meal_type"));
            vo.setCompletedAt(completedTime);
            return vo;
        });
    }

    @Override
    public List<SampleAvailableCookTaskVO> getHistoryAvailableCookTasks(SampleHistoryTaskQueryDTO query) {
        ensureHistorySupplementPrivilege();
        LocalDate businessDate = normalizeHistoryBusinessDate(query != null ? query.getBusinessDate() : null);
        String keyword = query != null && query.getKeyword() != null ? query.getKeyword().trim() : null;

        StringBuilder sql = new StringBuilder("""
                SELECT ct.id,
                       ct.task_no,
                       ct.menu_id,
                       ct.menu_name,
                       ct.status,
                       ct.end_time,
                       ct.org_id,
                       ct.tenant_id,
                       rp.plan_date,
                       rp.meal_type
                FROM cook_task ct
                LEFT JOIN recipe_plan rp ON rp.id = ct.plan_id AND rp.deleted = 0
                WHERE ct.deleted = 0
                  AND ct.tenant_id = ?
                  AND ct.status = 'completed'
                  AND COALESCE(rp.plan_date, DATE(ct.end_time)) = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM sample_record sr
                      WHERE sr.task_id = ct.id
                        AND sr.deleted = 0
                        AND sr.status <> 'voided'
                  )
                """);
        List<Object> params = new ArrayList<>();
        params.add(getCurrentTenantId());
        params.add(java.sql.Date.valueOf(businessDate));

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (ct.task_no LIKE ? OR ct.menu_name LIKE ?)");
            String likeKeyword = "%" + keyword + "%";
            params.add(likeKeyword);
            params.add(likeKeyword);
        }

        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (scope.isRestricted()) {
            if (scope.getOrgIds().isEmpty()) {
                return Collections.emptyList();
            }
            sql.append(" AND ct.org_id IN (")
                    .append(scope.getOrgIds().stream().map(id -> "?").collect(Collectors.joining(",")))
                    .append(")");
            params.addAll(scope.getOrgIds());
        }
        sql.append(" ORDER BY ct.end_time DESC, ct.id DESC");

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            SampleAvailableCookTaskVO vo = new SampleAvailableCookTaskVO();
            vo.setId(rs.getLong("id"));
            vo.setTaskNo(rs.getString("task_no"));
            vo.setMenuId(rs.getLong("menu_id"));
            if (rs.wasNull()) {
                vo.setMenuId(null);
            }
            vo.setMenuName(rs.getString("menu_name"));
            vo.setTaskStatus(rs.getString("status"));
            java.sql.Date planDate = rs.getDate("plan_date");
            java.sql.Timestamp completedAt = rs.getTimestamp("end_time");
            LocalDateTime completedTime = completedAt != null ? completedAt.toLocalDateTime() : null;
            vo.setSampleDate(planDate != null ? planDate.toLocalDate() : businessDate);
            vo.setMealType(rs.getString("meal_type"));
            vo.setCompletedAt(completedTime);
            return vo;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.SAMPLE_RECORD,
            operationType = AuditOperationType.CREATE,
            targetId = "#result.id",
            targetNo = "#result.sampleNo",
            desc = "'新增留样记录：' + #result.sampleNo",
            mapper = SampleRecordMapper.class
    )
    public SampleRecordDetailVO createRecord(SampleRecordCreateDTO dto) {
        if (dto == null || dto.getTaskId() == null) {
            throw new RuntimeException("请选择关联烹饪任务");
        }

        CookTaskSnapshot taskSnapshot = loadCookTaskSnapshot(dto.getTaskId(), true);
        validateTaskAccessible(taskSnapshot);
        validateManualCreateTask(taskSnapshot);
        ensureNoActiveSampleRecord(taskSnapshot.getId(), null);

        SampleRecord record = createPendingSampleRecord(taskSnapshot, ORIGIN_MANUAL_DAILY, false);
        SampleRecordDetailVO result = buildDetailVO(record);

        if (hasRegisterPayload(dto)) {
            SampleRecordRegisterDTO registerDTO = new SampleRecordRegisterDTO();
            registerDTO.setSampleWeight(dto.getSampleWeight());
            registerDTO.setSampleImages(dto.getSampleImages());
            registerDTO.setStorageLocation(dto.getStorageLocation());
            registerDTO.setStorageTemp(dto.getStorageTemp());
            registerDTO.setSampledBy(dto.getSampledBy());
            result = registerRecordInternal(record, registerDTO, taskSnapshot);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.SAMPLE_RECORD,
            operationType = AuditOperationType.CREATE,
            targetId = "#result.id",
            targetNo = "#result.sampleNo",
            desc = "'历史留样补录：' + #result.sampleNo + '，类型：' + (#dto.recordOriginType == 'offline_delayed' ? '离线迟传补录' : '历史异常补录') + '，原因：' + #dto.supplementReason",
            mapper = SampleRecordMapper.class
    )
    public SampleRecordDetailVO createHistoricalRecord(SampleRecordHistoryCreateDTO dto) {
        if (dto == null || dto.getTaskId() == null) {
            throw new RuntimeException("请选择历史烹饪任务");
        }
        ensureHistorySupplementPrivilege();
        validateHistoryCreatePayload(dto);

        CookTaskSnapshot taskSnapshot = loadCookTaskSnapshot(dto.getTaskId(), true);
        validateTaskAccessible(taskSnapshot);
        validateHistoricalCreateTask(taskSnapshot);
        ensureNoActiveSampleRecord(taskSnapshot.getId(), null);

        SampleRecord record = new SampleRecord();
        LocalDate businessDate = taskSnapshot.getSampleDate() != null ? taskSnapshot.getSampleDate() : LocalDate.now();
        String datePart = businessDate.format(DATE_FMT);
        int seq = sampleRecordMapper.nextSeqForDate(businessDate);
        LocalDateTime sampledAt = resolveHistoricalSampledAt(taskSnapshot);
        LocalDateTime disposalDueAt = sampledAt.plusHours(48);

        record.setSampleNo("SP-" + datePart + String.format("%03d", seq));
        record.setTaskId(taskSnapshot.getId());
        record.setMenuId(taskSnapshot.getMenuId());
        record.setMenuName(taskSnapshot.getMenuName());
        record.setSampleDate(businessDate);
        record.setMealType(taskSnapshot.getMealType());
        record.setSampleWeight(dto.getSampleWeight());
        record.setStorageLocation(normalizeOptionalText(dto.getStorageLocation()));
        record.setStorageTemp(dto.getStorageTemp());
        record.setSampledBy(dto.getSampledBy());
        record.setSampledAt(sampledAt);
        record.setDisposalDueAt(disposalDueAt);
        record.setStatus(disposalDueAt.isBefore(LocalDateTime.now()) ? STATUS_OVERDUE : STATUS_SAMPLED);
        record.setOrgId(taskSnapshot.getOrgId());
        record.setTenantId(taskSnapshot.getTenantId());
        record.setLockStatus(LOCK_NONE);
        record.setFoodSafetyLedgerNo("TA-" + datePart + String.format("%03d", seq));
        record.setRecordOriginType(dto.getRecordOriginType());
        record.setSupplementReason(dto.getSupplementReason().trim());
        record.setSupplementRemark(dto.getSupplementRemark().trim());
        if (dto.getSampleImages() != null) {
            try {
                record.setSampleImages(objectMapper.writeValueAsString(dto.getSampleImages()));
            } catch (JsonProcessingException e) {
                log.warn("序列化历史补录留样照片失败", e);
            }
        }
        sampleRecordMapper.insert(record);

        String action = ORIGIN_OFFLINE_DELAYED.equals(dto.getRecordOriginType())
                ? ACTION_OFFLINE_DELAYED_SUPPLEMENT
                : ACTION_HISTORY_SUPPLEMENT_CREATE;
        String actionName = ORIGIN_OFFLINE_DELAYED.equals(dto.getRecordOriginType())
                ? "离线迟传补录"
                : "历史异常补录";
        String actionContent = actionName
                + "；业务日期："
                + businessDate
                + "；烹饪任务："
                + (taskSnapshot.getTaskNo() != null ? taskSnapshot.getTaskNo() : "-")
                + "；补录原因："
                + dto.getSupplementReason().trim()
                + "；备注："
                + dto.getSupplementRemark().trim();
        logOperation(record.getId(), action, actionName, actionContent);

        return buildDetailVO(record, taskSnapshot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleRecordDetailVO registerRecord(Long id, SampleRecordRegisterDTO dto) {
        SampleRecord record = getRequiredRecordForUpdate(id);
        String requestLockToken = resolveCurrentRequestOperationLockToken();
        assertOperationLockAllowed(record, OPERATION_REGISTER, requestLockToken);
        checkLock(record);
        if (!STATUS_PENDING_SAMPLE.equals(record.getStatus())) {
            throw new RuntimeException("只有待留样状态的记录可以执行留样登记");
        }
        if (record.getTaskId() == null) {
            throw new RuntimeException("留样任务缺少关联烹饪任务，无法登记");
        }

        CookTaskSnapshot taskSnapshot = loadCookTaskSnapshot(record.getTaskId(), true);
        validateTaskAccessible(taskSnapshot);
        if (!TASK_STATUS_COMPLETED.equals(taskSnapshot.getStatus()) && !TASK_STATUS_ARCHIVED.equals(taskSnapshot.getStatus())) {
            throw new RuntimeException("仅关联烹饪任务状态为已完成或已归档的留样任务允许执行留样登记");
        }
        ensureNoActiveSampleRecord(record.getTaskId(), record.getId());
        SampleRecordDetailVO result = registerRecordInternal(record, dto, taskSnapshot);
        releaseOperationLockAfterWrite(record.getId(), requestLockToken);
        result.setOperationLock(buildOperationLockVO(null, false));
        return result;
    }

    @Override
    public SampleRecordDetailVO getRecordDetail(Long id) {
        SampleRecord record = getRequiredRecord(id);
        return buildDetailVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleOperationLockVO acquireOperationLock(Long id, SampleOperationLockAcquireDTO dto) {
        SampleRecord record = getRequiredRecord(id);
        String operationType = normalizeOperationLockType(dto);
        validateOperationLockAcquire(record, operationType);

        SampleRecordOperationLock lock = sampleRecordOperationLockMapper.selectByRecordIdForUpdate(id);
        LocalDateTime now = LocalDateTime.now();
        if (isOperationLockActive(lock, now) && !isCurrentUserLockOwner(lock)) {
            throw new RuntimeException(resolveOperationLockConflictMessage(lock.getOperationType(), operationType));
        }

        String lockToken = UUID.randomUUID().toString();
        if (lock == null) {
            lock = new SampleRecordOperationLock();
            lock.setSampleRecordId(record.getId());
            lock.setSampleNo(record.getSampleNo());
            lock.setOrgId(record.getOrgId());
            lock.setTenantId(record.getTenantId());
        }
        fillOperationLock(lock, record, operationType, lockToken, now);
        if (lock.getId() == null) {
            sampleRecordOperationLockMapper.insert(lock);
        } else {
            sampleRecordOperationLockMapper.updateById(lock);
        }
        return buildOperationLockVO(lock, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleOperationLockVO refreshOperationLock(Long id, SampleOperationLockRefreshDTO dto) {
        String lockToken = normalizeOperationLockToken(dto != null ? dto.getLockToken() : null);
        SampleRecordOperationLock lock = sampleRecordOperationLockMapper.selectByRecordIdForUpdate(id);
        LocalDateTime now = LocalDateTime.now();
        ensureOwnedOperationLock(lock, lockToken, now);
        lock.setLastHeartbeatAt(now);
        lock.setExpiresAt(now.plusSeconds(OPERATION_LOCK_TIMEOUT_SECONDS));
        sampleRecordOperationLockMapper.updateById(lock);
        return buildOperationLockVO(lock, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseOperationLock(Long id, SampleOperationLockReleaseDTO dto) {
        String lockToken = normalizeOperationLockToken(dto != null ? dto.getLockToken() : null);
        SampleRecordOperationLock lock = sampleRecordOperationLockMapper.selectByRecordIdForUpdate(id);
        if (lock == null) {
            return;
        }
        if (!Objects.equals(lock.getSampleRecordId(), id)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!isOperationLockActive(lock, now)) {
            deactivateOperationLock(lock, false);
            sampleRecordOperationLockMapper.updateById(lock);
            return;
        }
        if (!Objects.equals(lockToken, lock.getLockToken())) {
            return;
        }
        deactivateOperationLock(lock, false);
        sampleRecordOperationLockMapper.updateById(lock);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleRecordDetailVO disposeRecord(Long id, SampleRecordDisposeDTO dto) {
        SampleRecord record = getRequiredRecordForUpdate(id);
        String requestLockToken = resolveCurrentRequestOperationLockToken();
        assertOperationLockAllowed(record, OPERATION_DISPOSE, requestLockToken);
        checkLock(record);
        validateTaskCompletedForBusinessOperation(record, "销样");
        validateTransition(record.getStatus(), STATUS_DISPOSED);
        if (dto.getDisposalImages() == null || dto.getDisposalImages().isEmpty()) {
            throw new RuntimeException("销样照片不能为空");
        }

        String beforeData = JSONUtil.toJsonStr(record);

        record.setDisposalBy(UserContext.getUserId());
        record.setDisposalAt(LocalDateTime.now());
        record.setDisposalRemark(dto.getDisposalRemark());
        record.setStatus(STATUS_DISPOSED);

        try {
            record.setDisposalImages(objectMapper.writeValueAsString(dto.getDisposalImages()));
        } catch (JsonProcessingException e) {
            log.warn("序列化销样照片失败", e);
        }

        sampleRecordMapper.updateById(record);

        logOperation(record.getId(), "dispose", "销样", "执行销样操作");

        auditLogService.log(AuditModule.SAMPLE_RECORD, AuditOperationType.STATUS_CHANGE,
                record.getId(), record.getSampleNo(), "执行销样操作：" + record.getSampleNo(),
                beforeData, JSONUtil.toJsonStr(record));

        releaseOperationLockAfterWrite(record.getId(), requestLockToken);

        return buildDetailVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleRecordDetailVO manualSupplementDisposal(Long id, SampleManualDisposalSupplementDTO dto) {
        ensureManualDisposalSupplementPrivilege();
        validateManualDisposalSupplementPayload(dto);

        ReentrantLock lock = acquireManualDisposalLock(id);
        try {
            SampleRecord record = getRequiredRecordForUpdate(id);
            String requestLockToken = resolveCurrentRequestOperationLockToken();
            assertOperationLockAllowed(record, OPERATION_MANUAL_DISPOSAL_SUPPLEMENT, requestLockToken);
            checkLock(record);
            validateTaskCompletedForBusinessOperation(record, "销样手工补录");
            validateManualDisposalSupplementRecord(record);
            ensureNoManualDisposalSupplementExists(record.getId());

            String beforeData = JSONUtil.toJsonStr(record);
            LocalDateTime now = LocalDateTime.now();
            String sceneCode = dto.getSupplementScene().trim();
            String supplementRemark = dto.getSupplementRemark().trim();

            record.setDisposalBy(UserContext.getUserId());
            record.setDisposalAt(now);
            record.setDisposalRemark(normalizeOptionalText(dto.getDisposalRemark()));
            record.setStatus(STATUS_DISPOSED);
            try {
                record.setDisposalImages(objectMapper.writeValueAsString(dto.getDisposalImages()));
            } catch (JsonProcessingException e) {
                log.warn("序列化销样补录照片失败", e);
            }
            sampleRecordMapper.updateById(record);

            SampleDisposalSupplement supplement = new SampleDisposalSupplement();
            supplement.setSampleRecordId(record.getId());
            supplement.setSampleNo(record.getSampleNo());
            supplement.setTaskId(record.getTaskId());
            supplement.setDisposalSourceType(DISPOSAL_SOURCE_MANUAL_EXCEPTION_SUPPLEMENT);
            supplement.setSupplementScene(sceneCode);
            supplement.setSupplementRemark(supplementRemark);
            supplement.setOrgId(record.getOrgId());
            supplement.setTenantId(record.getTenantId());
            sampleDisposalSupplementMapper.insert(supplement);

            String sceneLabel = resolveManualDisposalSceneLabel(sceneCode);
            String operationContent = "销样手工补录；场景：" + sceneLabel
                    + "；补录备注：" + supplementRemark;
            logOperation(record.getId(), ACTION_MANUAL_DISPOSAL_SUPPLEMENT, "销样手工补录", operationContent);

            auditLogService.log(
                    AuditModule.SAMPLE_RECORD,
                    AuditOperationType.STATUS_CHANGE,
                    record.getId(),
                    record.getSampleNo(),
                    "管理员执行销样手工补录：" + record.getSampleNo() + "，场景：" + sceneLabel,
                    beforeData,
                    JSONUtil.toJsonStr(record)
            );

            CookTaskSnapshot taskSnapshot = record.getTaskId() != null ? loadCookTaskSnapshot(record.getTaskId(), false) : null;
            releaseOperationLockAfterWrite(record.getId(), requestLockToken);
            return buildDetailVO(record, taskSnapshot, supplement);
        } finally {
            releaseManualDisposalLock(id, lock);
        }
    }

    @Override
    @DataScope
    public PageResult<DisposalReminderVO> getDisposalReminders(SampleRecordQueryDTO query) {
        LambdaQueryWrapper<SampleRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SampleRecord::getStatus, STATUS_SAMPLED, STATUS_EVALUATED, STATUS_PENDING_DISPOSAL, STATUS_OVERDUE)
                .eq(SampleRecord::getRollbackIsolated, 0)
                .eq(query.getSampleDate() != null, SampleRecord::getSampleDate, query.getSampleDate())
                .eq(query.getMealType() != null && !query.getMealType().isBlank(), SampleRecord::getMealType, query.getMealType())
                .like(query.getMenuName() != null && !query.getMenuName().isBlank(), SampleRecord::getMenuName, query.getMenuName())
                .eq(query.getOrgId() != null, SampleRecord::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), SampleRecord::getOrgId, query.getOrgIds())
                .orderByAsc(SampleRecord::getDisposalDueAt);
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(SampleRecord::getId);
        }

        List<SampleRecord> allRecords = sampleRecordMapper.selectList(wrapper);
        LocalDateTime now = LocalDateTime.now();

        // 惰性更新超期状态
        List<SampleRecord> toUpdate = allRecords.stream()
                .filter(r -> !STATUS_OVERDUE.equals(r.getStatus())
                        && r.getDisposalDueAt() != null
                        && r.getDisposalDueAt().isBefore(now))
                .collect(Collectors.toList());
        for (SampleRecord r : toUpdate) {
            r.setStatus(STATUS_OVERDUE);
            sampleRecordMapper.updateById(r);
        }

        Map<Long, SampleRecordOperationLock> operationLockMap = loadActiveOperationLockMap(
                allRecords.stream().map(SampleRecord::getId).collect(Collectors.toSet())
        );

        List<DisposalReminderVO> voList = allRecords.stream().map(record -> {
            DisposalReminderVO vo = new DisposalReminderVO();
            vo.setId(record.getId());
            vo.setSampleNo(record.getSampleNo());
            vo.setTaskId(record.getTaskId());
            vo.setMenuName(record.getMenuName());
            vo.setSampleDate(record.getSampleDate());
            vo.setSampledAt(record.getSampledAt());
            vo.setStorageLocation(record.getStorageLocation());
            vo.setDisposalDueAt(record.getDisposalDueAt());
            vo.setStatus(STATUS_OVERDUE.equals(record.getStatus()) ? STATUS_OVERDUE : record.getStatus());

            boolean overdue = record.getDisposalDueAt() != null && record.getDisposalDueAt().isBefore(now);
            vo.setIsOverdue(overdue);
            if (record.getDisposalDueAt() != null) {
                vo.setRemainHours(ChronoUnit.HOURS.between(now, record.getDisposalDueAt()));
            } else {
                vo.setRemainHours(0L);
            }
            vo.setOperationLock(buildOperationLockVO(operationLockMap.get(record.getId()), false));
            return vo;
        }).collect(Collectors.toList());

        int pageNum = query.getPageNum() != null ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 20;
        int fromIndex = Math.min((pageNum - 1) * pageSize, voList.size());
        int toIndex = Math.min(fromIndex + pageSize, voList.size());
        List<DisposalReminderVO> pageList = voList.subList(fromIndex, toIndex);

        return PageResult.of(pageList, (long) pageNum, (long) pageSize, (long) voList.size());
    }

    @Override
    public SampleRecordDetailVO getDisposalDetail(Long id) {
        SampleRecord record = getRequiredRecord(id);
        if (record.getDisposalAt() == null) {
            throw new RuntimeException("该记录尚未销样");
        }
        return buildDetailVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiEvaluateVO aiEvaluateRecord(Long id) {
        SampleRecord record = getRequiredRecordForUpdate(id);
        String requestLockToken = resolveCurrentRequestOperationLockToken();
        assertOperationLockAllowed(record, OPERATION_AI_EVALUATE, requestLockToken);
        validateTaskCompletedForBusinessOperation(record, "AI评估");

        String beforeData = JSONUtil.toJsonStr(record);

        Random random = new Random();
        BigDecimal colorScore = BigDecimal.valueOf(80 + random.nextInt(16));
        BigDecimal shapeScore = BigDecimal.valueOf(75 + random.nextInt(21));
        BigDecimal donenessScore = BigDecimal.valueOf(80 + random.nextInt(19));

        BigDecimal finalScore = colorScore.add(shapeScore).add(donenessScore)
                .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        int starLevel = Math.min(5, Math.max(1, finalScore.intValue() / 20));
        String riskLevel = finalScore.compareTo(BigDecimal.valueOf(60)) < 0 ? "high"
                : finalScore.compareTo(BigDecimal.valueOf(80)) < 0 ? "medium" : "low";

        AiEvaluateVO vo = new AiEvaluateVO();
        vo.setId(record.getId());
        vo.setFinalScore(finalScore);
        vo.setStarLevel(starLevel);
        vo.setRiskLevel(riskLevel);

        AiEvaluateVO.DimensionScores scores = new AiEvaluateVO.DimensionScores();
        scores.setColorScore(colorScore);
        scores.setShapeScore(shapeScore);
        scores.setDonenessScore(donenessScore);
        vo.setDimensionScores(scores);

        AiEvaluateVO.DimensionAnalysis analysis = new AiEvaluateVO.DimensionAnalysis();
        analysis.setColorAnalysis(colorScore.intValue() >= 85 ? "色泽鲜艳，食材颜色正常，无异常变色" : "色泽略有偏差，建议检查食材新鲜度");
        analysis.setShapeAnalysis(shapeScore.intValue() >= 85 ? "形态完整，切块均匀，摆盘规范" : "形态略有瑕疵，建议注意切割均匀度");
        analysis.setDonenessAnalysis(donenessScore.intValue() >= 85 ? "熟度适中，火候得当，口感良好" : "熟度略有偏差，建议调整烹饪时间");
        vo.setDimensionAnalysis(analysis);

        List<String> suggestions = new ArrayList<>();
        if (colorScore.intValue() < 85) suggestions.add("建议检查食材新鲜度，确保色泽正常");
        if (shapeScore.intValue() < 85) suggestions.add("可优化切割工艺，使食材形态更均匀");
        if (donenessScore.intValue() < 85) suggestions.add("可适当调整烹饪时间和温度");
        if (suggestions.isEmpty()) suggestions.add("各项指标良好，继续保持");
        vo.setSuggestions(suggestions);

        // 持久化AI评估结果
        record.setAiQualityScore(finalScore);
        record.setEvaluatedAt(LocalDateTime.now());
        if (STATUS_SAMPLED.equals(record.getStatus())) {
            record.setStatus(STATUS_EVALUATED);
        }
        try {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("finalScore", finalScore);
            resultMap.put("starLevel", starLevel);
            resultMap.put("riskLevel", riskLevel);
            resultMap.put("dimensionScores", scores);
            resultMap.put("dimensionAnalysis", analysis);
            resultMap.put("suggestions", suggestions);
            record.setAiAnalysisResult(objectMapper.writeValueAsString(resultMap));
        } catch (JsonProcessingException e) {
            log.warn("序列化AI评估结果失败", e);
        }
        sampleRecordMapper.updateById(record);

        logOperation(record.getId(), "ai_evaluate", "AI智能评估",
                "AI评估完成，评分：" + finalScore + "，星级：" + starLevel);

        auditLogService.log(AuditModule.SAMPLE_RECORD, AuditOperationType.PROCESS,
                record.getId(), record.getSampleNo(),
                "AI评估完成，评分：" + finalScore + "，星级：" + starLevel,
                beforeData, JSONUtil.toJsonStr(record));

        releaseOperationLockAfterWrite(record.getId(), requestLockToken);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.SAMPLE_RECORD,
            operationType = AuditOperationType.UPDATE,
            targetId = "#id",
            targetNo = "#entity.sampleNo",
            desc = "'编辑留样记录：' + #entity.sampleNo",
            mapper = SampleRecordMapper.class
    )
    public SampleRecordDetailVO updateRecord(Long id, SampleRecordUpdateDTO dto) {
        SampleRecord record = getRequiredRecordForUpdate(id);
        String requestLockToken = resolveCurrentRequestOperationLockToken();
        assertOperationLockAllowed(record, OPERATION_EDIT, requestLockToken);
        validateTaskCompletedForBusinessOperation(record, "编辑");
        if (!STATUS_SAMPLED.equals(record.getStatus())) {
            throw new RuntimeException("只有已留样状态的记录可以编辑");
        }

        if (dto.getSampleWeight() != null) record.setSampleWeight(dto.getSampleWeight());
        if (dto.getStorageLocation() != null) record.setStorageLocation(dto.getStorageLocation());
        if (dto.getStorageTemp() != null) record.setStorageTemp(dto.getStorageTemp());
        if (dto.getSampleImages() != null) {
            try {
                record.setSampleImages(objectMapper.writeValueAsString(dto.getSampleImages()));
            } catch (JsonProcessingException e) {
                log.warn("序列化留样照片失败", e);
            }
        }

        sampleRecordMapper.updateById(record);

        logOperation(record.getId(), "update", "编辑留样记录", "编辑留样记录信息");

        releaseOperationLockAfterWrite(record.getId(), requestLockToken);

        return buildDetailVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.SAMPLE_RECORD,
            operationType = AuditOperationType.STATUS_CHANGE,
            targetId = "#id",
            targetNo = "#entity.sampleNo",
            desc = "'作废留样记录并统一回写为已作废：' + #entity.sampleNo + '，原因：' + #reason",
            mapper = SampleRecordMapper.class
    )
    public SampleRecordDetailVO voidRecord(Long id, String reason) {
        SampleRecord record = getRequiredRecordForUpdate(id);
        String requestLockToken = resolveCurrentRequestOperationLockToken();
        assertOperationLockAllowed(record, OPERATION_VOID, requestLockToken);
        voidRecordInternal(record, reason, false);
        releaseOperationLockAfterWrite(record.getId(), requestLockToken);
        return buildDetailVO(record);
    }

    @Override
    public void deleteRecord(Long id) {
        throw new RuntimeException("仅支持作废或归档");
    }

    // ==================== 私有方法 ====================

    private SampleRecord getRequiredRecord(Long id) {
        SampleRecord record = sampleRecordMapper.selectById(id);
        if (record == null) {
            throw new RuntimeException("留样记录不存在");
        }
        validateRollbackIsolatedReadable(record);
        return record;
    }

    private SampleRecord getRequiredRecordForUpdate(Long id) {
        SampleRecord record = sampleRecordMapper.selectByIdForUpdate(id);
        if (record == null) {
            throw new RuntimeException("留样记录不存在");
        }
        validateRollbackIsolatedReadable(record);
        return record;
    }

    private LambdaQueryWrapper<SampleRecord> buildQueryWrapper(SampleRecordQueryDTO query, boolean allowRollbackIsolatedQuery) {
        LambdaQueryWrapper<SampleRecord> wrapper = new LambdaQueryWrapper<>();
        boolean showRollbackIsolated = allowRollbackIsolatedQuery
                && query != null
                && Boolean.TRUE.equals(query.getShowRollbackIsolated())
                && dataScopeService.isAdminUser();
        wrapper.eq(SampleRecord::getRollbackIsolated, showRollbackIsolated ? 1 : 0);
        if (query != null) {
            if (query.getStatus() != null && !query.getStatus().isBlank()) {
                if ("pending_disposal".equals(query.getStatus())) {
                    wrapper.in(SampleRecord::getStatus, STATUS_SAMPLED, STATUS_EVALUATED, STATUS_PENDING_DISPOSAL, STATUS_OVERDUE);
                } else {
                    wrapper.eq(SampleRecord::getStatus, query.getStatus());
                }
            }
            wrapper.ge(query.getSampleDate() != null, SampleRecord::getSampleDate, query.getSampleDate());
            wrapper.le(query.getSampleDateEnd() != null, SampleRecord::getSampleDate, query.getSampleDateEnd());
            wrapper.eq(query.getMealType() != null && !query.getMealType().isBlank(), SampleRecord::getMealType, query.getMealType());
            appendKeywordSearchCondition(wrapper, query.getMenuName());
            wrapper.eq(query.getOrgId() != null, SampleRecord::getOrgId, query.getOrgId());
            wrapper.in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), SampleRecord::getOrgId, query.getOrgIds());
            if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
                wrapper.isNull(SampleRecord::getId);
            }
        }
        return wrapper;
    }

    private void appendKeywordSearchCondition(LambdaQueryWrapper<SampleRecord> wrapper, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }

        String normalizedKeyword = keyword.trim();
        List<Long> matchedTaskIds = findTaskIdsByTaskNo(normalizedKeyword);
        wrapper.and(condition -> {
            condition.like(SampleRecord::getMenuName, normalizedKeyword)
                    .or()
                    .like(SampleRecord::getSampleNo, normalizedKeyword);
            if (!matchedTaskIds.isEmpty()) {
                condition.or().in(SampleRecord::getTaskId, matchedTaskIds);
            }
        });
    }

    private List<Long> findTaskIdsByTaskNo(String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT ct.id
                FROM cook_task ct
                WHERE ct.deleted = 0
                  AND ct.tenant_id = ?
                  AND ct.task_no LIKE ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(getCurrentTenantId());
        params.add("%" + keyword + "%");

        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (scope.isRestricted()) {
            if (scope.getOrgIds().isEmpty()) {
                return Collections.emptyList();
            }
            sql.append(" AND ct.org_id IN (")
                    .append(scope.getOrgIds().stream().map(id -> "?").collect(Collectors.joining(",")))
                    .append(")");
            params.addAll(scope.getOrgIds());
        }

        sql.append(" ORDER BY ct.id DESC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray(), Long.class);
    }

    private SampleRecordVO buildRecordVO(SampleRecord record,
                                         CookTaskSnapshot taskSnapshot,
                                         SampleDisposalSupplement disposalSupplement,
                                         SampleRecordOperationLock operationLock) {
        SampleRecordVO vo = new SampleRecordVO();
        vo.setId(record.getId());
        vo.setSampleNo(record.getSampleNo());
        vo.setTaskId(record.getTaskId());
        vo.setTaskNo(taskSnapshot != null ? taskSnapshot.getTaskNo() : null);
        vo.setRecordOriginType(resolveRecordOriginType(record));
        vo.setSourceLabel(resolveSourceLabel(record));
        vo.setDisposalSourceType(resolveDisposalSourceType(disposalSupplement));
        vo.setDisposalSourceLabel(resolveDisposalSourceLabel(disposalSupplement));
        vo.setTaskStatus(taskSnapshot != null ? taskSnapshot.getStatus() : null);
        vo.setMenuName(record.getMenuName());
        vo.setSampleDate(record.getSampleDate());
        vo.setMealType(record.getMealType());
        vo.setSampleWeight(record.getSampleWeight());
        vo.setAiQualityScore(record.getAiQualityScore());
        vo.setStorageLocation(record.getStorageLocation());
        vo.setStatus(record.getStatus());
        vo.setRollbackIsolated(isRollbackIsolated(record));
        vo.setOperationLock(buildOperationLockVO(operationLock, false));
        vo.setSampledAt(record.getSampledAt());
        vo.setDisposalDueAt(record.getDisposalDueAt());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    private SampleRecordDetailVO buildDetailVO(SampleRecord record) {
        CookTaskSnapshot taskSnapshot = null;
        if (record.getTaskId() != null) {
            try {
                taskSnapshot = loadCookTaskSnapshot(record.getTaskId(), false);
            } catch (Exception e) {
                log.warn("加载烹饪任务快照失败, taskId={}", record.getTaskId(), e);
            }
        }
        return buildDetailVO(record, taskSnapshot, loadDisposalSupplement(record.getId()));
    }

    private SampleRecordDetailVO buildDetailVO(SampleRecord record, CookTaskSnapshot taskSnapshot) {
        return buildDetailVO(record, taskSnapshot, loadDisposalSupplement(record.getId()));
    }

    private SampleRecordDetailVO buildDetailVO(SampleRecord record,
                                               CookTaskSnapshot taskSnapshot,
                                               SampleDisposalSupplement disposalSupplement) {
        SampleRecordDetailVO vo = new SampleRecordDetailVO();
        vo.setId(record.getId());
        vo.setSampleNo(record.getSampleNo());
        vo.setTaskId(record.getTaskId());
        vo.setTaskNo(taskSnapshot != null ? taskSnapshot.getTaskNo() : null);
        vo.setRecordOriginType(resolveRecordOriginType(record));
        vo.setSourceLabel(resolveSourceLabel(record));
        vo.setDisposalSourceType(resolveDisposalSourceType(disposalSupplement));
        vo.setDisposalSourceLabel(resolveDisposalSourceLabel(disposalSupplement));
        vo.setTaskStatus(taskSnapshot != null ? taskSnapshot.getStatus() : null);
        vo.setMenuId(record.getMenuId());
        vo.setMenuName(record.getMenuName());
        vo.setSampleDate(record.getSampleDate());
        vo.setMealType(record.getMealType());
        vo.setSampleWeight(record.getSampleWeight());
        vo.setAiQualityScore(record.getAiQualityScore());
        vo.setStorageLocation(record.getStorageLocation());
        vo.setStorageTemp(record.getStorageTemp());
        vo.setSampledBy(record.getSampledBy());
        vo.setSampledAt(record.getSampledAt());
        vo.setDisposalDueAt(record.getDisposalDueAt());
        vo.setDisposalBy(record.getDisposalBy());
        vo.setDisposalAt(record.getDisposalAt());
        vo.setDisposalRemark(record.getDisposalRemark());
        vo.setStatus(record.getStatus());
        vo.setOrgId(record.getOrgId());
        vo.setTenantId(record.getTenantId());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        vo.setVoidReason(record.getVoidReason());
        vo.setArchivedAt(record.getArchivedAt());
        vo.setEvaluatedAt(record.getEvaluatedAt());
        vo.setLockStatus(record.getLockStatus());
        vo.setTraceBatchId(record.getTraceBatchId());
        vo.setFoodSafetyLedgerNo(record.getFoodSafetyLedgerNo());
        vo.setSupplementReason(record.getSupplementReason());
        vo.setSupplementRemark(record.getSupplementRemark());
        vo.setDisposalSupplementScene(disposalSupplement != null ? disposalSupplement.getSupplementScene() : null);
        vo.setDisposalSupplementRemark(disposalSupplement != null ? disposalSupplement.getSupplementRemark() : null);
        vo.setDisposalSupplementedAt(disposalSupplement != null ? disposalSupplement.getCreatedAt() : null);
        vo.setDisposalSupplementedBy(disposalSupplement != null ? disposalSupplement.getCreatedBy() : null);
        vo.setRollbackIsolated(isRollbackIsolated(record));
        vo.setOperationLock(buildOperationLockVO(loadActiveOperationLock(record.getId()), false));
        vo.setRollbackIsolatedAt(record.getRollbackIsolatedAt());
        vo.setRollbackIsolationReason(record.getRollbackIsolationReason());

        // 人员名称解析
        vo.setSampledByName(resolveEmployeeName(record.getSampledBy()));
        vo.setDisposalByName(resolveEmployeeName(record.getDisposalBy()));
        vo.setDisposalSupplementedByName(disposalSupplement != null ? resolveEmployeeName(disposalSupplement.getCreatedBy()) : null);

        // 追溯链
        vo.setTraceChain(buildTraceChain(record));

        // 解析JSON字段
        vo.setSampleImages(parseJsonArray(record.getSampleImages()));
        vo.setDisposalImages(parseJsonArray(record.getDisposalImages()));
        vo.setAiAnalysisResult(parseJsonObject(record.getAiAnalysisResult()));

        return vo;
    }

    private String resolveSourceLabel(SampleRecord record) {
        return switch (resolveRecordOriginType(record)) {
            case ORIGIN_MANUAL_DAILY -> SOURCE_MANUAL_DAILY;
            case ORIGIN_MANUAL_HISTORY -> SOURCE_MANUAL_HISTORY;
            case ORIGIN_OFFLINE_DELAYED -> SOURCE_OFFLINE_DELAYED;
            case ORIGIN_SYSTEM_BACKFILL -> SOURCE_SYSTEM_BACKFILL;
            case ORIGIN_AUTO -> SOURCE_AUTO;
            default -> record.getCreatedBy() == null ? SOURCE_AUTO : SOURCE_MANUAL_DAILY;
        };
    }

    private String resolveRecordOriginType(SampleRecord record) {
        if (record == null) {
            return ORIGIN_AUTO;
        }
        if (record.getRecordOriginType() != null && !record.getRecordOriginType().isBlank()) {
            return record.getRecordOriginType();
        }
        return record.getCreatedBy() == null ? ORIGIN_AUTO : ORIGIN_MANUAL_DAILY;
    }

    private String resolveDisposalSourceType(SampleDisposalSupplement disposalSupplement) {
        return disposalSupplement != null
                ? DISPOSAL_SOURCE_MANUAL_EXCEPTION_SUPPLEMENT
                : DISPOSAL_SOURCE_SYSTEM_AUTO;
    }

    private String resolveDisposalSourceLabel(SampleDisposalSupplement disposalSupplement) {
        return disposalSupplement != null
                ? DISPOSAL_SOURCE_MANUAL_EXCEPTION_LABEL
                : DISPOSAL_SOURCE_SYSTEM_AUTO_LABEL;
    }

    private SampleDisposalSupplement loadDisposalSupplement(Long sampleRecordId) {
        if (sampleRecordId == null) {
            return null;
        }
        LambdaQueryWrapper<SampleDisposalSupplement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleDisposalSupplement::getSampleRecordId, sampleRecordId)
                .last("LIMIT 1");
        return sampleDisposalSupplementMapper.selectOne(wrapper);
    }

    private Map<Long, SampleDisposalSupplement> loadDisposalSupplementMap(Set<Long> sampleRecordIds) {
        if (sampleRecordIds == null || sampleRecordIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<SampleDisposalSupplement> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SampleDisposalSupplement::getSampleRecordId, sampleRecordIds);
        List<SampleDisposalSupplement> supplements = sampleDisposalSupplementMapper.selectList(wrapper);
        return supplements.stream().collect(Collectors.toMap(
                SampleDisposalSupplement::getSampleRecordId,
                supplement -> supplement,
                (left, right) -> left
        ));
    }

    private SampleRecordOperationLock loadActiveOperationLock(Long sampleRecordId) {
        if (sampleRecordId == null) {
            return null;
        }
        return loadActiveOperationLockMap(Set.of(sampleRecordId)).get(sampleRecordId);
    }

    private Map<Long, SampleRecordOperationLock> loadActiveOperationLockMap(Set<Long> sampleRecordIds) {
        if (sampleRecordIds == null || sampleRecordIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<SampleRecordOperationLock> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SampleRecordOperationLock::getSampleRecordId, sampleRecordIds)
                .eq(SampleRecordOperationLock::getDeleted, 0)
                .eq(SampleRecordOperationLock::getActive, OPERATION_LOCK_ACTIVE);
        List<SampleRecordOperationLock> locks = sampleRecordOperationLockMapper.selectList(wrapper);
        LocalDateTime now = LocalDateTime.now();
        return locks.stream()
                .filter(lock -> isOperationLockActive(lock, now))
                .collect(Collectors.toMap(
                        SampleRecordOperationLock::getSampleRecordId,
                        lock -> lock,
                        (left, right) -> left
                ));
    }

    private SampleOperationLockVO buildOperationLockVO(SampleRecordOperationLock lock, boolean includeToken) {
        SampleOperationLockVO vo = new SampleOperationLockVO();
        if (!isOperationLockActive(lock, LocalDateTime.now())) {
            vo.setLocked(false);
            vo.setOwnedByCurrentUser(false);
            return vo;
        }
        vo.setLocked(true);
        vo.setLockToken(includeToken ? lock.getLockToken() : null);
        vo.setOperationType(lock.getOperationType());
        vo.setOperationTypeLabel(resolveOperationLockTypeLabel(lock.getOperationType()));
        vo.setOperatorId(lock.getOperatorId());
        vo.setOperatorName(lock.getOperatorName());
        vo.setOwnedByCurrentUser(isCurrentUserLockOwner(lock));
        vo.setAcquiredAt(lock.getAcquiredAt());
        vo.setExpiresAt(lock.getExpiresAt());
        return vo;
    }

    private CookTaskSnapshot loadCookTaskSnapshot(Long taskId, boolean forUpdate) {
        if (taskId == null) {
            return null;
        }
        String sql = """
                SELECT ct.id,
                       ct.task_no,
                       ct.menu_id,
                       ct.menu_name,
                       ct.status,
                       ct.org_id,
                       ct.tenant_id,
                       ct.end_time,
                       rp.plan_date,
                       rp.meal_type
                FROM cook_task ct
                LEFT JOIN recipe_plan rp ON rp.id = ct.plan_id AND rp.deleted = 0
                WHERE ct.id = ?
                  AND ct.deleted = 0
                """ + (forUpdate ? " FOR UPDATE" : "");
        List<CookTaskSnapshot> snapshots = jdbcTemplate.query(sql, (rs, rowNum) -> {
            CookTaskSnapshot snapshot = new CookTaskSnapshot();
            snapshot.setId(rs.getLong("id"));
            snapshot.setTaskNo(rs.getString("task_no"));
            long menuId = rs.getLong("menu_id");
            snapshot.setMenuId(rs.wasNull() ? null : menuId);
            snapshot.setMenuName(rs.getString("menu_name"));
            snapshot.setStatus(rs.getString("status"));
            long orgId = rs.getLong("org_id");
            snapshot.setOrgId(rs.wasNull() ? null : orgId);
            long tenantId = rs.getLong("tenant_id");
            snapshot.setTenantId(rs.wasNull() ? null : tenantId);
            java.sql.Timestamp endTime = rs.getTimestamp("end_time");
            snapshot.setCompletedAt(endTime != null ? endTime.toLocalDateTime() : null);
            java.sql.Date planDate = rs.getDate("plan_date");
            snapshot.setSampleDate(planDate != null
                    ? planDate.toLocalDate()
                    : (snapshot.getCompletedAt() != null ? snapshot.getCompletedAt().toLocalDate() : LocalDate.now()));
            snapshot.setMealType(rs.getString("meal_type"));
            return snapshot;
        }, taskId);

        if (snapshots.isEmpty()) {
            throw new RuntimeException("关联烹饪任务不存在");
        }
        return snapshots.get(0);
    }

    private Map<Long, CookTaskSnapshot> loadCookTaskSnapshotMap(Set<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String placeholders = taskIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT ct.id,
                       ct.task_no,
                       ct.menu_id,
                       ct.menu_name,
                       ct.status,
                       ct.org_id,
                       ct.tenant_id,
                       ct.end_time,
                       rp.plan_date,
                       rp.meal_type
                FROM cook_task ct
                LEFT JOIN recipe_plan rp ON rp.id = ct.plan_id AND rp.deleted = 0
                WHERE ct.deleted = 0
                  AND ct.id IN (
                """ + placeholders + ")";

        List<CookTaskSnapshot> snapshots = jdbcTemplate.query(sql, (rs, rowNum) -> {
            CookTaskSnapshot snapshot = new CookTaskSnapshot();
            snapshot.setId(rs.getLong("id"));
            snapshot.setTaskNo(rs.getString("task_no"));
            long menuId = rs.getLong("menu_id");
            snapshot.setMenuId(rs.wasNull() ? null : menuId);
            snapshot.setMenuName(rs.getString("menu_name"));
            snapshot.setStatus(rs.getString("status"));
            long orgId = rs.getLong("org_id");
            snapshot.setOrgId(rs.wasNull() ? null : orgId);
            long tenantId = rs.getLong("tenant_id");
            snapshot.setTenantId(rs.wasNull() ? null : tenantId);
            java.sql.Timestamp endTime = rs.getTimestamp("end_time");
            snapshot.setCompletedAt(endTime != null ? endTime.toLocalDateTime() : null);
            java.sql.Date planDate = rs.getDate("plan_date");
            snapshot.setSampleDate(planDate != null
                    ? planDate.toLocalDate()
                    : (snapshot.getCompletedAt() != null ? snapshot.getCompletedAt().toLocalDate() : LocalDate.now()));
            snapshot.setMealType(rs.getString("meal_type"));
            return snapshot;
        }, taskIds.toArray());

        return snapshots.stream().collect(Collectors.toMap(CookTaskSnapshot::getId, snapshot -> snapshot, (left, right) -> left));
    }

    private void validateTaskAccessible(CookTaskSnapshot taskSnapshot) {
        if (taskSnapshot == null) {
            throw new RuntimeException("关联烹饪任务不存在");
        }
        if (!Objects.equals(getCurrentTenantId(), taskSnapshot.getTenantId())) {
            throw new RuntimeException("无权访问该烹饪任务");
        }
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (scope.isRestricted() && !scope.isAllowed(taskSnapshot.getOrgId())) {
            throw new RuntimeException("无权访问该烹饪任务");
        }
    }

    private void validateManualCreateTask(CookTaskSnapshot taskSnapshot) {
        if (!TASK_STATUS_COMPLETED.equals(taskSnapshot.getStatus()) && !TASK_STATUS_ARCHIVED.equals(taskSnapshot.getStatus())) {
            throw new RuntimeException("仅支持为当前业务日已完成或已归档的烹饪任务手工补录留样任务");
        }
        if (taskSnapshot.getSampleDate() == null || !LocalDate.now().equals(taskSnapshot.getSampleDate())) {
            throw new RuntimeException("普通角色仅支持当前业务日已完成烹饪任务的当日补录，不允许跨日补录");
        }
    }

    private void validateHistoricalCreateTask(CookTaskSnapshot taskSnapshot) {
        if (!TASK_STATUS_COMPLETED.equals(taskSnapshot.getStatus()) && !TASK_STATUS_ARCHIVED.equals(taskSnapshot.getStatus())) {
            throw new RuntimeException("仅允许对已完成或已归档的历史烹饪任务执行补录");
        }
        if (taskSnapshot.getSampleDate() == null || !taskSnapshot.getSampleDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("历史补录仅允许选择非当日的往期业务日期任务");
        }
    }

    private void ensureNoActiveSampleRecord(Long taskId, Long excludeRecordId) {
        if (taskId == null) {
            return;
        }
        LambdaQueryWrapper<SampleRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleRecord::getTaskId, taskId)
                .ne(SampleRecord::getStatus, STATUS_VOIDED);
        if (excludeRecordId != null) {
            wrapper.ne(SampleRecord::getId, excludeRecordId);
        }
        Long count = sampleRecordMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new RuntimeException("该烹饪任务已生成有效留样任务，不可重复新增");
        }
    }

    private SampleRecord createPendingSampleRecord(CookTaskSnapshot taskSnapshot,
                                                   String recordOriginType,
                                                   boolean autoCreated) {
        LocalDate sampleDate = taskSnapshot.getSampleDate() != null ? taskSnapshot.getSampleDate() : LocalDate.now();
        int seq = sampleRecordMapper.nextSeqForDate(sampleDate);
        String datePart = sampleDate.format(DATE_FMT);

        SampleRecord record = new SampleRecord();
        record.setSampleNo("SP-" + datePart + String.format("%03d", seq));
        record.setTaskId(taskSnapshot.getId());
        record.setMenuId(taskSnapshot.getMenuId());
        record.setMenuName(taskSnapshot.getMenuName());
        record.setSampleDate(sampleDate);
        record.setMealType(taskSnapshot.getMealType());
        record.setStatus(STATUS_PENDING_SAMPLE);
        record.setOrgId(taskSnapshot.getOrgId());
        record.setTenantId(taskSnapshot.getTenantId());
        record.setLockStatus(LOCK_NONE);
        record.setFoodSafetyLedgerNo("TA-" + datePart + String.format("%03d", seq));
        record.setRecordOriginType(recordOriginType);
        sampleRecordMapper.insert(record);

        if (autoCreated) {
            logOperation(record.getId(), ACTION_AUTO_CREATE, "系统自动生成",
                    "烹饪任务完成后系统自动生成待留样任务", null, null, "system");
        } else {
            logOperation(record.getId(), ACTION_MANUAL_CREATE, "手工新增",
                    "手工新增待留样任务", UserContext.getUserId(), UserContext.getRealName(), "web");
        }
        return record;
    }

    private SampleRecordDetailVO registerRecordInternal(SampleRecord record,
                                                        SampleRecordRegisterDTO dto,
                                                        CookTaskSnapshot taskSnapshot) {
        SampleRecordRegisterDTO registerDTO = dto != null ? dto : new SampleRecordRegisterDTO();
        String beforeData = JSONUtil.toJsonStr(record);

        if (registerDTO.getSampleWeight() != null) {
            record.setSampleWeight(registerDTO.getSampleWeight());
        }
        if (registerDTO.getStorageLocation() != null) {
            record.setStorageLocation(registerDTO.getStorageLocation());
        }
        if (registerDTO.getStorageTemp() != null) {
            record.setStorageTemp(registerDTO.getStorageTemp());
        }
        if (registerDTO.getSampledBy() != null) {
            record.setSampledBy(registerDTO.getSampledBy());
        }
        if (registerDTO.getSampleImages() != null) {
            try {
                record.setSampleImages(objectMapper.writeValueAsString(registerDTO.getSampleImages()));
            } catch (JsonProcessingException e) {
                log.warn("序列化留样照片失败", e);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        record.setSampledAt(now);
        record.setDisposalDueAt(now.plusHours(48));
        record.setStatus(STATUS_SAMPLED);
        if (record.getLockStatus() == null || record.getLockStatus().isBlank()) {
            record.setLockStatus(LOCK_NONE);
        }
        if (record.getSampleDate() == null && taskSnapshot != null && taskSnapshot.getSampleDate() != null) {
            record.setSampleDate(taskSnapshot.getSampleDate());
        }
        if ((record.getMealType() == null || record.getMealType().isBlank()) && taskSnapshot != null) {
            record.setMealType(taskSnapshot.getMealType());
        }
        sampleRecordMapper.updateById(record);

        logOperation(record.getId(), ACTION_REGISTER, "留样登记", "执行留样登记");
        auditLogService.log(AuditModule.SAMPLE_RECORD, AuditOperationType.STATUS_CHANGE,
                record.getId(), record.getSampleNo(), "执行留样登记：" + record.getSampleNo(),
                beforeData, JSONUtil.toJsonStr(record));
        return buildDetailVO(record, taskSnapshot);
    }

    private boolean hasRegisterPayload(SampleRecordCreateDTO dto) {
        if (dto == null) {
            return false;
        }
        return dto.getSampleWeight() != null
                || dto.getStorageTemp() != null
                || dto.getSampledBy() != null
                || (dto.getStorageLocation() != null && !dto.getStorageLocation().isBlank())
                || (dto.getSampleImages() != null && !dto.getSampleImages().isEmpty());
    }

    private void ensureHistorySupplementPrivilege() {
        if (hasHistorySupplementPrivilege()) {
            return;
        }
        throw new RuntimeException("仅系统管理员、运维管理员允许执行历史跨日补录");
    }

    private boolean hasHistorySupplementPrivilege() {
        if (dataScopeService.isAdminUser()) {
            return true;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
        }
        return hasExplicitPermission(userId, PERMISSION_HISTORY_SUPPLEMENT)
                || hasMatchedRole(userId, HISTORY_SUPPLEMENT_ADMIN_ROLE_CODES, HISTORY_SUPPLEMENT_ADMIN_ROLE_NAME_KEYWORDS);
    }

    private void ensureManualDisposalSupplementPrivilege() {
        if (hasManualDisposalSupplementPrivilege()) {
            return;
        }
        throw new RuntimeException("仅系统管理员、运维管理员、食堂超级管理员允许执行销样手工补录");
    }

    private boolean hasManualDisposalSupplementPrivilege() {
        if (dataScopeService.isAdminUser()) {
            return true;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
        }
        return hasExplicitPermission(userId, PERMISSION_MANUAL_DISPOSAL_SUPPLEMENT)
                || hasMatchedRole(userId, MANUAL_DISPOSAL_SUPPLEMENT_ADMIN_ROLE_CODES, MANUAL_DISPOSAL_SUPPLEMENT_ADMIN_ROLE_NAME_KEYWORDS);
    }

    private boolean hasExplicitPermission(Long userId, String permissionCode) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auth_user_role ur
                INNER JOIN auth_role r ON r.id = ur.role_id
                INNER JOIN auth_role_permission rp ON rp.role_id = r.id
                INNER JOIN auth_permission p ON p.id = rp.permission_id
                WHERE ur.user_id = ?
                  AND r.deleted = 0
                  AND r.status = 'active'
                  AND p.permission_code = ?
                  AND p.status = 'active'
                """,
                Long.class,
                userId,
                permissionCode
        );
        return count != null && count > 0;
    }

    private boolean hasMatchedRole(Long userId, Set<String> roleCodes, List<String> roleNameKeywords) {
        List<Map<String, Object>> roleRows = jdbcTemplate.queryForList(
                """
                SELECT r.role_code, r.role_name
                FROM auth_user_role ur
                INNER JOIN auth_role r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                  AND r.deleted = 0
                  AND r.status = 'active'
                """,
                userId
        );
        for (Map<String, Object> roleRow : roleRows) {
            String roleCode = roleRow.get("role_code") != null ? String.valueOf(roleRow.get("role_code")).trim().toUpperCase(Locale.ROOT) : "";
            if (roleCodes.contains(roleCode)) {
                return true;
            }
            String roleName = roleRow.get("role_name") != null ? String.valueOf(roleRow.get("role_name")).trim() : "";
            for (String keyword : roleNameKeywords) {
                if (!roleName.isBlank() && roleName.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validateManualDisposalSupplementPayload(SampleManualDisposalSupplementDTO dto) {
        if (dto == null) {
            throw new RuntimeException("销样手工补录参数不能为空");
        }
        if (dto.getSupplementScene() == null || dto.getSupplementScene().isBlank()) {
            throw new RuntimeException("请选择补录原因场景");
        }
        String scene = dto.getSupplementScene().trim();
        if (!MANUAL_DISPOSAL_ALLOWED_SCENES.contains(scene)) {
            throw new RuntimeException("补录原因场景不合法");
        }
        if (dto.getSupplementRemark() == null || dto.getSupplementRemark().isBlank()) {
            throw new RuntimeException("请填写补录备注");
        }
        if (dto.getSupplementRemark().trim().length() > 200) {
            throw new RuntimeException("补录备注不能超过200个字符");
        }
        if (dto.getDisposalRemark() != null && dto.getDisposalRemark().trim().length() > 300) {
            throw new RuntimeException("销样备注不能超过300个字符");
        }
        if (dto.getDisposalImages() == null || dto.getDisposalImages().isEmpty()) {
            throw new RuntimeException("销样照片不能为空");
        }
    }

    private void validateManualDisposalSupplementRecord(SampleRecord record) {
        if (record == null) {
            throw new RuntimeException("留样记录不存在");
        }
        if (record.getTaskId() == null) {
            throw new RuntimeException("销样手工补录必须强绑定有效留样任务");
        }
        if (STATUS_VOIDED.equals(record.getStatus()) || STATUS_ARCHIVED.equals(record.getStatus())) {
            throw new RuntimeException("已作废或已归档的留样记录不允许销样手工补录");
        }
        if (record.getSampledAt() == null || !MANUAL_DISPOSAL_ELIGIBLE_STATUSES.contains(record.getStatus())) {
            throw new RuntimeException("仅允许对已完成且待销样的有效留样记录执行销样手工补录");
        }
        if (record.getDisposalAt() != null || STATUS_DISPOSED.equals(record.getStatus())) {
            throw new RuntimeException("该留样已有正常未作废销样记录，不允许重复手工补录");
        }
    }

    private void ensureNoManualDisposalSupplementExists(Long sampleRecordId) {
        LambdaQueryWrapper<SampleDisposalSupplement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleDisposalSupplement::getSampleRecordId, sampleRecordId);
        Long count = sampleDisposalSupplementMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new RuntimeException("该留样已完成销样手工补录，请勿重复操作");
        }
    }

    private String normalizeOperationLockType(SampleOperationLockAcquireDTO dto) {
        if (dto == null || dto.getOperationType() == null || dto.getOperationType().isBlank()) {
            throw new RuntimeException("操作锁类型不能为空");
        }
        String operationType = dto.getOperationType().trim();
        if (!SUPPORTED_OPERATION_LOCK_TYPES.contains(operationType)) {
            throw new RuntimeException("不支持的操作锁类型");
        }
        return operationType;
    }

    private String normalizeOperationLockToken(String lockToken) {
        if (lockToken == null || lockToken.isBlank()) {
            throw new RuntimeException("操作锁令牌不能为空");
        }
        return lockToken.trim();
    }

    private void fillOperationLock(SampleRecordOperationLock lock,
                                   SampleRecord record,
                                   String operationType,
                                   String lockToken,
                                   LocalDateTime now) {
        lock.setSampleRecordId(record.getId());
        lock.setSampleNo(record.getSampleNo());
        lock.setLockToken(lockToken);
        lock.setOperationType(operationType);
        lock.setOperatorId(UserContext.getUserId());
        lock.setOperatorName(UserContext.getRealName());
        lock.setSourceTerminal(resolveSourceTerminal());
        lock.setActive(OPERATION_LOCK_ACTIVE);
        lock.setAcquiredAt(now);
        lock.setLastHeartbeatAt(now);
        lock.setExpiresAt(now.plusSeconds(OPERATION_LOCK_TIMEOUT_SECONDS));
        lock.setOrgId(record.getOrgId());
        lock.setTenantId(record.getTenantId() != null ? record.getTenantId() : getCurrentTenantId());
        lock.setDeleted(0);
    }

    private void deactivateOperationLock(SampleRecordOperationLock lock, boolean expired) {
        if (lock == null) {
            return;
        }
        lock.setActive(OPERATION_LOCK_INACTIVE);
        lock.setLockToken(null);
        lock.setOperationType(null);
        lock.setOperatorId(null);
        lock.setOperatorName(null);
        lock.setSourceTerminal(expired ? "timeout_release" : lock.getSourceTerminal());
        lock.setLastHeartbeatAt(LocalDateTime.now());
    }

    private boolean isOperationLockActive(SampleRecordOperationLock lock, LocalDateTime now) {
        return lock != null
                && Integer.valueOf(OPERATION_LOCK_ACTIVE).equals(lock.getActive())
                && lock.getExpiresAt() != null
                && lock.getExpiresAt().isAfter(now);
    }

    private boolean isCurrentUserLockOwner(SampleRecordOperationLock lock) {
        return lock != null && Objects.equals(lock.getOperatorId(), UserContext.getUserId());
    }

    private String resolveOperationLockTypeLabel(String operationType) {
        return OPERATION_LOCK_TYPE_LABELS.getOrDefault(operationType, "处理中");
    }

    private void validateOperationLockAcquire(SampleRecord record, String operationType) {
        switch (operationType) {
            case OPERATION_REGISTER -> {
                checkLock(record);
                if (!STATUS_PENDING_SAMPLE.equals(record.getStatus())) {
                    throw new RuntimeException("当前留样状态不允许进入留样登记");
                }
                if (record.getTaskId() == null) {
                    throw new RuntimeException("留样任务缺少关联烹饪任务，无法登记");
                }
                CookTaskSnapshot taskSnapshot = loadCookTaskSnapshot(record.getTaskId(), false);
                validateTaskAccessible(taskSnapshot);
                if (!TASK_STATUS_COMPLETED.equals(taskSnapshot.getStatus()) && !TASK_STATUS_ARCHIVED.equals(taskSnapshot.getStatus())) {
                    throw new RuntimeException("关联烹饪任务未完成或未归档，暂不可进入留样登记");
                }
            }
            case OPERATION_EDIT -> {
                checkLock(record);
                validateTaskCompletedForBusinessOperation(record, "编辑");
                if (!STATUS_SAMPLED.equals(record.getStatus())) {
                    throw new RuntimeException("当前留样状态不允许进入编辑");
                }
            }
            case OPERATION_DISPOSE -> {
                checkLock(record);
                validateTaskCompletedForBusinessOperation(record, "销样");
                if (!Set.of(STATUS_SAMPLED, STATUS_EVALUATED, STATUS_PENDING_DISPOSAL, STATUS_OVERDUE).contains(record.getStatus())) {
                    throw new RuntimeException("当前留样状态不允许执行销样");
                }
            }
            case OPERATION_MANUAL_DISPOSAL_SUPPLEMENT -> {
                ensureManualDisposalSupplementPrivilege();
                checkLock(record);
                validateTaskCompletedForBusinessOperation(record, "销样手工补录");
                validateManualDisposalSupplementRecord(record);
                ensureNoManualDisposalSupplementExists(record.getId());
            }
            case OPERATION_VOID -> {
                checkLock(record);
                ensureVoidAllowed(record);
            }
            case OPERATION_ARCHIVE -> {
                checkLock(record);
                validateTaskCompletedForBusinessOperation(record, "归档");
                if (!STATUS_DISPOSED.equals(record.getStatus())) {
                    throw new RuntimeException("当前留样状态不允许归档");
                }
            }
            case OPERATION_AI_EVALUATE -> {
                checkLock(record);
                validateTaskCompletedForBusinessOperation(record, "AI评估");
                if (!Set.of(STATUS_SAMPLED, STATUS_EVALUATED).contains(record.getStatus())) {
                    throw new RuntimeException("当前留样状态不允许执行AI评估");
                }
            }
            default -> {
                // no-op
            }
        }
    }

    private void ensureOwnedOperationLock(SampleRecordOperationLock lock, String lockToken, LocalDateTime now) {
        if (!isOperationLockActive(lock, now)) {
            throw new RuntimeException("当前操作锁已失效，请重新进入操作页面");
        }
        if (!Objects.equals(lockToken, lock.getLockToken())) {
            throw new RuntimeException("当前操作锁已失效，请重新进入操作页面");
        }
        if (!isCurrentUserLockOwner(lock)) {
            throw new RuntimeException("当前操作锁已被其他用户接管，请重新进入操作页面");
        }
    }

    private void assertOperationLockAllowed(SampleRecord record, String requestedOperationType, String requestLockToken) {
        SampleRecordOperationLock lock = sampleRecordOperationLockMapper.selectByRecordIdForUpdate(record.getId());
        LocalDateTime now = LocalDateTime.now();
        if (!isOperationLockActive(lock, now)) {
            if (lock != null && Integer.valueOf(OPERATION_LOCK_ACTIVE).equals(lock.getActive())) {
                deactivateOperationLock(lock, true);
                sampleRecordOperationLockMapper.updateById(lock);
            }
            return;
        }
        if (requestLockToken != null && requestLockToken.equals(lock.getLockToken()) && isCurrentUserLockOwner(lock)) {
            return;
        }
        if (isCurrentUserLockOwner(lock)) {
            throw new RuntimeException("当前操作锁已失效，请重新进入操作页面");
        }
        throw new RuntimeException(resolveOperationLockConflictMessage(lock.getOperationType(), requestedOperationType));
    }

    private void releaseOperationLockAfterWrite(Long recordId, String requestLockToken) {
        if (recordId == null || requestLockToken == null || requestLockToken.isBlank()) {
            return;
        }
        SampleRecordOperationLock lock = sampleRecordOperationLockMapper.selectByRecordIdForUpdate(recordId);
        if (lock == null || !Objects.equals(requestLockToken, lock.getLockToken())) {
            return;
        }
        deactivateOperationLock(lock, false);
        sampleRecordOperationLockMapper.updateById(lock);
    }

    private String resolveOperationLockConflictMessage(String existingOperationType, String requestedOperationType) {
        if (OPERATION_EDIT.equals(requestedOperationType)) {
            return OPERATION_EDIT.equals(existingOperationType)
                    ? "该留样正在被他人编辑，暂不可操作"
                    : "该留样正在进行" + resolveOperationLockTypeLabel(existingOperationType) + "，暂不可编辑";
        }
        if (OPERATION_DISPOSE.equals(requestedOperationType) || OPERATION_MANUAL_DISPOSAL_SUPPLEMENT.equals(requestedOperationType)) {
            if (OPERATION_MANUAL_DISPOSAL_SUPPLEMENT.equals(requestedOperationType)
                    && OPERATION_MANUAL_DISPOSAL_SUPPLEMENT.equals(existingOperationType)) {
                return "该留样已正在补录销样，请勿重复操作";
            }
            if (OPERATION_DISPOSE.equals(existingOperationType) || OPERATION_MANUAL_DISPOSAL_SUPPLEMENT.equals(existingOperationType)) {
                return "该销样任务正在处理中，请勿重复操作";
            }
            if (OPERATION_VOID.equals(existingOperationType)) {
                return "该留样/销样正在作废处理中，暂不可执行销样";
            }
            return "该留样正在进行" + resolveOperationLockTypeLabel(existingOperationType) + "，暂不可执行销样";
        }
        if (OPERATION_VOID.equals(requestedOperationType)) {
            if (OPERATION_VOID.equals(existingOperationType)) {
                return "该留样/销样正在作废处理中，请勿重复操作";
            }
            if (OPERATION_DISPOSE.equals(existingOperationType) || OPERATION_MANUAL_DISPOSAL_SUPPLEMENT.equals(existingOperationType)) {
                return "该销样任务正在处理中，暂不可发起作废";
            }
            return "该留样正在进行" + resolveOperationLockTypeLabel(existingOperationType) + "，暂不可作废";
        }
        if (OPERATION_ARCHIVE.equals(requestedOperationType)) {
            return "该留样正在进行" + resolveOperationLockTypeLabel(existingOperationType) + "，暂不可归档";
        }
        if (OPERATION_REGISTER.equals(requestedOperationType)) {
            return "该留样正在进行" + resolveOperationLockTypeLabel(existingOperationType) + "，暂不可登记";
        }
        if (OPERATION_AI_EVALUATE.equals(requestedOperationType)) {
            return "该留样正在进行" + resolveOperationLockTypeLabel(existingOperationType) + "，暂不可执行AI评估";
        }
        return "该留样正在进行" + resolveOperationLockTypeLabel(existingOperationType) + "，请稍后重试";
    }

    private String resolveCurrentRequestOperationLockToken() {
        HttpServletRequest request = resolveCurrentRequest();
        if (request == null) {
            return null;
        }
        String headerValue = request.getHeader(HEADER_OPERATION_LOCK_TOKEN);
        return headerValue != null && !headerValue.isBlank() ? headerValue.trim() : null;
    }

    private String resolveSourceTerminal() {
        HttpServletRequest request = resolveCurrentRequest();
        if (request == null) {
            return "web";
        }
        String terminal = request.getHeader("X-Source-Terminal");
        return terminal != null && !terminal.isBlank() ? terminal.trim() : "web";
    }

    private HttpServletRequest resolveCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private ReentrantLock acquireManualDisposalLock(Long recordId) {
        ReentrantLock lock = manualDisposalLocks.computeIfAbsent(recordId, key -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new RuntimeException("该留样已正在补录销样，请勿重复操作");
        }
        return lock;
    }

    private void releaseManualDisposalLock(Long recordId, ReentrantLock lock) {
        if (lock == null) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } finally {
            if (!lock.hasQueuedThreads()) {
                manualDisposalLocks.remove(recordId, lock);
            }
        }
    }

    private String resolveManualDisposalSceneLabel(String scene) {
        return MANUAL_DISPOSAL_SCENE_LABELS.getOrDefault(scene, scene);
    }

    private LocalDate normalizeHistoryBusinessDate(LocalDate businessDate) {
        if (businessDate == null) {
            throw new RuntimeException("请选择历史业务日期");
        }
        if (!businessDate.isBefore(LocalDate.now())) {
            throw new RuntimeException("历史补录仅允许选择非当日的往期业务日期");
        }
        return businessDate;
    }

    private void validateHistoryCreatePayload(SampleRecordHistoryCreateDTO dto) {
        if (!ORIGIN_MANUAL_HISTORY.equals(dto.getRecordOriginType()) && !ORIGIN_OFFLINE_DELAYED.equals(dto.getRecordOriginType())) {
            throw new RuntimeException("历史补录类型仅支持历史异常补录或离线迟传补录");
        }
        if (dto.getSampledBy() == null) {
            throw new RuntimeException("请选择留样人员");
        }
        if (dto.getSampleWeight() == null || dto.getSampleWeight().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("请输入合法的留样重量");
        }
        if (dto.getStorageLocation() == null || dto.getStorageLocation().isBlank()) {
            throw new RuntimeException("请输入存放位置");
        }
        if (dto.getSupplementReason() == null || dto.getSupplementReason().isBlank()) {
            throw new RuntimeException("历史补录原因不能为空");
        }
        if (dto.getSupplementRemark() == null || dto.getSupplementRemark().isBlank()) {
            throw new RuntimeException("历史补录备注不能为空");
        }
    }

    private LocalDateTime resolveHistoricalSampledAt(CookTaskSnapshot taskSnapshot) {
        if (taskSnapshot != null && taskSnapshot.getCompletedAt() != null) {
            return taskSnapshot.getCompletedAt();
        }
        if (taskSnapshot != null && taskSnapshot.getSampleDate() != null) {
            return taskSnapshot.getSampleDate().atTime(12, 0);
        }
        return LocalDateTime.now();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Long getCurrentTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析JSON数组失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    private Object parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            log.warn("解析JSON对象失败: {}", json, e);
            return null;
        }
    }

    // ==================== 新增方法 ====================

    @Override
    public String uploadImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_UPLOAD_TYPES.contains(contentType)) {
            throw new RuntimeException("不支持的文件类型");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new RuntimeException("文件大小不能超过50MB");
        }

        return fileStorageService.upload(file, "sample");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.SAMPLE_RECORD,
            operationType = AuditOperationType.STATUS_CHANGE,
            targetId = "#id",
            targetNo = "#entity.sampleNo",
            desc = "'归档留样记录：' + #entity.sampleNo",
            mapper = SampleRecordMapper.class
    )
    public SampleRecordDetailVO archiveRecord(Long id) {
        SampleRecord record = getRequiredRecordForUpdate(id);
        String requestLockToken = resolveCurrentRequestOperationLockToken();
        assertOperationLockAllowed(record, OPERATION_ARCHIVE, requestLockToken);
        checkLock(record);
        validateTaskCompletedForBusinessOperation(record, "归档");
        validateTransition(record.getStatus(), STATUS_ARCHIVED);
        record.setStatus(STATUS_ARCHIVED);
        record.setArchivedAt(LocalDateTime.now());
        sampleRecordMapper.updateById(record);

        logOperation(id, "archive", "归档", "归档留样记录");
        releaseOperationLockAfterWrite(record.getId(), requestLockToken);
        return buildDetailVO(record);
    }

    @Override
    @DataScope
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void exportRecords(SampleRecordQueryDTO query, HttpServletResponse response) {
        LocalDateTime snapshotAt = LocalDateTime.now();
        try {
            LambdaQueryWrapper<SampleRecord> wrapper = buildQueryWrapper(query, true);
            wrapper.orderByDesc(SampleRecord::getCreatedAt)
                    .orderByDesc(SampleRecord::getId);
            List<SampleRecord> records = sampleRecordMapper.selectList(wrapper);

            Map<Long, CookTaskSnapshot> taskSnapshotMap = loadCookTaskSnapshotMap(
                    records.stream()
                            .map(SampleRecord::getTaskId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet())
            );
            Map<Long, SampleDisposalSupplement> disposalSupplementMap = loadDisposalSupplementMap(
                    records.stream().map(SampleRecord::getId).collect(Collectors.toSet())
            );
            Set<Long> exportOrgIds = new HashSet<>();
            records.stream()
                    .map(SampleRecord::getOrgId)
                    .filter(Objects::nonNull)
                    .forEach(exportOrgIds::add);
            taskSnapshotMap.values().stream()
                    .map(CookTaskSnapshot::getOrgId)
                    .filter(Objects::nonNull)
                    .forEach(exportOrgIds::add);
            Map<Long, String> organizationNameMap = loadOrganizationNameMap(exportOrgIds);
            Map<Long, String> sampledByNameMap = loadSysEmployeeNameMap(
                    records.stream()
                            .map(SampleRecord::getSampledBy)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet())
            );
            Map<Long, String> disposalByNameMap = loadAuthUserNameMap(
                    records.stream()
                            .map(SampleRecord::getDisposalBy)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet())
            );

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet(SAMPLE_LEDGER_SHEET_NAME);
                CellStyle headerStyle = createSampleLedgerHeaderStyle(workbook);
                CellStyle contentStyle = createSampleLedgerContentStyle(workbook);
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < SAMPLE_LEDGER_EXPORT_HEADERS.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(SAMPLE_LEDGER_EXPORT_HEADERS[i]);
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(i, SAMPLE_LEDGER_EXPORT_COLUMN_WIDTHS[i] * 256);
                }

                int rowNum = 1;
                for (SampleRecord r : records) {
                    CookTaskSnapshot taskSnapshot = taskSnapshotMap.get(r.getTaskId());
                    SampleDisposalSupplement disposalSupplement = disposalSupplementMap.get(r.getId());
                    boolean hasDisposalLifecycle = hasDisposalLifecycle(r);
                    boolean hasActualDisposalRecord = hasActualDisposalRecord(r);
                    boolean disposalOverdue = isDisposalOverdue(r, snapshotAt);
                    Row row = sheet.createRow(rowNum++);
                    String[] values = {
                            defaultString(r.getSampleNo()),
                            resolveOrganizationDisplayName(r.getOrgId(), taskSnapshot, organizationNameMap),
                            defaultString(r.getMenuName()),
                            defaultString(taskSnapshot != null ? taskSnapshot.getTaskNo() : null),
                            formatExportDateTime(r.getSampledAt()),
                            defaultString(sampledByNameMap.get(r.getSampledBy())),
                            defaultString(resolveStatusLabel(r.getStatus())),
                            formatExportDateTime(r.getCreatedAt()),
                            defaultString(resolveSourceLabel(r)),
                            resolveVoidFlag(r),
                            defaultString(r.getVoidReason()),
                            defaultString(resolveSampleLedgerRemark(r, disposalSupplement)),
                            hasDisposalLifecycle ? defaultString(resolveDisposalTaskNo(r)) : "",
                            hasActualDisposalRecord ? formatExportDateTime(r.getDisposalAt()) : "",
                            hasActualDisposalRecord ? defaultString(disposalByNameMap.get(r.getDisposalBy())) : "",
                            hasDisposalLifecycle ? resolveDisposalStatusLabel(r, hasActualDisposalRecord, disposalOverdue) : "",
                            hasDisposalLifecycle ? (disposalOverdue ? "是" : "否") : "",
                            hasDisposalLifecycle && disposalOverdue ? resolveOverdueDurationText(r, snapshotAt) : "",
                            hasDisposalLifecycle ? resolveVoidFlag(r) : "",
                            hasActualDisposalRecord ? defaultString(resolveDisposalSourceLabel(disposalSupplement)) : "",
                            hasDisposalLifecycle ? resolveReminderStatusLabel(r, disposalOverdue) : ""
                    };
                    for (int i = 0; i < values.length; i++) {
                        Cell cell = row.createCell(i);
                        cell.setCellValue(values[i]);
                        cell.setCellStyle(contentStyle);
                    }
                }

                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setCharacterEncoding("utf-8");
                String fileName = URLEncoder.encode("留样台账_" + snapshotAt.format(EXPORT_FILE_NAME_FMT), StandardCharsets.UTF_8)
                        .replaceAll("\\+", "%20");
                response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
                workbook.write(response.getOutputStream());
                response.flushBuffer();
            }

            auditLogService.log(
                    AuditModule.SAMPLE_RECORD,
                    AuditOperationType.EXPORT,
                    null,
                    null,
                    buildSampleLedgerExportAuditDesc(query, records.size(), snapshotAt),
                    null,
                    null
            );
        } catch (Exception e) {
            auditLogService.log(
                    AuditModule.SAMPLE_RECORD,
                    AuditOperationType.EXPORT,
                    null,
                    null,
                    "导出留样台账失败",
                    null,
                    null,
                    "failed",
                    e.getMessage()
            );
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("导出留样台账失败", e);
        }
    }

    @Override
    public List<OperationLogVO> getOperationLogs(Long recordId) {
        getRequiredRecord(recordId);
        LambdaQueryWrapper<SampleOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOperationLog::getRecordId, recordId);
        wrapper.orderByDesc(SampleOperationLog::getCreatedAt);
        List<SampleOperationLog> logs = operationLogMapper.selectList(wrapper);
        return logs.stream().map(this::buildOperationLogVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchVoidRecords(List<Long> ids, String reason) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("请选择要作废的记录");
        }
        String normalizedReason = normalizeVoidReason(reason);
        List<Long> orderedIds = ids.stream().filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
        for (Long id : orderedIds) {
            SampleRecord record = getRequiredRecordForUpdate(id);
            assertOperationLockAllowed(record, OPERATION_VOID, null);
            if (STATUS_VOIDED.equals(record.getStatus())
                    || STATUS_ARCHIVED.equals(record.getStatus())) {
                continue;
            }
            String beforeData = JSONUtil.toJsonStr(record);
            String beforeStatus = record.getStatus();
            voidRecordInternal(record, normalizedReason, true);
            auditLogService.log(AuditModule.SAMPLE_RECORD, AuditOperationType.STATUS_CHANGE,
                    id, record.getSampleNo(),
                    "批量作废留样记录并回写主状态：" + resolveStatusLabel(beforeStatus) + " -> " + resolveStatusLabel(STATUS_VOIDED)
                            + "，记录：" + record.getSampleNo() + "，原因：" + normalizedReason,
                    beforeData, JSONUtil.toJsonStr(record));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchArchiveRecords(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("请选择要归档的记录");
        }
        List<Long> orderedIds = ids.stream().filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
        for (Long id : orderedIds) {
            SampleRecord record = getRequiredRecordForUpdate(id);
            assertOperationLockAllowed(record, OPERATION_ARCHIVE, null);
            validateTaskCompletedForBusinessOperation(record, "归档");
            if (!STATUS_DISPOSED.equals(record.getStatus())) {
                continue;
            }
            record.setStatus(STATUS_ARCHIVED);
            record.setArchivedAt(LocalDateTime.now());
            sampleRecordMapper.updateById(record);
            logOperation(id, "archive", "归档", "批量归档");
            auditLogService.log(AuditModule.SAMPLE_RECORD, AuditOperationType.STATUS_CHANGE,
                    id, record.getSampleNo(), "批量归档：" + record.getSampleNo(),
                    null, null);
        }
    }

    private OperationLogVO buildOperationLogVO(SampleOperationLog log) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(log.getId());
        vo.setRecordId(log.getRecordId());
        vo.setAction(log.getAction());
        vo.setActionName(log.getActionName());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(log.getOperatorName());
        vo.setContent(log.getContent());
        vo.setTerminal(log.getTerminal());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private void logOperation(Long recordId, String action, String actionName, String content) {
        logOperation(recordId, action, actionName, content, UserContext.getUserId(), UserContext.getRealName(), "web");
    }

    private void logOperation(Long recordId,
                              String action,
                              String actionName,
                              String content,
                              Long operatorId,
                              String operatorName,
                              String terminal) {
        SampleOperationLog logEntry = new SampleOperationLog();
        logEntry.setRecordId(recordId);
        logEntry.setAction(action);
        logEntry.setActionName(actionName);
        logEntry.setOperatorId(operatorId);
        logEntry.setOperatorName(operatorName);
        logEntry.setContent(content);
        logEntry.setTerminal(terminal);
        operationLogMapper.insert(logEntry);
    }

    private boolean isRollbackIsolated(SampleRecord record) {
        return record != null && Integer.valueOf(1).equals(record.getRollbackIsolated());
    }

    private Map<Long, String> loadOrganizationNameMap(Set<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = orgIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>();
        params.add(getCurrentTenantId());
        params.addAll(orgIds);
        String sql = """
                SELECT id, org_name
                FROM sys_organization
                WHERE deleted = 0
                  AND tenant_id = ?
                  AND id IN (
                """ + placeholders + ")";
        return jdbcTemplate.query(sql, rs -> {
            Map<Long, String> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("id"), rs.getString("org_name"));
            }
            return result;
        }, params.toArray());
    }

    private Map<Long, String> loadSysEmployeeNameMap(Set<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = employeeIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>();
        params.add(getCurrentTenantId());
        params.addAll(employeeIds);
        String sql = """
                SELECT id, real_name
                FROM sys_employee
                WHERE deleted = 0
                  AND tenant_id = ?
                  AND id IN (
                """ + placeholders + ")";
        return jdbcTemplate.query(sql, rs -> {
            Map<Long, String> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("id"), rs.getString("real_name"));
            }
            return result;
        }, params.toArray());
    }

    private Map<Long, String> loadAuthUserNameMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>();
        params.add(getCurrentTenantId());
        params.addAll(userIds);
        String sql = """
                SELECT id, real_name
                FROM auth_user
                WHERE deleted = 0
                  AND tenant_id = ?
                  AND id IN (
                """ + placeholders + ")";
        return jdbcTemplate.query(sql, rs -> {
            Map<Long, String> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("id"), rs.getString("real_name"));
            }
            return result;
        }, params.toArray());
    }

    private CellStyle createSampleLedgerHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createSampleLedgerContentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        style.setFont(font);
        return style;
    }

    private String resolveOrganizationDisplayName(Long orgId,
                                                  CookTaskSnapshot taskSnapshot,
                                                  Map<Long, String> organizationNameMap) {
        if (isPositiveId(orgId)) {
            return defaultString(organizationNameMap.get(orgId), String.valueOf(orgId));
        }
        Long taskOrgId = taskSnapshot != null ? taskSnapshot.getOrgId() : null;
        if (isPositiveId(taskOrgId)) {
            return defaultString(organizationNameMap.get(taskOrgId), String.valueOf(taskOrgId));
        }
        return "";
    }

    private boolean isPositiveId(Long value) {
        return value != null && value > 0;
    }

    private String defaultString(String value) {
        return defaultString(value, "");
    }

    private String defaultString(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String formatExportDateTime(LocalDateTime value) {
        return value != null ? value.format(EXPORT_DATETIME_FMT) : "";
    }

    private boolean hasDisposalLifecycle(SampleRecord record) {
        return record != null
                && (record.getSampledAt() != null
                || record.getDisposalDueAt() != null
                || record.getDisposalAt() != null
                || record.getDisposalBy() != null);
    }

    private boolean hasActualDisposalRecord(SampleRecord record) {
        return record != null && (record.getDisposalAt() != null || record.getDisposalBy() != null);
    }

    private String resolveDisposalTaskNo(SampleRecord record) {
        return record != null ? record.getSampleNo() : "";
    }

    private String resolveVoidFlag(SampleRecord record) {
        return record != null && STATUS_VOIDED.equals(record.getStatus()) ? "已作废" : "未作废";
    }

    private String resolveDisposalStatusLabel(SampleRecord record,
                                              boolean hasActualDisposalRecord,
                                              boolean disposalOverdue) {
        if (record == null) {
            return "";
        }
        if (hasActualDisposalRecord || STATUS_DISPOSED.equals(record.getStatus()) || STATUS_ARCHIVED.equals(record.getStatus())) {
            return "已销样";
        }
        if (disposalOverdue || STATUS_OVERDUE.equals(record.getStatus())) {
            return "超期未销";
        }
        return "待销样";
    }

    private boolean isDisposalOverdue(SampleRecord record, LocalDateTime snapshotAt) {
        if (record == null || record.getDisposalDueAt() == null) {
            return false;
        }
        LocalDateTime referenceTime = resolveDisposalOverdueReferenceTime(record, snapshotAt);
        return referenceTime != null && referenceTime.isAfter(record.getDisposalDueAt());
    }

    private LocalDateTime resolveDisposalOverdueReferenceTime(SampleRecord record, LocalDateTime snapshotAt) {
        if (record == null) {
            return null;
        }
        if (record.getDisposalAt() != null) {
            return record.getDisposalAt();
        }
        if (STATUS_VOIDED.equals(record.getStatus()) && record.getUpdatedAt() != null) {
            return record.getUpdatedAt();
        }
        if (STATUS_ARCHIVED.equals(record.getStatus()) && record.getArchivedAt() != null) {
            return record.getArchivedAt();
        }
        return snapshotAt;
    }

    private String resolveOverdueDurationText(SampleRecord record, LocalDateTime snapshotAt) {
        if (record == null || record.getDisposalDueAt() == null) {
            return "";
        }
        LocalDateTime referenceTime = resolveDisposalOverdueReferenceTime(record, snapshotAt);
        if (referenceTime == null || !referenceTime.isAfter(record.getDisposalDueAt())) {
            return "";
        }
        return formatDurationText(Duration.between(record.getDisposalDueAt(), referenceTime));
    }

    private String formatDurationText(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "";
        }
        long totalMinutes = duration.toMinutes();
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;
        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("天");
        }
        if (hours > 0) {
            builder.append(hours).append("小时");
        }
        if (minutes > 0 || builder.length() == 0) {
            builder.append(minutes).append("分钟");
        }
        return builder.toString();
    }

    private String resolveReminderStatusLabel(SampleRecord record, boolean disposalOverdue) {
        if (record == null) {
            return "";
        }
        if (STATUS_VOIDED.equals(record.getStatus()) || isRollbackIsolated(record)) {
            return "已关停";
        }
        if (record.getDisposalAt() != null || STATUS_DISPOSED.equals(record.getStatus()) || STATUS_ARCHIVED.equals(record.getStatus())) {
            return "已完成";
        }
        if (disposalOverdue || STATUS_OVERDUE.equals(record.getStatus())) {
            return "已超期";
        }
        return "待提醒";
    }

    private String resolveSampleLedgerRemark(SampleRecord record, SampleDisposalSupplement disposalSupplement) {
        if (record == null) {
            return "";
        }
        List<String> remarks = new ArrayList<>();
        appendLabeledRemark(remarks, "补录原因", record.getSupplementReason());
        appendLabeledRemark(remarks, "补录备注", record.getSupplementRemark());
        appendLabeledRemark(remarks, "销样备注", record.getDisposalRemark());
        if (disposalSupplement != null) {
            appendLabeledRemark(remarks, "销样补录场景", resolveManualDisposalSceneLabel(disposalSupplement.getSupplementScene()));
            appendLabeledRemark(remarks, "销样补录备注", disposalSupplement.getSupplementRemark());
        }
        appendLabeledRemark(remarks, "回滚隔离原因", record.getRollbackIsolationReason());
        return String.join("；", remarks);
    }

    private void appendLabeledRemark(List<String> remarks, String label, String value) {
        if (remarks == null || value == null || value.isBlank()) {
            return;
        }
        remarks.add(label + "：" + value.trim());
    }

    private String buildSampleLedgerExportAuditDesc(SampleRecordQueryDTO query, int count, LocalDateTime snapshotAt) {
        List<String> filters = new ArrayList<>();
        if (query != null) {
            if (query.getSampleDate() != null || query.getSampleDateEnd() != null) {
                filters.add("日期="
                        + (query.getSampleDate() != null ? query.getSampleDate() : "")
                        + "~"
                        + (query.getSampleDateEnd() != null ? query.getSampleDateEnd() : ""));
            }
            if (query.getStatus() != null && !query.getStatus().isBlank()) {
                filters.add("状态=" + resolveStatusLabel(query.getStatus()));
            }
            if (query.getMealType() != null && !query.getMealType().isBlank()) {
                filters.add("餐次=" + query.getMealType());
            }
            if (query.getMenuName() != null && !query.getMenuName().isBlank()) {
                filters.add("关键字=" + query.getMenuName().trim());
            }
            if (query.getOrgId() != null) {
                filters.add("组织ID=" + query.getOrgId());
            } else {
                filters.add("组织范围=当前数据权限");
            }
            if (Boolean.TRUE.equals(query.getShowRollbackIsolated()) && dataScopeService.isAdminUser()) {
                filters.add("台账视角=回滚作废台账");
            }
        }
        if (filters.isEmpty()) {
            filters.add("全部可见数据");
        }
        return "导出留样台账，共" + count + "条；快照时间：" + formatExportDateTime(snapshotAt) + "；筛选条件：" + String.join("，", filters);
    }

    private void validateRollbackIsolatedReadable(SampleRecord record) {
        if (record == null || !isRollbackIsolated(record)) {
            return;
        }
        if (!dataScopeService.isAdminUser()) {
            throw new RuntimeException("留样记录不存在");
        }
    }

    private void validateTaskCompletedForBusinessOperation(SampleRecord record, String actionName) {
        if (record == null || record.getTaskId() == null) {
            return;
        }
        CookTaskSnapshot taskSnapshot = loadCookTaskSnapshot(record.getTaskId(), false);
        validateTaskAccessible(taskSnapshot);
        if (!TASK_STATUS_COMPLETED.equals(taskSnapshot.getStatus()) && !TASK_STATUS_ARCHIVED.equals(taskSnapshot.getStatus())) {
            throw new RuntimeException("关联烹饪任务未完成或未归档，禁止继续执行" + actionName + "操作");
        }
    }

    // ==================== 状态机校验 ====================

    private void validateTransition(String current, String target) {
        Set<String> allowed = STATE_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(target)) {
            throw new RuntimeException("状态不允许从 [" + current + "] 转换到 [" + target + "]");
        }
    }

    private void voidRecordInternal(SampleRecord record, String reason, boolean batch) {
        String normalizedReason = normalizeVoidReason(reason);
        checkLock(record);
        String beforeStatus = record.getStatus();
        ensureVoidAllowed(record);

        record.setStatus(STATUS_VOIDED);
        record.setVoidReason(normalizedReason);
        record.setDisposalRemark(appendVoidLinkageRemark(record.getDisposalRemark(), beforeStatus, normalizedReason));
        sampleRecordMapper.updateById(record);

        String operationContent = buildVoidOperationContent(beforeStatus, normalizedReason, batch);
        logOperation(record.getId(), "void", "作废", operationContent);
    }

    private String normalizeVoidReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("作废原因不能为空");
        }
        return reason.trim();
    }

    private void ensureVoidAllowed(SampleRecord record) {
        if (record == null) {
            throw new RuntimeException("留样记录不存在");
        }
        if (STATUS_VOIDED.equals(record.getStatus())) {
            throw new RuntimeException("该留样记录已作废，不可再次作废");
        }
        if (STATUS_ARCHIVED.equals(record.getStatus())) {
            throw new RuntimeException("该留样记录已归档，不允许作废后颠覆历史台账");
        }
        validateTransition(record.getStatus(), STATUS_VOIDED);
    }

    private String appendVoidLinkageRemark(String existingRemark, String beforeStatus, String reason) {
        List<String> parts = new ArrayList<>();
        if (existingRemark != null && !existingRemark.isBlank()) {
            parts.add(existingRemark.trim());
        }
        parts.add("[状态回写] " + resolveStatusLabel(beforeStatus) + " -> " + resolveStatusLabel(STATUS_VOIDED));
        parts.add("[作废联动销样] " + reason);
        parts.add("[提醒关停] 已终止销样倒计时、到期预警、待办与风险统计");
        return String.join("；", parts);
    }

    private String buildVoidOperationContent(String beforeStatus, String reason, boolean batch) {
        return (batch ? "批量作废" : "作废")
                + "留样记录，状态回写："
                + resolveStatusLabel(beforeStatus)
                + " -> "
                + resolveStatusLabel(STATUS_VOIDED)
                + "；已同步联动销样链路为已作废，并终止倒计时/到期预警/待办统计；作废原因："
                + reason;
    }

    private String resolveStatusLabel(String status) {
        if (STATUS_PENDING_SAMPLE.equals(status)) {
            return "待留样";
        }
        if (STATUS_SAMPLED.equals(status)) {
            return "已留样";
        }
        if (STATUS_EVALUATED.equals(status)) {
            return "已评估";
        }
        if (STATUS_PENDING_DISPOSAL.equals(status)) {
            return "待销样";
        }
        if (STATUS_DISPOSED.equals(status)) {
            return "已销样";
        }
        if (STATUS_OVERDUE.equals(status)) {
            return "超期未销";
        }
        if (STATUS_VOIDED.equals(status)) {
            return "已作废";
        }
        if (STATUS_ARCHIVED.equals(status)) {
            return "已归档";
        }
        return status;
    }

    // ==================== 监管锁定 ====================

    private void checkLock(SampleRecord record) {
        String lock = record.getLockStatus();
        if (LOCK_INVESTIGATION.equals(lock) || LOCK_ACCIDENT.equals(lock)) {
            throw new RuntimeException("该记录已被监管锁定（" + lock + "），禁止操作");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.SAMPLE_RECORD,
            operationType = AuditOperationType.STATUS_CHANGE,
            targetId = "#id",
            targetNo = "#entity.sampleNo",
            desc = "'监管锁定留样记录：' + #lockStatus",
            mapper = SampleRecordMapper.class
    )
    public SampleRecordDetailVO lockRecord(Long id, String lockStatus) {
        if (!LOCK_INVESTIGATION.equals(lockStatus) && !LOCK_ACCIDENT.equals(lockStatus)) {
            throw new RuntimeException("锁定类型只支持 investigation 或 accident");
        }
        SampleRecord record = getRequiredRecord(id);
        record.setLockStatus(lockStatus);
        sampleRecordMapper.updateById(record);
        logOperation(id, "lock", "监管锁定", "锁定类型：" + lockStatus);
        return buildDetailVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.SAMPLE_RECORD,
            operationType = AuditOperationType.STATUS_CHANGE,
            targetId = "#id",
            targetNo = "#entity.sampleNo",
            desc = "'解除监管锁定：' + #entity.sampleNo",
            mapper = SampleRecordMapper.class
    )
    public SampleRecordDetailVO unlockRecord(Long id) {
        SampleRecord record = getRequiredRecord(id);
        record.setLockStatus(LOCK_NONE);
        sampleRecordMapper.updateById(record);
        logOperation(id, "unlock", "解除锁定", "解除监管锁定");
        return buildDetailVO(record);
    }

    // ==================== 人员名称解析 ====================

    private String resolveEmployeeName(Long id) {
        if (id == null) return null;
        try {
            // disposalBy 存的是 auth_user.id，sampledBy 存的是 sys_employee.id
            // 优先查 auth_user，查不到再查 sys_employee
            List<String> names = jdbcTemplate.queryForList(
                    "SELECT real_name FROM auth_user WHERE id = ? AND deleted = 0 LIMIT 1",
                    String.class, id);
            if (!names.isEmpty()) return names.get(0);
            names = jdbcTemplate.queryForList(
                    "SELECT real_name FROM sys_employee WHERE id = ? AND deleted = 0 LIMIT 1",
                    String.class, id);
            return names.isEmpty() ? null : names.get(0);
        } catch (Exception e) {
            log.warn("查询人员姓名失败, id={}", id, e);
            return null;
        }
    }

    // ==================== 追溯链 ====================

    private TraceChainVO buildTraceChain(SampleRecord record) {
        TraceChainVO chain = new TraceChainVO();
        chain.setSampleNo(record.getSampleNo());
        chain.setSampledByName(resolveEmployeeName(record.getSampledBy()));
        chain.setDisposalByName(resolveEmployeeName(record.getDisposalBy()));

        if (record.getTaskId() != null) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT ct.task_no, ct.assigned_chef_name, ct.plan_id " +
                        "FROM cook_task ct WHERE ct.id = ? AND ct.deleted = 0 LIMIT 1",
                        record.getTaskId());
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.get(0);
                    chain.setTaskNo((String) row.get("task_no"));
                    chain.setChefName((String) row.get("assigned_chef_name"));
                    Object planId = row.get("plan_id");
                    if (planId != null) {
                        List<String> codes = jdbcTemplate.queryForList(
                                "SELECT plan_code FROM recipe_plan WHERE id = ? AND deleted = 0 LIMIT 1",
                                String.class, planId);
                        if (!codes.isEmpty()) chain.setPlanCode(codes.get(0));
                    }
                }
            } catch (Exception e) {
                log.warn("构建追溯链失败, recordId={}", record.getId(), e);
            }
        }
        return chain;
    }

    private static class CookTaskSnapshot {
        private Long id;
        private String taskNo;
        private Long menuId;
        private String menuName;
        private LocalDate sampleDate;
        private String mealType;
        private String status;
        private LocalDateTime completedAt;
        private Long orgId;
        private Long tenantId;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTaskNo() {
            return taskNo;
        }

        public void setTaskNo(String taskNo) {
            this.taskNo = taskNo;
        }

        public Long getMenuId() {
            return menuId;
        }

        public void setMenuId(Long menuId) {
            this.menuId = menuId;
        }

        public String getMenuName() {
            return menuName;
        }

        public void setMenuName(String menuName) {
            this.menuName = menuName;
        }

        public LocalDate getSampleDate() {
            return sampleDate;
        }

        public void setSampleDate(LocalDate sampleDate) {
            this.sampleDate = sampleDate;
        }

        public String getMealType() {
            return mealType;
        }

        public void setMealType(String mealType) {
            this.mealType = mealType;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getCompletedAt() {
            return completedAt;
        }

        public void setCompletedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
        }

        public Long getOrgId() {
            return orgId;
        }

        public void setOrgId(Long orgId) {
            this.orgId = orgId;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }
    }
}
