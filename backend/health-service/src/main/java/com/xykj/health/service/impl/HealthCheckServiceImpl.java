package com.xykj.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.health.dto.AiCheckDTO;
import com.xykj.health.dto.FaceRecognizeDTO;
import com.xykj.health.dto.HealthCheckCreateDTO;
import com.xykj.health.dto.HealthCheckQueryDTO;
import com.xykj.health.dto.HealthCheckUpdateDTO;
import com.xykj.health.entity.HealthCertificate;
import com.xykj.health.entity.HealthCheckRecord;
import com.xykj.health.mapper.HealthCertificateMapper;
import com.xykj.health.mapper.HealthCheckRecordMapper;
import com.xykj.health.service.HealthCheckService;
import com.xykj.health.service.HealthFaceFeatureService;
import com.xykj.health.service.HealthTaskLinkageService;
import com.xykj.health.vo.AiCheckResultVO;
import com.xykj.health.vo.FaceRecognizeVO;
import com.xykj.health.vo.HealthCheckDetailVO;
import com.xykj.health.vo.HealthCheckLinkageVersionVO;
import com.xykj.health.vo.HealthCheckRecordVO;
import com.xykj.health.vo.HealthDashboardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 晨检记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckServiceImpl implements HealthCheckService {

    // 状态常量
    private static final String STATUS_PENDING_CHECK = "pending_check";
    private static final String STATUS_CHECKING = "checking";
    private static final String STATUS_COMPLETED_NORMAL = "completed_normal";
    private static final String STATUS_COMPLETED_ABNORMAL = "completed_abnormal";
    private static final String STATUS_ARCHIVED = "archived";

    // 体温常量
    private static final BigDecimal TEMP_WARNING_THRESHOLD = new BigDecimal("37.3");
    private static final BigDecimal TEMP_LOW_THRESHOLD = new BigDecimal("36.0");
    private static final String TEMP_LOW = "low";
    private static final String TEMP_NORMAL = "normal";
    private static final String TEMP_HIGH = "high";
    private static final int CERTIFICATE_WARNING_DAYS = 30;

    // 人脸匹配阈值
    private static final BigDecimal FACE_MATCH_THRESHOLD = new BigDecimal("80");

    private static final DateTimeFormatter CHECK_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HealthCheckRecordMapper healthCheckRecordMapper;
    private final HealthCertificateMapper healthCertificateMapper;
    private final JdbcTemplate jdbcTemplate;
    private final HealthFaceFeatureService faceFeatureService;
    private final AuditLogService auditLogService;
    private final DataScopeService dataScopeService;
    private final HealthTaskLinkageService healthTaskLinkageService;

    @Override
    @DataScope
    public HealthDashboardVO getDashboard(HealthCheckQueryDTO query) {
        healthTaskLinkageService.reconcileTodayTasksIfNeeded();
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<HealthCheckRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthCheckRecord::getCheckDate, today);
        List<HealthCheckRecord> todayRecords = healthCheckRecordMapper.selectList(wrapper);
        Map<Long, HealthTaskLinkageService.TaskLinkageState> stateMap =
                healthTaskLinkageService.loadStateMap(today, extractEmployeeIds(todayRecords));

        long pendingCount = todayRecords.stream()
                .filter(record -> shouldIncludeForTodayStats(record, stateMap.get(record.getEmployeeId()), query))
                .filter(r -> STATUS_PENDING_CHECK.equals(r.getStatus()) || STATUS_CHECKING.equals(r.getStatus()))
                .count();
        long completedCount = todayRecords.stream()
                .filter(record -> shouldIncludeForTodayStats(record, stateMap.get(record.getEmployeeId()), query))
                .filter(this::isCompletedForStats)
                .count();
        long normalCount = todayRecords.stream()
                .filter(record -> shouldIncludeForTodayStats(record, stateMap.get(record.getEmployeeId()), query))
                .filter(this::isPassForStats)
                .count();
        long abnormalCount = todayRecords.stream()
                .filter(record -> shouldIncludeForTodayStats(record, stateMap.get(record.getEmployeeId()), query))
                .filter(this::isFailForStats)
                .count();

        long certificateExpiringCount = queryCertificateCount("expiring", query);
        long certificateExpiredCount = queryCertificateCount("expired", query);

        long totalChecked = pendingCount + completedCount;
        BigDecimal passRate = completedCount > 0
                ? BigDecimal.valueOf(normalCount * 100.0 / completedCount).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return HealthDashboardVO.builder()
                .pendingCount(pendingCount)
                .completedCount(completedCount)
                .normalCount(normalCount)
                .abnormalCount(abnormalCount)
                .totalChecked(totalChecked)
                .passRate(passRate)
                .certificateExpiringCount(certificateExpiringCount)
                .certificateExpiredCount(certificateExpiredCount)
                .build();
    }

    @Override
    @DataScope
    public PageResult<HealthCheckRecordVO> getPendingList(HealthCheckQueryDTO query) {
        healthTaskLinkageService.reconcileTodayTasksIfNeeded();

        LocalDate targetDate = query != null && query.getCheckDate() != null ? query.getCheckDate() : LocalDate.now();
        LambdaQueryWrapper<HealthCheckRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthCheckRecord::getCheckDate, targetDate);
        if (query != null && query.getEmployeeName() != null && !query.getEmployeeName().isBlank()) {
            wrapper.like(HealthCheckRecord::getEmployeeName, query.getEmployeeName());
        }
        if (query != null && query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(HealthCheckRecord::getStatus, query.getStatus());
        } else {
            wrapper.in(HealthCheckRecord::getStatus, List.of(STATUS_PENDING_CHECK, STATUS_CHECKING));
        }
        wrapper.orderByAsc(HealthCheckRecord::getCreatedAt);

        List<HealthCheckRecord> records = healthCheckRecordMapper.selectList(wrapper);
        Map<Long, HealthTaskLinkageService.TaskLinkageState> stateMap =
                healthTaskLinkageService.loadStateMap(targetDate, extractEmployeeIds(records));
        List<HealthCheckRecordVO> voList = records.stream()
                .filter(record -> shouldIncludeInPendingList(record, stateMap.get(record.getEmployeeId()), query))
                .map(record -> buildRecordVO(record, stateMap.get(record.getEmployeeId())))
                .collect(Collectors.toList());

        int pageNum = query != null && query.getPageNum() != null ? query.getPageNum() : 1;
        int pageSize = query != null && query.getPageSize() != null ? query.getPageSize() : 20;
        int fromIndex = Math.min((pageNum - 1) * pageSize, voList.size());
        int toIndex = Math.min(fromIndex + pageSize, voList.size());
        List<HealthCheckRecordVO> pageList = voList.subList(fromIndex, toIndex);

        return PageResult.of(pageList, (long) pageNum, (long) pageSize, (long) voList.size());
    }

    @Override
    @DataScope
    public PageResult<HealthCheckRecordVO> getRecordPage(HealthCheckQueryDTO query) {
        healthTaskLinkageService.reconcileTodayTasksIfNeeded();
        LambdaQueryWrapper<HealthCheckRecord> wrapper = buildQueryWrapper(query);
        // 已完成列表排除待检状态
        wrapper.notIn(HealthCheckRecord::getStatus, List.of(STATUS_PENDING_CHECK, STATUS_CHECKING));
        wrapper.orderByDesc(HealthCheckRecord::getCreatedAt);

        List<HealthCheckRecord> allRecords = healthCheckRecordMapper.selectList(wrapper);
        Map<String, HealthTaskLinkageService.TaskLinkageState> stateMap = loadTaskStateMapByRecords(allRecords);
        List<HealthCheckRecordVO> voList = allRecords.stream()
                .map(record -> buildRecordVO(record, stateMap.get(buildStateKey(record.getCheckDate(), record.getEmployeeId()))))
                .collect(Collectors.toList());

        // 内存分页
        int pageNum = query != null && query.getPageNum() != null ? query.getPageNum() : 1;
        int pageSize = query != null && query.getPageSize() != null ? query.getPageSize() : 20;
        int fromIndex = Math.min((pageNum - 1) * pageSize, voList.size());
        int toIndex = Math.min(fromIndex + pageSize, voList.size());
        List<HealthCheckRecordVO> pageList = voList.subList(fromIndex, toIndex);

        return PageResult.of(pageList, (long) pageNum, (long) pageSize, (long) voList.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.HEALTH_CHECK,
            operationType = AuditOperationType.CREATE,
            targetId = "#result.id",
            targetNo = "#result.checkNo",
            desc = "'新增晨检记录：' + #result.checkNo",
            mapper = HealthCheckRecordMapper.class
    )
    public HealthCheckDetailVO createRecord(HealthCheckCreateDTO dto) {
        healthTaskLinkageService.reconcileTodayTasksIfNeeded();
        if (dto.getEmployeeId() != null) {
            HealthTaskLinkageService.TaskLinkageState linkageState = healthTaskLinkageService.loadState(
                    dto.getCheckDate() != null ? dto.getCheckDate() : LocalDate.now(),
                    dto.getEmployeeId()
            );
            if (linkageState != null && Boolean.FALSE.equals(linkageState.getShouldCheck())) {
                throw new RuntimeException("该员工今日已被移出应检范围，无法继续晨检");
            }
        }

        // 检查重复：员工+日期
        LambdaQueryWrapper<HealthCheckRecord> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(HealthCheckRecord::getEmployeeId, dto.getEmployeeId());
        existWrapper.eq(HealthCheckRecord::getCheckDate, dto.getCheckDate());
        HealthCheckRecord existing = healthCheckRecordMapper.selectOne(existWrapper);

        if (existing != null) {
            if (STATUS_PENDING_CHECK.equals(existing.getStatus())) {
                // 更新已有的待晨检记录
                return updateExistingRecord(existing, dto);
            } else {
                throw new RuntimeException("该员工今日已完成晨检，状态: " + existing.getStatus());
            }
        }

        // 创建新记录
        HealthCheckRecord record = new HealthCheckRecord();

        // 生成 check_no: HC-yyyyMMdd-NNN
        LocalDate checkDate = dto.getCheckDate() != null ? dto.getCheckDate() : LocalDate.now();
        String datePart = checkDate.format(CHECK_NO_FMT);
        int seq = healthCheckRecordMapper.nextSeqForDate(checkDate);
        record.setCheckNo("HC-" + datePart + String.format("%03d", seq));

        record.setEmployeeId(dto.getEmployeeId());
        // 自动填充员工姓名
        if (dto.getEmployeeName() == null || dto.getEmployeeName().isBlank()) {
            record.setEmployeeName(getEmployeeField(dto.getEmployeeId(), "real_name"));
        } else {
            record.setEmployeeName(dto.getEmployeeName());
        }
        record.setCheckDate(checkDate);
        record.setCheckTime(LocalDateTime.now());
        record.setTemperature(dto.getTemperature());
        record.setFaceImageUrl(dto.getFaceImageUrl());
        record.setFaceMatchScore(dto.getFaceMatchScore());
        record.setCheckerId(dto.getCheckerId());
        record.setRemark(dto.getRemark());
        record.setOrgId(UserContext.getOrgId() != null ? UserContext.getOrgId() : (dto.getOrgId() != null ? dto.getOrgId() : 1L));
        record.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : (dto.getTenantId() != null ? dto.getTenantId() : 1L));

        // 手部卫生和着装检查
        record.setHandHygiene(dto.getHandHygiene());
        record.setUniformCheck(dto.getUniformCheck());

        // 判定体温状态和健康状况
        String tempStatus = determineTempStatus(dto.getTemperature());
        record.setHealthStatus(TEMP_HIGH.equals(tempStatus) ? "abnormal" : "normal");

        // 判定晨检结果
        String checkResult = determineCheckResult(record);
        record.setCheckResult(checkResult);

        // 获取健康证状态
        String certStatus = getCertificateStatus(dto.getEmployeeId());
        record.setCertificateStatus(certStatus);

        // 设置状态
        record.setStatus(checkResult.equals("pass") ? STATUS_COMPLETED_NORMAL : STATUS_COMPLETED_ABNORMAL);

        healthCheckRecordMapper.insert(record);
        return buildDetailVO(record);
    }

    @Override
    public HealthCheckDetailVO getRecordDetail(Long id) {
        healthTaskLinkageService.reconcileTodayTasksIfNeeded();
        HealthCheckRecord record = getRequiredRecord(id);
        ensureRecordAccessible(record);
        return buildDetailVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.HEALTH_CHECK,
            operationType = AuditOperationType.UPDATE,
            targetId = "#id",
            targetNo = "#entity.checkNo",
            desc = "'编辑晨检记录：' + #entity.checkNo",
            mapper = HealthCheckRecordMapper.class
    )
    public HealthCheckDetailVO updateRecord(Long id, HealthCheckUpdateDTO dto) {
        healthTaskLinkageService.reconcileTodayTasksIfNeeded();
        HealthCheckRecord record = getRequiredRecord(id);
        ensureRecordAccessible(record);

        // 只允许更新未归档的记录
        if (STATUS_ARCHIVED.equals(record.getStatus())) {
            throw new RuntimeException("已归档的记录无法更新");
        }

        // 更新字段
        if (dto.getTemperature() != null) {
            record.setTemperature(dto.getTemperature());
            String tempStatus = determineTempStatus(dto.getTemperature());
            record.setHealthStatus(TEMP_HIGH.equals(tempStatus) ? "abnormal" : "normal");
        }
        if (dto.getFaceImageUrl() != null) {
            record.setFaceImageUrl(dto.getFaceImageUrl());
        }
        if (dto.getFaceMatchScore() != null) {
            record.setFaceMatchScore(dto.getFaceMatchScore());
        }
        if (dto.getHandHygiene() != null) {
            record.setHandHygiene(dto.getHandHygiene());
        }
        if (dto.getUniformCheck() != null) {
            record.setUniformCheck(dto.getUniformCheck());
        }
        if (dto.getHealthStatus() != null) {
            record.setHealthStatus(dto.getHealthStatus());
        }
        if (dto.getFailReason() != null) {
            record.setFailReason(dto.getFailReason());
        }
        if (dto.getRemark() != null) {
            record.setRemark(dto.getRemark());
        }

        // 重新计算晨检结果
        String checkResult = determineCheckResult(record);
        record.setCheckResult(checkResult);
        record.setStatus(checkResult.equals("pass") ? STATUS_COMPLETED_NORMAL : STATUS_COMPLETED_ABNORMAL);

        healthCheckRecordMapper.updateById(record);
        return buildDetailVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.HEALTH_CHECK,
            operationType = AuditOperationType.STATUS_CHANGE,
            targetId = "#id",
            targetNo = "#entity.checkNo",
            desc = "'归档晨检记录：' + #entity.checkNo",
            mapper = HealthCheckRecordMapper.class
    )
    public HealthCheckDetailVO archiveRecord(Long id) {
        healthTaskLinkageService.reconcileTodayTasksIfNeeded();
        HealthCheckRecord record = getRequiredRecord(id);
        ensureRecordAccessible(record);

        if (!STATUS_COMPLETED_NORMAL.equals(record.getStatus()) && !STATUS_COMPLETED_ABNORMAL.equals(record.getStatus())) {
            throw new RuntimeException("只有已完成的记录才能归档");
        }

        record.setStatus(STATUS_ARCHIVED);
        healthCheckRecordMapper.updateById(record);
        return buildDetailVO(record);
    }

    // ==================== 私有方法 ====================

    private HealthCheckRecord getRequiredRecord(Long id) {
        HealthCheckRecord record = healthCheckRecordMapper.selectById(id);
        if (record == null) {
            throw new RuntimeException("晨检记录不存在");
        }
        return record;
    }

    private void ensureRecordAccessible(HealthCheckRecord record) {
        if (record == null || dataScopeService.isAdminUser()) {
            return;
        }
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(record.getOrgId())) {
            throw new RuntimeException("无权访问该晨检记录");
        }
    }

    private HealthCheckDetailVO updateExistingRecord(HealthCheckRecord record, HealthCheckCreateDTO dto) {
        record.setCheckTime(LocalDateTime.now());
        record.setTemperature(dto.getTemperature());
        record.setFaceImageUrl(dto.getFaceImageUrl());
        record.setFaceMatchScore(dto.getFaceMatchScore());
        record.setHandHygiene(dto.getHandHygiene());
        record.setUniformCheck(dto.getUniformCheck());
        record.setCheckerId(dto.getCheckerId());
        record.setRemark(dto.getRemark());

        String tempStatus = determineTempStatus(dto.getTemperature());
        record.setHealthStatus(TEMP_HIGH.equals(tempStatus) ? "abnormal" : "normal");

        String checkResult = determineCheckResult(record);
        record.setCheckResult(checkResult);
        record.setCertificateStatus(getCertificateStatus(dto.getEmployeeId()));
        record.setStatus(checkResult.equals("pass") ? STATUS_COMPLETED_NORMAL : STATUS_COMPLETED_ABNORMAL);

        healthCheckRecordMapper.updateById(record);
        return buildDetailVO(record);
    }

    private String determineTempStatus(BigDecimal temperature) {
        if (temperature == null) return TEMP_NORMAL;
        if (temperature.compareTo(TEMP_LOW_THRESHOLD) < 0) return TEMP_LOW;
        if (temperature.compareTo(TEMP_WARNING_THRESHOLD) >= 0) return TEMP_HIGH;
        return TEMP_NORMAL;
    }

    private String determineCheckResult(HealthCheckRecord record) {
        String tempStatus = determineTempStatus(record.getTemperature());
        String handHygiene = record.getHandHygiene();
        String uniformCheck = record.getUniformCheck();

        // 体温检查
        if (TEMP_HIGH.equals(tempStatus)) {
            return "fail";
        }
        // 卫生检查
        if ("fail".equals(handHygiene) || "fail".equals(uniformCheck)) {
            return "fail";
        }
        return "pass";
    }

    private String getCertificateStatus(Long employeeId) {
        LambdaQueryWrapper<HealthCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthCertificate::getEmployeeId, employeeId);
        HealthCertificate cert = healthCertificateMapper.selectOne(wrapper);

        if (cert == null) {
            return "pending";
        }

        String status = cert.getStatus();
        if ("expired".equals(status)) {
            return "expired";
        }

        // 检查是否即将过期
        if ("valid".equals(status) && cert.getExpiryDate() != null) {
            LocalDate warningDate = LocalDate.now().plusDays(CERTIFICATE_WARNING_DAYS);
            if (cert.getExpiryDate().isBefore(LocalDate.now())) {
                return "expired";
            } else if (cert.getExpiryDate().isBefore(warningDate)) {
                return "expiring";
            }
        }
        return status;
    }

    private LambdaQueryWrapper<HealthCheckRecord> buildQueryWrapper(HealthCheckQueryDTO query) {
        LambdaQueryWrapper<HealthCheckRecord> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getCheckDateStart() != null) {
                wrapper.ge(HealthCheckRecord::getCheckDate, query.getCheckDateStart());
            }
            if (query.getCheckDateEnd() != null) {
                wrapper.le(HealthCheckRecord::getCheckDate, query.getCheckDateEnd());
            }
            if (query.getCheckDate() != null) {
                wrapper.eq(HealthCheckRecord::getCheckDate, query.getCheckDate());
            }
            if (query.getStatus() != null && !query.getStatus().isBlank()) {
                wrapper.eq(HealthCheckRecord::getStatus, query.getStatus());
            }
            if (query.getCheckResult() != null && !query.getCheckResult().isBlank()) {
                wrapper.eq(HealthCheckRecord::getCheckResult, query.getCheckResult());
            }
            if (query.getEmployeeName() != null && !query.getEmployeeName().isBlank()) {
                wrapper.like(HealthCheckRecord::getEmployeeName, query.getEmployeeName());
            }
            if (applyOrgScopeFilter(wrapper, query)) {
                wrapper.isNull(HealthCheckRecord::getId);
            }
        }
        return wrapper;
    }

    /**
     * @return true 表示权限范围为空，应返回空结果
     */
    private boolean applyOrgScopeFilter(LambdaQueryWrapper<HealthCheckRecord> wrapper, HealthCheckQueryDTO query) {
        if (query == null) {
            return false;
        }
        if (query.getOrgId() != null) {
            wrapper.eq(HealthCheckRecord::getOrgId, query.getOrgId());
            return false;
        }
        if (query.getOrgIds() == null) {
            return false;
        }
        if (query.getOrgIds().isEmpty()) {
            return true;
        }
        wrapper.in(HealthCheckRecord::getOrgId, query.getOrgIds());
        return false;
    }

    private long queryCertificateCount(String status, HealthCheckQueryDTO query) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM health_certificate " +
                "INNER JOIN sys_employee e ON e.id = health_certificate.employee_id AND e.deleted = 0 AND e.status = 'active' " +
                "INNER JOIN auth_user u ON u.id = e.user_id AND u.deleted = 0 AND u.status = 'active' " +
                "LEFT JOIN sys_organization o ON o.id = e.org_id AND o.deleted = 0 " +
                "WHERE health_certificate.status = ? AND health_certificate.deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(status);
        sql.append(" AND (o.status IS NULL OR o.status = 'active')");
        if (query != null) {
            if (query.getOrgId() != null) {
                sql.append(" AND e.org_id = ?");
                args.add(query.getOrgId());
            } else if (query.getOrgIds() != null) {
                if (query.getOrgIds().isEmpty()) {
                    return 0L;
                }
                sql.append(" AND e.org_id IN (")
                        .append(String.join(",", Collections.nCopies(query.getOrgIds().size(), "?")))
                        .append(")");
                args.addAll(query.getOrgIds());
            }
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private void applyLinkageInfo(HealthCheckRecordVO vo,
                                  HealthTaskLinkageService.TaskLinkageState linkageState) {
        if (vo == null) {
            return;
        }
        if (linkageState == null) {
            vo.setShouldCheck(Boolean.TRUE);
            vo.setDutyType(HealthTaskLinkageService.DUTY_TYPE_FORMAL);
            vo.setDutyTypeName(healthTaskLinkageService.resolveDutyTypeName(HealthTaskLinkageService.DUTY_TYPE_FORMAL));
            return;
        }
        vo.setShouldCheck(linkageState.getShouldCheck());
        vo.setDutyType(linkageState.getDutyType());
        vo.setDutyTypeName(healthTaskLinkageService.resolveDutyTypeName(linkageState.getDutyType()));
        vo.setCurrentOrgId(linkageState.getCurrentOrgId());
        vo.setCurrentOrgName(linkageState.getCurrentOrgName());
        vo.setLinkageReason(linkageState.getReasonDesc());
    }

    private void applyLinkageInfo(HealthCheckDetailVO vo,
                                  HealthTaskLinkageService.TaskLinkageState linkageState) {
        if (vo == null) {
            return;
        }
        if (linkageState == null) {
            vo.setShouldCheck(Boolean.TRUE);
            vo.setDutyType(HealthTaskLinkageService.DUTY_TYPE_FORMAL);
            vo.setDutyTypeName(healthTaskLinkageService.resolveDutyTypeName(HealthTaskLinkageService.DUTY_TYPE_FORMAL));
            return;
        }
        vo.setShouldCheck(linkageState.getShouldCheck());
        vo.setDutyType(linkageState.getDutyType());
        vo.setDutyTypeName(healthTaskLinkageService.resolveDutyTypeName(linkageState.getDutyType()));
        vo.setCurrentOrgId(linkageState.getCurrentOrgId());
        vo.setCurrentOrgName(linkageState.getCurrentOrgName());
        vo.setLinkageReason(linkageState.getReasonDesc());
        vo.setLinkageUpdatedAt(linkageState.getUpdatedAt());
    }

    private Map<String, HealthTaskLinkageService.TaskLinkageState> loadTaskStateMapByRecords(Collection<HealthCheckRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<LocalDate, List<HealthCheckRecord>> recordsByDate = records.stream()
                .filter(Objects::nonNull)
                .filter(record -> record.getCheckDate() != null && record.getEmployeeId() != null)
                .collect(Collectors.groupingBy(HealthCheckRecord::getCheckDate));
        if (recordsByDate.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, HealthTaskLinkageService.TaskLinkageState> result = new HashMap<>();
        recordsByDate.forEach((checkDate, dateRecords) -> {
            Map<Long, HealthTaskLinkageService.TaskLinkageState> dateStateMap =
                    healthTaskLinkageService.loadStateMap(checkDate, extractEmployeeIds(dateRecords));
            dateStateMap.forEach((employeeId, state) -> result.put(buildStateKey(checkDate, employeeId), state));
        });
        return result;
    }

    private Collection<Long> extractEmployeeIds(Collection<HealthCheckRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        return records.stream()
                .filter(Objects::nonNull)
                .map(HealthCheckRecord::getEmployeeId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean shouldIncludeForTodayStats(HealthCheckRecord record,
                                               HealthTaskLinkageService.TaskLinkageState linkageState,
                                               HealthCheckQueryDTO query) {
        if (record == null || !healthTaskLinkageService.shouldCheck(linkageState)) {
            return false;
        }
        Long scopedOrgId = linkageState != null ? linkageState.getCurrentOrgId() : record.getOrgId();
        return matchesOrgScope(scopedOrgId, query);
    }

    private boolean shouldIncludeInPendingList(HealthCheckRecord record,
                                               HealthTaskLinkageService.TaskLinkageState linkageState,
                                               HealthCheckQueryDTO query) {
        if (record == null || !healthTaskLinkageService.isPendingLike(record.getStatus())) {
            return false;
        }
        if (!healthTaskLinkageService.shouldCheck(linkageState)) {
            return false;
        }
        Long scopedOrgId = linkageState != null ? linkageState.getCurrentOrgId() : record.getOrgId();
        return matchesOrgScope(scopedOrgId, query);
    }

    private boolean matchesOrgScope(Long orgId, HealthCheckQueryDTO query) {
        if (query == null) {
            return true;
        }
        if (query.getOrgId() != null) {
            return Objects.equals(query.getOrgId(), orgId);
        }
        if (query.getOrgIds() == null) {
            return true;
        }
        if (query.getOrgIds().isEmpty()) {
            return false;
        }
        return orgId != null && query.getOrgIds().contains(orgId);
    }

    private boolean isCompletedForStats(HealthCheckRecord record) {
        return healthTaskLinkageService.isCompletedLike(record);
    }

    private boolean isPassForStats(HealthCheckRecord record) {
        return isCompletedForStats(record) && "pass".equals(record.getCheckResult());
    }

    private boolean isFailForStats(HealthCheckRecord record) {
        return isCompletedForStats(record) && "fail".equals(record.getCheckResult());
    }

    private String buildStateKey(LocalDate checkDate, Long employeeId) {
        return String.valueOf(checkDate) + "#" + employeeId;
    }

    private HealthCheckRecordVO buildRecordVO(HealthCheckRecord record,
                                              HealthTaskLinkageService.TaskLinkageState linkageState) {
        HealthCheckRecordVO vo = new HealthCheckRecordVO();
        vo.setId(record.getId());
        vo.setCheckNo(record.getCheckNo());
        vo.setEmployeeId(record.getEmployeeId());
        vo.setEmployeeName(record.getEmployeeName());
        vo.setCheckDate(record.getCheckDate());
        vo.setCheckTime(record.getCheckTime());
        vo.setTemperature(record.getTemperature());
        vo.setFaceMatchScore(record.getFaceMatchScore());
        vo.setCertificateStatus(record.getCertificateStatus());
        vo.setHandHygiene(record.getHandHygiene());
        vo.setUniformCheck(record.getUniformCheck());
        vo.setHealthStatus(record.getHealthStatus());
        vo.setCheckResult(record.getCheckResult());
        vo.setFailReason(record.getFailReason());
        vo.setCheckerId(record.getCheckerId());
        vo.setStatus(record.getStatus());
        vo.setCreatedAt(record.getCreatedAt());
        applyLinkageInfo(vo, linkageState);
        // 补充员工扩展信息
        enrichEmployeeInfo(vo, record.getEmployeeId());
        return vo;
    }

    private HealthCheckDetailVO buildDetailVO(HealthCheckRecord record) {
        HealthTaskLinkageService.TaskLinkageState linkageState =
                healthTaskLinkageService.loadState(record.getCheckDate(), record.getEmployeeId());
        HealthCheckDetailVO vo = new HealthCheckDetailVO();
        vo.setId(record.getId());
        vo.setCheckNo(record.getCheckNo());
        vo.setEmployeeId(record.getEmployeeId());
        vo.setEmployeeName(record.getEmployeeName());
        vo.setCheckDate(record.getCheckDate());
        vo.setCheckTime(record.getCheckTime());
        vo.setTemperature(record.getTemperature());
        vo.setFaceImageUrl(record.getFaceImageUrl());
        vo.setFaceMatchScore(record.getFaceMatchScore());
        vo.setCertificateStatus(record.getCertificateStatus());
        vo.setHandHygiene(record.getHandHygiene());
        vo.setUniformCheck(record.getUniformCheck());
        vo.setHealthStatus(record.getHealthStatus());
        vo.setCheckResult(record.getCheckResult());
        vo.setFailReason(record.getFailReason());
        vo.setCheckerId(record.getCheckerId());
        vo.setRemark(record.getRemark());
        vo.setStatus(record.getStatus());
        applyLinkageInfo(vo, linkageState);
        vo.setOrgId(record.getOrgId());
        vo.setTenantId(record.getTenantId());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        vo.setMovementLogs(healthTaskLinkageService.loadMovementLogs(record.getId()));
        // 补充员工扩展信息
        enrichDetailEmployeeInfo(vo, record.getEmployeeId());
        // 补充晨检员姓名
        if (record.getCheckerId() != null) {
            vo.setCheckerName(getEmployeeField(record.getCheckerId(), "employee_name"));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiCheckResultVO aiCheck(AiCheckDTO dto) {
        log.info("AI一键晨检请求: expectedEmployeeId={}, deviceId={}", dto.getExpectedEmployeeId(), dto.getDeviceId());

        List<String> failReasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ==================== 1. 人脸识别 ====================
        FaceRecognizeDTO faceDTO = new FaceRecognizeDTO();
        faceDTO.setFaceImageBase64(dto.getFaceImage());
        faceDTO.setExpectedEmployeeId(dto.getExpectedEmployeeId());
        faceDTO.setMatchThreshold(dto.getMatchThreshold() != null ? dto.getMatchThreshold().doubleValue() : FACE_MATCH_THRESHOLD.doubleValue());
        faceDTO.setOrgId(dto.getOrgId());

        FaceRecognizeVO faceResult = faceFeatureService.recognize(faceDTO);

        if (!Boolean.TRUE.equals(faceResult.getSuccess())) {
            throw new RuntimeException("人脸识别失败，请重新拍摄: " + faceResult.getFailReason());
        }

        BigDecimal faceMatchScore = faceResult.getMatchScore() != null ? faceResult.getMatchScore() : BigDecimal.ZERO;
        boolean faceVerified = Boolean.TRUE.equals(faceResult.getVerified());
        Long employeeId = faceResult.getEmployeeId();
        String employeeName = faceResult.getEmployeeName() != null ? faceResult.getEmployeeName() : "未知";
        String faceMatchResult = faceVerified ? "pass" : "fail";

        if (!faceVerified) {
            failReasons.add("人脸验证未通过（匹配度: " + faceMatchScore + "%）");
        }

        // 获取员工扩展信息
        String position = getEmployeeField(employeeId, "position");
        String employeeNo = getEmployeeField(employeeId, "employee_code");
        String avatarUrl = getEmployeeField(employeeId, "avatar_url");

        // ==================== 2. 体温检测 ====================
        BigDecimal temperature = dto.getTemperature();
        String tempStatus = TEMP_NORMAL;
        String tempCheckResult = "pass";
        String tempCheckMessage = null;

        if (temperature != null) {
            tempStatus = determineTempStatus(temperature);
            if (TEMP_HIGH.equals(tempStatus)) {
                tempCheckResult = "fail";
                tempCheckMessage = "体温异常: " + temperature + "℃（≥37.3℃）";
                failReasons.add(tempCheckMessage);
            } else if (TEMP_LOW.equals(tempStatus)) {
                tempCheckResult = "pass";
                tempCheckMessage = "体温偏低: " + temperature + "℃（<36.0℃）";
                warnings.add(tempCheckMessage);
            }
        }

        // ==================== 3. 健康证校验 ====================
        String certNo = null;
        LocalDate certExpiryDate = null;
        String certStatus = "pending";
        String certCheckResult = "pass";
        String certCheckMessage = null;

        if (employeeId != null) {
            LambdaQueryWrapper<HealthCertificate> certWrapper = new LambdaQueryWrapper<>();
            certWrapper.eq(HealthCertificate::getEmployeeId, employeeId);
            HealthCertificate cert = healthCertificateMapper.selectOne(certWrapper);

            if (cert != null) {
                certNo = cert.getCertificateNo();
                certExpiryDate = cert.getExpiryDate();
                certStatus = cert.getStatus() != null ? cert.getStatus() : "pending";

                if (cert.getExpiryDate() != null && cert.getExpiryDate().isBefore(LocalDate.now())) {
                    certStatus = "expired";
                    certCheckResult = "fail";
                    certCheckMessage = "健康证已过期（到期日: " + cert.getExpiryDate() + "）";
                    failReasons.add(certCheckMessage);
                } else if (cert.getExpiryDate() != null && cert.getExpiryDate().isBefore(LocalDate.now().plusDays(CERTIFICATE_WARNING_DAYS))) {
                    certStatus = "expiring";
                    certCheckResult = "pass";
                    long remainDays = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), cert.getExpiryDate());
                    certCheckMessage = "健康证即将过期（剩余 " + remainDays + " 天）";
                    warnings.add(certCheckMessage);
                }
            } else {
                certStatus = "pending";
                certCheckResult = "fail";
                certCheckMessage = "未办理健康证";
                failReasons.add(certCheckMessage);
            }
        }

        // ==================== 4. 手部卫生 & 着装检查 ====================
        String handHygiene = dto.getHandHygiene() != null ? dto.getHandHygiene() : "pass";
        String uniformCheck = dto.getUniformCheck() != null ? dto.getUniformCheck() : "pass";
        String handHygieneMessage = "pass".equals(handHygiene) ? null : "手部卫生检查不通过";
        String uniformCheckMessage = "pass".equals(uniformCheck) ? null : "着装检查不通过";

        if ("fail".equals(handHygiene)) {
            failReasons.add("手部卫生检查不通过");
        }
        if ("fail".equals(uniformCheck)) {
            failReasons.add("着装检查不通过");
        }

        // ==================== 5. 综合判定 ====================
        String checkResult = failReasons.isEmpty() ? "pass" : "fail";
        boolean hasWarning = !warnings.isEmpty();

        // ==================== 6. 创建晨检记录 ====================
        HealthCheckCreateDTO createDto = new HealthCheckCreateDTO();
        createDto.setEmployeeId(employeeId);
        createDto.setEmployeeName(employeeName);
        createDto.setTemperature(temperature);
        createDto.setFaceImageBase64(dto.getFaceImage());
        createDto.setEnableFaceRecognize(true);
        createDto.setFaceMatchScore(faceMatchScore);
        createDto.setFaceVerified(faceVerified);
        createDto.setHandHygiene(handHygiene);
        createDto.setUniformCheck(uniformCheck);
        createDto.setOrgId(dto.getOrgId());
        if (!failReasons.isEmpty()) {
            createDto.setRemark("【AI自动检查】" + String.join("; ", failReasons));
        }

        HealthCheckDetailVO detail = createRecord(createDto);

        auditLogService.log(AuditModule.HEALTH_CHECK, AuditOperationType.PROCESS,
                detail.getId(), detail.getCheckNo(),
                "AI一键晨检，员工：" + employeeName + "，结果：" + checkResult,
                null, null);

        // ==================== 7. 组装AI检查结果 ====================
        return AiCheckResultVO.builder()
                .checkId(detail.getId())
                .checkNo(detail.getCheckNo())
                .employeeId(employeeId)
                .employeeName(employeeName)
                .position(position)
                .employeeNo(employeeNo)
                .avatarUrl(avatarUrl)
                .faceMatchScore(faceMatchScore)
                .faceMatchResult(faceMatchResult)
                .faceImageUrl(detail.getFaceImageUrl())
                .checkTime(detail.getCheckTime())
                .temperature(temperature)
                .tempStatus(tempStatus)
                .tempCheckResult(tempCheckResult)
                .certNo(certNo)
                .certExpiryDate(certExpiryDate)
                .certStatus(certStatus)
                .certCheckResult(certCheckResult)
                .certCheckMessage(certCheckMessage)
                .handHygiene(handHygiene)
                .handHygieneMessage(handHygieneMessage)
                .uniformCheck(uniformCheck)
                .uniformCheckMessage(uniformCheckMessage)
                .checkResult(checkResult)
                .failReasons(failReasons)
                .hasWarning(hasWarning)
                .warningMessages(warnings)
                .build();
    }

    @Override
    public HealthCheckLinkageVersionVO getLinkageVersion() {
        return healthTaskLinkageService.getVersion();
    }

    private String getEmployeeField(Long employeeId, String fieldName) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT " + fieldName + " FROM sys_employee WHERE id = ?", String.class, employeeId);
        } catch (Exception e) {
            return null;
        }
    }

    private void enrichEmployeeInfo(HealthCheckRecordVO vo, Long employeeId) {
        if (employeeId == null) return;
        try {
            Map<String, Object> empInfo = jdbcTemplate.queryForMap(
                    "SELECT avatar_url, position, employee_no, face_enrolled FROM sys_employee WHERE id = ?",
                    new Object[]{employeeId});
            if (empInfo != null) {
                vo.setAvatarUrl((String) empInfo.get("avatar_url"));
                vo.setPosition((String) empInfo.get("position"));
                vo.setEmployeeNo((String) empInfo.get("employee_no"));
                vo.setHasFaceData(Boolean.TRUE.equals(empInfo.get("face_enrolled")));
            }
        } catch (Exception e) {
            log.warn("获取员工扩展信息失败, employeeId={}", employeeId, e);
        }
        // 获取健康证状态和到期日期
        try {
            String certStatus = getCertificateStatus(employeeId);
            vo.setCertificateStatus(certStatus);
            LambdaQueryWrapper<HealthCertificate> certWrapper = new LambdaQueryWrapper<>();
            certWrapper.eq(HealthCertificate::getEmployeeId, employeeId);
            HealthCertificate cert = healthCertificateMapper.selectOne(certWrapper);
            if (cert != null) {
                vo.setCertExpiryDate(cert.getExpiryDate());
            }
        } catch (Exception e) {
            log.warn("获取健康证信息失败, employeeId={}", employeeId, e);
        }
    }

    private void enrichDetailEmployeeInfo(HealthCheckDetailVO vo, Long employeeId) {
        if (employeeId == null) return;
        try {
            Map<String, Object> empInfo = jdbcTemplate.queryForMap(
                    "SELECT avatar_url, position, employee_no, face_enrolled FROM sys_employee WHERE id = ?",
                    new Object[]{employeeId});
            if (empInfo != null) {
                vo.setAvatarUrl((String) empInfo.get("avatar_url"));
                vo.setPosition((String) empInfo.get("position"));
                vo.setEmployeeNo((String) empInfo.get("employee_no"));
                vo.setHasFaceData(Boolean.TRUE.equals(empInfo.get("face_enrolled")));
            }
        } catch (Exception e) {
            log.warn("获取员工扩展信息失败, employeeId={}", employeeId, e);
        }
        try {
            String certStatus = getCertificateStatus(employeeId);
            vo.setCertificateStatus(certStatus);
            LambdaQueryWrapper<HealthCertificate> certWrapper = new LambdaQueryWrapper<>();
            certWrapper.eq(HealthCertificate::getEmployeeId, employeeId);
            HealthCertificate cert = healthCertificateMapper.selectOne(certWrapper);
            if (cert != null) {
                vo.setCertExpiryDate(cert.getExpiryDate());
            }
        } catch (Exception e) {
            log.warn("获取健康证信息失败, employeeId={}", employeeId, e);
        }
    }
}
