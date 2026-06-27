package com.xykj.health.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xykj.health.entity.HealthCheckRecord;
import com.xykj.health.mapper.HealthCheckRecordMapper;
import com.xykj.health.vo.HealthCheckLinkageVersionVO;
import com.xykj.health.vo.HealthCheckMovementLogVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 晨检任务生成后的异动联动服务。
 *
 * 保持原 00:00 基础任务生成逻辑不变，仅在其后补充当日实时联动：
 * 1. 员工/账号/组织状态变更后自动补发、恢复、剔除任务
 * 2. 同步维护当日“应检标记”“任务归属”“正式/临时替班”状态
 * 3. 为详情页提供异动留痕
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthTaskLinkageService {

    public static final String DUTY_TYPE_FORMAL = "formal";
    public static final String DUTY_TYPE_SUBSTITUTE = "substitute";
    public static final String LINKAGE_STATUS_ACTIVE = "active";
    public static final String LINKAGE_STATUS_EXCLUDED = "excluded";

    private static final String STATUS_PENDING_CHECK = "pending_check";
    private static final String STATUS_CHECKING = "checking";
    private static final String STATUS_COMPLETED_NORMAL = "completed_normal";
    private static final String STATUS_COMPLETED_ABNORMAL = "completed_abnormal";
    private static final String STATUS_ARCHIVED = "archived";
    private static final String CHECK_RESULT_PENDING = "pending";
    private static final DateTimeFormatter CHECK_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbcTemplate;
    private final HealthCheckRecordMapper healthCheckRecordMapper;

    private final AtomicBoolean reconciling = new AtomicBoolean(false);
    private volatile long lastAttemptAt = 0L;
    private volatile long lastSuccessAt = 0L;

    @PostConstruct
    public void ensureSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS health_check_task_linkage (
              id BIGINT NOT NULL AUTO_INCREMENT COMMENT '联动状态ID',
              record_id BIGINT DEFAULT NULL COMMENT '关联晨检记录ID',
              employee_id BIGINT NOT NULL COMMENT '员工ID',
              employee_name VARCHAR(50) NOT NULL COMMENT '员工姓名（冗余）',
              check_date DATE NOT NULL COMMENT '晨检日期',
              should_check TINYINT NOT NULL DEFAULT 1 COMMENT '是否应检：1=应检，0=剔除',
              duty_type VARCHAR(20) NOT NULL DEFAULT 'formal' COMMENT '任务标签：formal=正式在岗，substitute=临时替班',
              linkage_status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '联动状态：active=有效，excluded=已剔除',
              base_org_id BIGINT DEFAULT NULL COMMENT '任务基准组织ID',
              base_org_name VARCHAR(100) DEFAULT NULL COMMENT '任务基准组织名称',
              base_position VARCHAR(100) DEFAULT NULL COMMENT '任务基准岗位',
              current_org_id BIGINT DEFAULT NULL COMMENT '当前归属组织ID',
              current_org_name VARCHAR(100) DEFAULT NULL COMMENT '当前归属组织名称',
              current_position VARCHAR(100) DEFAULT NULL COMMENT '当前归属岗位',
              reason_code VARCHAR(50) DEFAULT NULL COMMENT '最近一次联动原因编码',
              reason_desc VARCHAR(255) DEFAULT NULL COMMENT '最近一次联动原因说明',
              last_signature VARCHAR(255) DEFAULT NULL COMMENT '最近一次处理签名',
              last_event_time DATETIME DEFAULT NULL COMMENT '最近一次联动时间',
              tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
              PRIMARY KEY (id),
              UNIQUE KEY uk_hctl_employee_date (employee_id, check_date),
              KEY idx_hctl_record (record_id),
              KEY idx_hctl_should_check (should_check),
              KEY idx_hctl_current_org (current_org_id),
              KEY idx_hctl_updated_at (updated_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='晨检任务联动状态表'
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS health_check_task_linkage_log (
              id BIGINT NOT NULL AUTO_INCREMENT COMMENT '联动日志ID',
              linkage_id BIGINT DEFAULT NULL COMMENT '联动状态ID',
              record_id BIGINT DEFAULT NULL COMMENT '晨检记录ID',
              employee_id BIGINT NOT NULL COMMENT '员工ID',
              employee_name VARCHAR(50) NOT NULL COMMENT '员工姓名（冗余）',
              check_date DATE NOT NULL COMMENT '晨检日期',
              event_type VARCHAR(50) NOT NULL COMMENT '事件类型',
              event_name VARCHAR(100) NOT NULL COMMENT '事件名称',
              reason_code VARCHAR(50) DEFAULT NULL COMMENT '原因编码',
              reason_desc VARCHAR(255) DEFAULT NULL COMMENT '原因说明',
              before_snapshot JSON DEFAULT NULL COMMENT '变更前快照',
              after_snapshot JSON DEFAULT NULL COMMENT '变更后快照',
              event_signature VARCHAR(255) DEFAULT NULL COMMENT '事件签名',
              org_id BIGINT DEFAULT NULL COMMENT '当前组织ID',
              tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
              PRIMARY KEY (id),
              KEY idx_hctll_record (record_id),
              KEY idx_hctll_employee_date (employee_id, check_date),
              KEY idx_hctll_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='晨检任务联动留痕表'
            """);
    }

    /**
     * 高频查询入口使用的轻量节流。
     */
    public void reconcileTodayTasksIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastAttemptAt < 2000L) {
            return;
        }
        reconcileTodayTasks();
    }

    /**
     * 3 秒级联动重算。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reconcileTodayTasks() {
        if (!reconciling.compareAndSet(false, true)) {
            return;
        }
        lastAttemptAt = System.currentTimeMillis();
        try {
            LocalDate today = LocalDate.now();
            Map<Long, EmployeeSnapshot> snapshotMap = loadEmployeeSnapshots();
            Map<Long, HealthCheckRecord> recordMap = loadEffectiveTodayRecordMap(today);
            Map<Long, TaskLinkageState> stateMap = loadStateMap(today, null);

            Set<Long> employeeIds = new java.util.HashSet<>();
            employeeIds.addAll(snapshotMap.keySet());
            employeeIds.addAll(recordMap.keySet());
            employeeIds.addAll(stateMap.keySet());

            for (Long employeeId : employeeIds) {
                processEmployee(today, employeeId, snapshotMap.get(employeeId), recordMap.get(employeeId), stateMap.get(employeeId));
            }
            lastSuccessAt = System.currentTimeMillis();
        } finally {
            reconciling.set(false);
        }
    }

    public Map<Long, TaskLinkageState> loadStateMap(LocalDate checkDate, Collection<Long> employeeIds) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, record_id, employee_id, employee_name, check_date, should_check, duty_type, linkage_status,
                   base_org_id, base_org_name, base_position, current_org_id, current_org_name, current_position,
                   reason_code, reason_desc, last_signature, last_event_time, tenant_id, updated_at
            FROM health_check_task_linkage
            WHERE check_date = ?
            """);
        List<Object> args = new ArrayList<>();
        args.add(checkDate);
        if (employeeIds != null && !employeeIds.isEmpty()) {
            sql.append(" AND employee_id IN (")
                    .append(String.join(",", Collections.nCopies(employeeIds.size(), "?")))
                    .append(")");
            args.addAll(employeeIds);
        }

        List<TaskLinkageState> rows = jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) -> {
            TaskLinkageState state = new TaskLinkageState();
            state.setId(rs.getLong("id"));
            long recordId = rs.getLong("record_id");
            state.setRecordId(rs.wasNull() ? null : recordId);
            state.setEmployeeId(rs.getLong("employee_id"));
            state.setEmployeeName(rs.getString("employee_name"));
            state.setCheckDate(rs.getObject("check_date", LocalDate.class));
            state.setShouldCheck(rs.getInt("should_check") == 1);
            state.setDutyType(rs.getString("duty_type"));
            state.setLinkageStatus(rs.getString("linkage_status"));
            long baseOrgId = rs.getLong("base_org_id");
            state.setBaseOrgId(rs.wasNull() ? null : baseOrgId);
            state.setBaseOrgName(rs.getString("base_org_name"));
            state.setBasePosition(rs.getString("base_position"));
            long currentOrgId = rs.getLong("current_org_id");
            state.setCurrentOrgId(rs.wasNull() ? null : currentOrgId);
            state.setCurrentOrgName(rs.getString("current_org_name"));
            state.setCurrentPosition(rs.getString("current_position"));
            state.setReasonCode(rs.getString("reason_code"));
            state.setReasonDesc(rs.getString("reason_desc"));
            state.setLastSignature(rs.getString("last_signature"));
            Timestamp lastEventTime = rs.getTimestamp("last_event_time");
            state.setLastEventTime(lastEventTime != null ? lastEventTime.toLocalDateTime() : null);
            state.setTenantId(rs.getLong("tenant_id"));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            state.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);
            return state;
        });
        return rows.stream().collect(Collectors.toMap(TaskLinkageState::getEmployeeId, item -> item, (left, right) -> right));
    }

    public TaskLinkageState loadState(LocalDate checkDate, Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        return loadStateMap(checkDate, Collections.singleton(employeeId)).get(employeeId);
    }

    public List<HealthCheckMovementLogVO> loadMovementLogs(Long recordId) {
        if (recordId == null) {
            return Collections.emptyList();
        }
        String sql = """
            SELECT id, event_type, event_name, reason_code, reason_desc, before_snapshot, after_snapshot, created_at
            FROM health_check_task_linkage_log
            WHERE record_id = ?
            ORDER BY created_at DESC, id DESC
            """;
        return jdbcTemplate.query(sql, new Object[]{recordId}, (rs, rowNum) -> {
            HealthCheckMovementLogVO vo = new HealthCheckMovementLogVO();
            vo.setId(rs.getLong("id"));
            vo.setEventType(rs.getString("event_type"));
            vo.setEventName(rs.getString("event_name"));
            vo.setReasonCode(rs.getString("reason_code"));
            vo.setReasonDesc(rs.getString("reason_desc"));
            vo.setBeforeSummary(buildSummary(rs.getString("before_snapshot")));
            vo.setAfterSummary(buildSummary(rs.getString("after_snapshot")));
            Timestamp createdAt = rs.getTimestamp("created_at");
            vo.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
            return vo;
        });
    }

    public HealthCheckLinkageVersionVO getVersion() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
            SELECT MAX(updated_at) AS updated_at
            FROM health_check_task_linkage
            WHERE check_date = CURDATE()
            """);
        LocalDateTime time = toLocalDateTime(row.get("updated_at"));
        if (time == null) {
            return new HealthCheckLinkageVersionVO("none", null);
        }
        return new HealthCheckLinkageVersionVO(time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")), time);
    }

    public String resolveDutyTypeName(String dutyType) {
        return DUTY_TYPE_SUBSTITUTE.equals(dutyType) ? "临时替班" : "正式在岗";
    }

    public boolean shouldCheck(TaskLinkageState state) {
        return state == null || Boolean.TRUE.equals(state.getShouldCheck());
    }

    public boolean isPendingLike(String status) {
        return STATUS_PENDING_CHECK.equals(status) || STATUS_CHECKING.equals(status);
    }

    public boolean isCompletedLike(HealthCheckRecord record) {
        if (record == null) {
            return false;
        }
        return STATUS_COMPLETED_NORMAL.equals(record.getStatus())
                || STATUS_COMPLETED_ABNORMAL.equals(record.getStatus())
                || STATUS_ARCHIVED.equals(record.getStatus());
    }

    private void processEmployee(LocalDate today,
                                 Long employeeId,
                                 EmployeeSnapshot snapshot,
                                 HealthCheckRecord record,
                                 TaskLinkageState state) {
        boolean eligible = isEligible(snapshot);
        Long tenantId = snapshot != null && snapshot.getTenantId() != null
                ? snapshot.getTenantId()
                : record != null && record.getTenantId() != null ? record.getTenantId() : 1L;
        String employeeName = snapshot != null && notBlank(snapshot.getEmployeeName())
                ? snapshot.getEmployeeName()
                : state != null && notBlank(state.getEmployeeName()) ? state.getEmployeeName()
                : record != null ? record.getEmployeeName() : "未知员工";

        Long baseOrgId = state != null ? state.getBaseOrgId() : snapshot != null ? snapshot.getOrgId() : record != null ? record.getOrgId() : null;
        String baseOrgName = state != null && notBlank(state.getBaseOrgName())
                ? state.getBaseOrgName()
                : snapshot != null ? snapshot.getOrgName() : null;
        String basePosition = state != null && state.getBasePosition() != null
                ? state.getBasePosition()
                : snapshot != null ? snapshot.getPosition() : null;

        Long currentOrgId = snapshot != null ? snapshot.getOrgId() : state != null ? state.getCurrentOrgId() : record != null ? record.getOrgId() : null;
        String currentOrgName = snapshot != null ? snapshot.getOrgName() : state != null ? state.getCurrentOrgName() : null;
        String currentPosition = snapshot != null ? snapshot.getPosition() : state != null ? state.getCurrentPosition() : null;
        String dutyType = isSubstitute(baseOrgId, basePosition, currentOrgId, currentPosition) ? DUTY_TYPE_SUBSTITUTE : DUTY_TYPE_FORMAL;

        if (eligible && record == null) {
            record = createPendingRecord(snapshot, today);
        }

        String reasonCode = eligible ? "eligible" : resolveExcludeReasonCode(snapshot);
        String reasonDesc = eligible ? resolveEligibleReason(record, state, snapshot, dutyType) : resolveExcludeReasonDesc(snapshot);
        String signature = buildSignature(eligible, snapshot, record, baseOrgId, basePosition, currentOrgId, currentPosition, dutyType, reasonCode);

        if (!eligible && record == null && state == null) {
            return;
        }
        if (state != null
                && Objects.equals(state.getLastSignature(), signature)
                && Objects.equals(state.getRecordId(), record != null ? record.getId() : null)) {
            return;
        }

        Map<String, Object> beforeSnapshot = buildStateSnapshot(state, record);
        upsertState(
                record != null ? record.getId() : null,
                employeeId,
                employeeName,
                today,
                eligible,
                dutyType,
                eligible ? LINKAGE_STATUS_ACTIVE : LINKAGE_STATUS_EXCLUDED,
                baseOrgId,
                baseOrgName,
                basePosition,
                currentOrgId,
                currentOrgName,
                currentPosition,
                reasonCode,
                reasonDesc,
                signature,
                tenantId
        );

        TaskLinkageState latest = loadState(today, employeeId);
        Map<String, Object> afterSnapshot = buildStateSnapshot(latest, record);
        if (needsLog(state, eligible, dutyType, currentOrgId, currentPosition, latest)) {
            insertLog(
                    latest != null ? latest.getId() : null,
                    record != null ? record.getId() : null,
                    employeeId,
                    employeeName,
                    today,
                    resolveEventType(state, eligible, dutyType, currentOrgId, currentPosition, latest),
                    resolveEventName(state, eligible, dutyType, currentOrgId, currentPosition, latest),
                    reasonCode,
                    reasonDesc,
                    beforeSnapshot,
                    afterSnapshot,
                    signature,
                    currentOrgId,
                    tenantId
            );
        }
    }

    private boolean needsLog(TaskLinkageState previous,
                             boolean eligible,
                             String dutyType,
                             Long currentOrgId,
                             String currentPosition,
                             TaskLinkageState latest) {
        if (previous == null) {
            return true;
        }
        if (!Objects.equals(previous.getShouldCheck(), eligible)) {
            return true;
        }
        if (!Objects.equals(previous.getDutyType(), dutyType)) {
            return true;
        }
        if (!Objects.equals(previous.getCurrentOrgId(), currentOrgId)) {
            return true;
        }
        if (!Objects.equals(normalize(previous.getCurrentPosition()), normalize(currentPosition))) {
            return true;
        }
        if (latest != null && !Objects.equals(previous.getRecordId(), latest.getRecordId())) {
            return true;
        }
        return false;
    }

    private String resolveEventType(TaskLinkageState previous,
                                    boolean eligible,
                                    String dutyType,
                                    Long currentOrgId,
                                    String currentPosition,
                                    TaskLinkageState latest) {
        if (previous == null && latest != null && latest.getRecordId() != null) {
            return "task_initialized";
        }
        if (previous != null && !Boolean.TRUE.equals(previous.getShouldCheck()) && eligible) {
            return "task_restored";
        }
        if (!eligible) {
            return "task_excluded";
        }
        if (previous == null || previous.getRecordId() == null && latest != null && latest.getRecordId() != null) {
            return "task_supplemented";
        }
        if (!Objects.equals(previous.getDutyType(), dutyType)) {
            return DUTY_TYPE_SUBSTITUTE.equals(dutyType) ? "duty_switched_substitute" : "duty_restored_formal";
        }
        if (!Objects.equals(previous.getCurrentOrgId(), currentOrgId)
                || !Objects.equals(normalize(previous.getCurrentPosition()), normalize(currentPosition))) {
            return "task_reassigned";
        }
        return "task_refreshed";
    }

    private String resolveEventName(TaskLinkageState previous,
                                    boolean eligible,
                                    String dutyType,
                                    Long currentOrgId,
                                    String currentPosition,
                                    TaskLinkageState latest) {
        String eventType = resolveEventType(previous, eligible, dutyType, currentOrgId, currentPosition, latest);
        return switch (eventType) {
            case "task_initialized" -> "初始化当日联动状态";
            case "task_restored" -> "恢复原晨检任务";
            case "task_excluded" -> "剔除当日应检任务";
            case "task_supplemented" -> "补发晨检任务";
            case "duty_switched_substitute" -> "切换为临时替班";
            case "duty_restored_formal" -> "恢复正式在岗";
            case "task_reassigned" -> "刷新任务归属";
            default -> "刷新联动状态";
        };
    }

    private String resolveEligibleReason(HealthCheckRecord record,
                                         TaskLinkageState state,
                                         EmployeeSnapshot snapshot,
                                         String dutyType) {
        if (state == null && record != null) {
            return "已纳入当日应检范围";
        }
        if (state != null && !Boolean.TRUE.equals(state.getShouldCheck())) {
            return "员工恢复为当日应检状态，恢复原任务";
        }
        if (DUTY_TYPE_SUBSTITUTE.equals(dutyType)) {
            return "员工当日组织/岗位发生变更，按临时替班归属展示";
        }
        if (state != null && snapshot != null
                && (!Objects.equals(state.getCurrentOrgId(), snapshot.getOrgId())
                || !Objects.equals(normalize(state.getCurrentPosition()), normalize(snapshot.getPosition())))) {
            return "员工当日归属已同步为最新组织/岗位";
        }
        return "员工当前处于当日应检范围";
    }

    private String resolveExcludeReasonCode(EmployeeSnapshot snapshot) {
        if (snapshot == null) {
            return "employee_missing";
        }
        if (!"active".equals(snapshot.getEmployeeStatus())) {
            return "employee_inactive";
        }
        if (!"active".equals(snapshot.getAccountStatus())) {
            return "account_inactive";
        }
        if (snapshot.getOrgStatus() != null && !"active".equals(snapshot.getOrgStatus())) {
            return "org_inactive";
        }
        return "excluded";
    }

    private String resolveExcludeReasonDesc(EmployeeSnapshot snapshot) {
        if (snapshot == null) {
            return "员工已删除或无法获取最新人员档案，移出当日应检范围";
        }
        if (!"active".equals(snapshot.getEmployeeStatus())) {
            return "员工状态已不在岗，移出当日应检范围";
        }
        if (!"active".equals(snapshot.getAccountStatus())) {
            return "员工账号已停用或锁定，移出当日应检范围";
        }
        if (snapshot.getOrgStatus() != null && !"active".equals(snapshot.getOrgStatus())) {
            return "所属组织已停用，移出当日应检范围";
        }
        return "员工不再满足当日应检条件";
    }

    private String buildSignature(boolean eligible,
                                  EmployeeSnapshot snapshot,
                                  HealthCheckRecord record,
                                  Long baseOrgId,
                                  String basePosition,
                                  Long currentOrgId,
                                  String currentPosition,
                                  String dutyType,
                                  String reasonCode) {
        return String.join("|",
                eligible ? "1" : "0",
                safe(snapshot != null ? snapshot.getEmployeeStatus() : null),
                safe(snapshot != null ? snapshot.getAccountStatus() : null),
                safe(snapshot != null ? snapshot.getOrgStatus() : null),
                safe(baseOrgId),
                safe(basePosition),
                safe(currentOrgId),
                safe(currentPosition),
                safe(dutyType),
                safe(record != null ? record.getId() : null),
                safe(record != null ? record.getStatus() : null),
                safe(reasonCode));
    }

    private boolean isEligible(EmployeeSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        if (!"active".equals(snapshot.getEmployeeStatus())) {
            return false;
        }
        if (!"active".equals(snapshot.getAccountStatus())) {
            return false;
        }
        return snapshot.getOrgStatus() == null || "active".equals(snapshot.getOrgStatus());
    }

    private boolean isSubstitute(Long baseOrgId,
                                 String basePosition,
                                 Long currentOrgId,
                                 String currentPosition) {
        return !Objects.equals(baseOrgId, currentOrgId)
                || !Objects.equals(normalize(basePosition), normalize(currentPosition));
    }

    private HealthCheckRecord createPendingRecord(EmployeeSnapshot snapshot, LocalDate today) {
        if (snapshot == null) {
            throw new IllegalStateException("无法为不存在的员工补发晨检任务");
        }
        LambdaQueryWrapper<HealthCheckRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthCheckRecord::getEmployeeId, snapshot.getEmployeeId())
                .eq(HealthCheckRecord::getCheckDate, today)
                .orderByDesc(HealthCheckRecord::getId)
                .last("LIMIT 1");
        HealthCheckRecord existing = healthCheckRecordMapper.selectOne(wrapper);
        if (existing != null) {
            return existing;
        }

        HealthCheckRecord record = new HealthCheckRecord();
        int seq = healthCheckRecordMapper.nextSeqForDate(today);
        record.setCheckNo("HC-" + today.format(CHECK_NO_FMT) + String.format("%03d", seq));
        record.setEmployeeId(snapshot.getEmployeeId());
        record.setEmployeeName(snapshot.getEmployeeName());
        record.setCheckDate(today);
        // 待检记录不应设置 checkTime，只有实际执行晨检时才记录时间

        record.setCheckResult(CHECK_RESULT_PENDING);
        record.setStatus(STATUS_PENDING_CHECK);
        record.setOrgId(snapshot.getOrgId() != null ? snapshot.getOrgId() : 1L);
        record.setTenantId(snapshot.getTenantId() != null ? snapshot.getTenantId() : 1L);
        healthCheckRecordMapper.insert(record);
        return record;
    }

    private Map<Long, EmployeeSnapshot> loadEmployeeSnapshots() {
        String sql = """
            SELECT e.id,
                   e.real_name,
                   e.org_id,
                   e.position,
                   e.status AS employee_status,
                   e.tenant_id,
                   u.status AS account_status,
                   o.org_name,
                   o.status AS org_status
            FROM sys_employee e
            LEFT JOIN auth_user u ON u.id = e.user_id AND u.deleted = 0
            LEFT JOIN sys_organization o ON o.id = e.org_id AND o.deleted = 0
            WHERE e.deleted = 0
            """;
        List<EmployeeSnapshot> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            EmployeeSnapshot snapshot = new EmployeeSnapshot();
            snapshot.setEmployeeId(rs.getLong("id"));
            snapshot.setEmployeeName(rs.getString("real_name"));
            long orgId = rs.getLong("org_id");
            snapshot.setOrgId(rs.wasNull() ? null : orgId);
            snapshot.setPosition(rs.getString("position"));
            snapshot.setEmployeeStatus(rs.getString("employee_status"));
            long tenantId = rs.getLong("tenant_id");
            snapshot.setTenantId(rs.wasNull() ? 1L : tenantId);
            snapshot.setAccountStatus(rs.getString("account_status"));
            snapshot.setOrgName(rs.getString("org_name"));
            snapshot.setOrgStatus(rs.getString("org_status"));
            return snapshot;
        });
        return rows.stream().collect(Collectors.toMap(EmployeeSnapshot::getEmployeeId, item -> item, (left, right) -> right));
    }

    private Map<Long, HealthCheckRecord> loadEffectiveTodayRecordMap(LocalDate today) {
        LambdaQueryWrapper<HealthCheckRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthCheckRecord::getCheckDate, today)
                .orderByDesc(HealthCheckRecord::getId);
        List<HealthCheckRecord> records = healthCheckRecordMapper.selectList(wrapper);
        Map<Long, List<HealthCheckRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(HealthCheckRecord::getEmployeeId));
        Map<Long, HealthCheckRecord> result = new HashMap<>();
        for (Map.Entry<Long, List<HealthCheckRecord>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), pickEffectiveRecord(entry.getValue()));
        }
        return result;
    }

    private HealthCheckRecord pickEffectiveRecord(List<HealthCheckRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        return records.stream()
                .sorted(Comparator
                        .comparing((HealthCheckRecord item) -> isPendingLike(item.getStatus()) ? 0 : 1)
                        .thenComparing(HealthCheckRecord::getId, Comparator.reverseOrder()))
                .findFirst()
                .orElse(records.get(0));
    }

    private void upsertState(Long recordId,
                             Long employeeId,
                             String employeeName,
                             LocalDate checkDate,
                             boolean shouldCheck,
                             String dutyType,
                             String linkageStatus,
                             Long baseOrgId,
                             String baseOrgName,
                             String basePosition,
                             Long currentOrgId,
                             String currentOrgName,
                             String currentPosition,
                             String reasonCode,
                             String reasonDesc,
                             String lastSignature,
                             Long tenantId) {
        jdbcTemplate.update("""
            INSERT INTO health_check_task_linkage
            (record_id, employee_id, employee_name, check_date, should_check, duty_type, linkage_status,
             base_org_id, base_org_name, base_position, current_org_id, current_org_name, current_position,
             reason_code, reason_desc, last_signature, last_event_time, tenant_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
              record_id = VALUES(record_id),
              employee_name = VALUES(employee_name),
              should_check = VALUES(should_check),
              duty_type = VALUES(duty_type),
              linkage_status = VALUES(linkage_status),
              base_org_id = COALESCE(health_check_task_linkage.base_org_id, VALUES(base_org_id)),
              base_org_name = COALESCE(health_check_task_linkage.base_org_name, VALUES(base_org_name)),
              base_position = COALESCE(health_check_task_linkage.base_position, VALUES(base_position)),
              current_org_id = VALUES(current_org_id),
              current_org_name = VALUES(current_org_name),
              current_position = VALUES(current_position),
              reason_code = VALUES(reason_code),
              reason_desc = VALUES(reason_desc),
              last_signature = VALUES(last_signature),
              last_event_time = NOW(),
              tenant_id = VALUES(tenant_id),
              updated_at = NOW()
            """,
                recordId,
                employeeId,
                employeeName,
                checkDate,
                shouldCheck ? 1 : 0,
                dutyType,
                linkageStatus,
                baseOrgId,
                baseOrgName,
                basePosition,
                currentOrgId,
                currentOrgName,
                currentPosition,
                reasonCode,
                reasonDesc,
                lastSignature,
                tenantId != null ? tenantId : 1L
        );
    }

    private void insertLog(Long linkageId,
                           Long recordId,
                           Long employeeId,
                           String employeeName,
                           LocalDate checkDate,
                           String eventType,
                           String eventName,
                           String reasonCode,
                           String reasonDesc,
                           Map<String, Object> beforeSnapshot,
                           Map<String, Object> afterSnapshot,
                           String signature,
                           Long orgId,
                           Long tenantId) {
        jdbcTemplate.update("""
            INSERT INTO health_check_task_linkage_log
            (linkage_id, record_id, employee_id, employee_name, check_date, event_type, event_name,
             reason_code, reason_desc, before_snapshot, after_snapshot, event_signature, org_id, tenant_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?, NOW())
            """,
                linkageId,
                recordId,
                employeeId,
                employeeName,
                checkDate,
                eventType,
                eventName,
                reasonCode,
                reasonDesc,
                JSONUtil.toJsonStr(beforeSnapshot),
                JSONUtil.toJsonStr(afterSnapshot),
                signature,
                orgId,
                tenantId != null ? tenantId : 1L
        );
    }

    private Map<String, Object> buildStateSnapshot(TaskLinkageState state, HealthCheckRecord record) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (state != null) {
            snapshot.put("shouldCheck", state.getShouldCheck());
            snapshot.put("dutyType", state.getDutyType());
            snapshot.put("baseOrgName", state.getBaseOrgName());
            snapshot.put("basePosition", state.getBasePosition());
            snapshot.put("currentOrgName", state.getCurrentOrgName());
            snapshot.put("currentPosition", state.getCurrentPosition());
            snapshot.put("reasonDesc", state.getReasonDesc());
        }
        if (record != null) {
            snapshot.put("recordId", record.getId());
            snapshot.put("recordStatus", record.getStatus());
            snapshot.put("recordCheckNo", record.getCheckNo());
        }
        return snapshot;
    }

    private String buildSummary(String json) {
        if (!notBlank(json)) {
            return "-";
        }
        try {
            Map<String, Object> map = JSONUtil.toBean(json, Map.class);
            List<String> parts = new ArrayList<>();
            Object shouldCheck = map.get("shouldCheck");
            if (shouldCheck != null) {
                parts.add(Boolean.TRUE.equals(shouldCheck) || "true".equals(String.valueOf(shouldCheck)) ? "应检" : "已剔除");
            }
            Object dutyType = map.get("dutyType");
            if (dutyType != null) {
                parts.add(DUTY_TYPE_SUBSTITUTE.equals(String.valueOf(dutyType)) ? "临时替班" : "正式在岗");
            }
            if (map.get("currentOrgName") != null) {
                parts.add("组织:" + map.get("currentOrgName"));
            }
            if (map.get("currentPosition") != null) {
                parts.add("岗位:" + map.get("currentPosition"));
            }
            if (map.get("recordStatus") != null) {
                parts.add("任务状态:" + map.get("recordStatus"));
            }
            if (map.get("reasonDesc") != null) {
                parts.add("说明:" + map.get("reasonDesc"));
            }
            return parts.isEmpty() ? "-" : String.join("；", parts);
        } catch (Exception ex) {
            return "-";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime()).toLocalDateTime();
        }
        return null;
    }

    @Data
    public static class TaskLinkageState {
        private Long id;
        private Long recordId;
        private Long employeeId;
        private String employeeName;
        private LocalDate checkDate;
        private Boolean shouldCheck;
        private String dutyType;
        private String linkageStatus;
        private Long baseOrgId;
        private String baseOrgName;
        private String basePosition;
        private Long currentOrgId;
        private String currentOrgName;
        private String currentPosition;
        private String reasonCode;
        private String reasonDesc;
        private String lastSignature;
        private LocalDateTime lastEventTime;
        private Long tenantId;
        private LocalDateTime updatedAt;
    }

    @Data
    private static class EmployeeSnapshot {
        private Long employeeId;
        private String employeeName;
        private Long orgId;
        private String orgName;
        private String position;
        private String employeeStatus;
        private String accountStatus;
        private String orgStatus;
        private Long tenantId;
    }
}
