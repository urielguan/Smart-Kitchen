package com.xykj.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.core.util.StrUtil;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.exception.BizException;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.health.dto.HealthCertificateCreateDTO;
import com.xykj.health.dto.HealthCertificateQueryDTO;
import com.xykj.health.entity.HealthCertificate;
import com.xykj.health.mapper.HealthCertificateMapper;
import com.xykj.health.service.HealthCertificateService;
import com.xykj.health.vo.HealthCertificateDashboardVO;
import com.xykj.health.vo.HealthCertificateVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 健康证管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCertificateServiceImpl implements HealthCertificateService {

    private final HealthCertificateMapper certificateMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final DataScopeService dataScopeService;

    private static final int DEFAULT_WARNING_DAYS = 30;

    @Override
    public HealthCertificateVO save(HealthCertificateCreateDTO dto) {
        // 查询该员工是否已有健康证
        LambdaQueryWrapper<HealthCertificate> wrapper = new LambdaQueryWrapper<HealthCertificate>()
                .eq(HealthCertificate::getEmployeeId, dto.getEmployeeId());
        HealthCertificate existing = certificateMapper.selectOne(wrapper);

        // 获取员工姓名
        String employeeName = getEmployeeName(dto.getEmployeeId());

        // 校验健康证编号唯一性
        if (StrUtil.isNotBlank(dto.getCertificateNo())) {
            LambdaQueryWrapper<HealthCertificate> noWrapper = new LambdaQueryWrapper<HealthCertificate>()
                    .eq(HealthCertificate::getCertificateNo, dto.getCertificateNo());
            if (existing != null) {
                noWrapper.ne(HealthCertificate::getId, existing.getId());
            }
            if (certificateMapper.selectCount(noWrapper) > 0) {
                throw new BizException("健康证编号已存在：" + dto.getCertificateNo());
            }
        }

        // 计算状态
        String status = calculateStatus(dto.getExpiryDate(), dto.getWarningDays() != null ? dto.getWarningDays() : DEFAULT_WARNING_DAYS);

        String operationResult = "success";
        String errorMsg = null;

        try {
            if (existing != null) {
                // 编辑：记录修改前数据
                String beforeData = cn.hutool.json.JSONUtil.toJsonStr(existing);
                existing.setEmployeeName(employeeName);
                existing.setCertificateNo(dto.getCertificateNo());
                existing.setIssueDate(dto.getIssueDate());
                existing.setExpiryDate(dto.getExpiryDate());
                existing.setCertificateImages(dto.getCertificateImages());
                existing.setIssuingAuthority(dto.getIssuingAuthority());
                existing.setStatus(status);
                existing.setWarningDays(dto.getWarningDays() != null ? dto.getWarningDays() : DEFAULT_WARNING_DAYS);
                existing.setRemark(dto.getRemark());
                Long employeeOrgId = getEmployeeOrgId(dto.getEmployeeId());
                if (employeeOrgId != null) {
                    existing.setOrgId(employeeOrgId);
                } else if (UserContext.getOrgId() != null) {
                    existing.setOrgId(UserContext.getOrgId());
                }
                certificateMapper.updateById(existing);
                String afterData = cn.hutool.json.JSONUtil.toJsonStr(certificateMapper.selectById(existing.getId()));
                auditLogService.log(AuditModule.HEALTH_CERTIFICATE, AuditOperationType.UPDATE,
                        existing.getId(), existing.getCertificateNo(),
                        "编辑健康证：" + existing.getCertificateNo(),
                        beforeData, afterData, operationResult, errorMsg);
                return toVO(existing);
            } else {
                // 新建
                HealthCertificate cert = new HealthCertificate();
                cert.setEmployeeId(dto.getEmployeeId());
                cert.setEmployeeName(employeeName);
                cert.setCertificateNo(dto.getCertificateNo());
                cert.setIssueDate(dto.getIssueDate());
                cert.setExpiryDate(dto.getExpiryDate());
                cert.setCertificateImages(dto.getCertificateImages());
                cert.setIssuingAuthority(dto.getIssuingAuthority());
                cert.setStatus(status);
                cert.setWarningDays(dto.getWarningDays() != null ? dto.getWarningDays() : DEFAULT_WARNING_DAYS);
                cert.setRemark(dto.getRemark());
                Long newEmployeeOrgId = getEmployeeOrgId(dto.getEmployeeId());
                cert.setOrgId(newEmployeeOrgId != null ? newEmployeeOrgId : (UserContext.getOrgId() != null ? UserContext.getOrgId() : dto.getOrgId()));
                cert.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : dto.getTenantId());
                certificateMapper.insert(cert);
                String afterData = cn.hutool.json.JSONUtil.toJsonStr(cert);
                auditLogService.log(AuditModule.HEALTH_CERTIFICATE, AuditOperationType.CREATE,
                        cert.getId(), cert.getCertificateNo(),
                        "新增健康证：" + cert.getCertificateNo(),
                        null, afterData, operationResult, errorMsg);
                return toVO(cert);
            }
        } catch (Exception e) {
            operationResult = "failure";
            errorMsg = e.getMessage();
            throw e;
        } finally {
            // 失败时记录日志（成功时已在上面记录）
            if ("failure".equals(operationResult)) {
                auditLogService.log(AuditModule.HEALTH_CERTIFICATE,
                        existing != null ? AuditOperationType.UPDATE : AuditOperationType.CREATE,
                        existing != null ? existing.getId() : null,
                        dto.getCertificateNo(),
                        (existing != null ? "编辑" : "新增") + "健康证：" + dto.getCertificateNo(),
                        null, null, operationResult, errorMsg);
            }
        }
    }

    @Override
    public HealthCertificateVO getByEmployeeId(Long employeeId) {
        LambdaQueryWrapper<HealthCertificate> wrapper = new LambdaQueryWrapper<HealthCertificate>()
                .eq(HealthCertificate::getEmployeeId, employeeId);
        HealthCertificate cert = certificateMapper.selectOne(wrapper);
        ensureCertificateAccessible(cert);
        return cert != null ? toVO(cert) : null;
    }

    @Override
    @DataScope
    public List<HealthCertificateVO> list(HealthCertificateQueryDTO query) {
        LambdaQueryWrapper<HealthCertificate> wrapper = new LambdaQueryWrapper<HealthCertificate>()
                .eq(query.getStatus() != null && !query.getStatus().isBlank(), HealthCertificate::getStatus, query.getStatus())
                .eq(query.getOrgId() != null, HealthCertificate::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), HealthCertificate::getOrgId, query.getOrgIds())
                .orderByDesc(HealthCertificate::getExpiryDate);
        applyKeywordFilter(wrapper, query.getKeyword());
        applyActiveEmployeeFilter(wrapper);
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(HealthCertificate::getId);
        }
        return certificateMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @DataScope
    public List<HealthCertificateVO> listExpiring(HealthCertificateQueryDTO query) {
        LocalDate today = LocalDate.now();
        int withinDays = query.getWithinDays() != null ? query.getWithinDays() : DEFAULT_WARNING_DAYS;
        LocalDate deadline = today.plusDays(withinDays);

        LambdaQueryWrapper<HealthCertificate> wrapper = new LambdaQueryWrapper<HealthCertificate>()
                .between(HealthCertificate::getExpiryDate, today, deadline)
                .ne(HealthCertificate::getStatus, "expired")
                .eq(query.getOrgId() != null, HealthCertificate::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), HealthCertificate::getOrgId, query.getOrgIds())
                .orderByAsc(HealthCertificate::getExpiryDate);
        applyActiveEmployeeFilter(wrapper);
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(HealthCertificate::getId);
        }
        return certificateMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLog(
            module = AuditModule.HEALTH_CERTIFICATE,
            operationType = AuditOperationType.DELETE,
            targetId = "#id",
            desc = "'删除健康证'",
            mapper = HealthCertificateMapper.class
    )
    public boolean delete(Long id) {
        return certificateMapper.deleteById(id) > 0;
    }

    @Override
    public int refreshStatus() {
        LocalDate today = LocalDate.now();

        // 查询所有未过期的健康证，逐条按各自的 warning_days 计算状态
        List<HealthCertificate> certs = certificateMapper.selectList(
                new LambdaQueryWrapper<HealthCertificate>()
                        .ne(HealthCertificate::getStatus, "expired")
                        .eq(HealthCertificate::getDeleted, 0));

        int expiredCount = 0;
        int expiringCount = 0;
        int validCount = 0;

        for (HealthCertificate cert : certs) {
            if (cert.getExpiryDate() == null) continue;

            int warningDays = cert.getWarningDays() != null ? cert.getWarningDays() : DEFAULT_WARNING_DAYS;
            String newStatus = calculateStatus(cert.getExpiryDate(), warningDays);

            if (!newStatus.equals(cert.getStatus())) {
                cert.setStatus(newStatus);
                certificateMapper.updateById(cert);

                switch (newStatus) {
                    case "expired" -> expiredCount++;
                    case "expiring" -> expiringCount++;
                    case "valid" -> validCount++;
                }
            }
        }

        // 单独处理已经是 expired 但 expiry_date 为 NULL 的异常数据（如有）
        int expiredDirect = jdbcTemplate.update(
                "UPDATE health_certificate SET status = 'expired', updated_at = NOW() " +
                        "WHERE expiry_date < ? AND status != 'expired' AND deleted = 0", today);

        int totalUpdated = expiredCount + expiringCount + validCount + expiredDirect;
        log.info("健康证状态刷新完成: expired={}, expiring={}, valid={}, 直接标记过期={}", expiredCount, expiringCount, validCount, expiredDirect);

        auditLogService.log(AuditModule.HEALTH_CERTIFICATE, AuditOperationType.PROCESS,
                null, null, "刷新健康证状态，更新" + totalUpdated + "条记录", null, null);

        return totalUpdated;
    }

    /** 计算健康证状态 */
    private String calculateStatus(LocalDate expiryDate, int warningDays) {
        LocalDate today = LocalDate.now();
        if (expiryDate.isBefore(today)) {
            return "expired";
        } else if (!expiryDate.isAfter(today.plusDays(warningDays))) {
            return "expiring";
        } else {
            return "valid";
        }
    }

    /** 获取员工姓名 */
    private String getEmployeeName(Long employeeId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT real_name FROM sys_employee WHERE id = ?", String.class, employeeId);
        } catch (Exception e) {
            return "未知员工";
        }
    }

    /** 获取员工所属组织ID */
    private Long getEmployeeOrgId(Long employeeId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT org_id FROM sys_employee WHERE id = ? AND deleted = 0", Long.class, employeeId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 过滤在职且启用的员工健康证 */
    private void applyActiveEmployeeFilter(LambdaQueryWrapper<HealthCertificate> wrapper) {
        wrapper.exists(
                "SELECT 1 FROM sys_employee e " +
                "INNER JOIN auth_user u ON u.id = e.user_id AND u.deleted = 0 AND u.status = 'active' " +
                "WHERE e.id = health_certificate.employee_id AND e.deleted = 0 AND e.status = 'active'"
        );
    }

    /** 列表勾选“显示离职员工”后，允许查看全部未删除员工的健康证档案。 */
    private void applyEmployeeVisibilityFilter(LambdaQueryWrapper<HealthCertificate> wrapper, HealthCertificateQueryDTO query) {
        if (Boolean.TRUE.equals(query.getShowLeftEmployees())) {
            wrapper.exists(
                    "SELECT 1 FROM sys_employee e " +
                    "WHERE e.id = health_certificate.employee_id AND e.deleted = 0"
            );
            return;
        }
        applyActiveEmployeeFilter(wrapper);
    }

    @Override
    public HealthCertificateVO getById(Long id) {
        HealthCertificate cert = certificateMapper.selectById(id);
        ensureCertificateAccessible(cert);
        return cert != null ? toVO(cert) : null;
    }

    @Override
    @DataScope
    public PageResult<HealthCertificateVO> listPage(HealthCertificateQueryDTO query) {
        LambdaQueryWrapper<HealthCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getStatus() != null && !query.getStatus().isBlank(), HealthCertificate::getStatus, query.getStatus());
        wrapper.eq(query.getOrgId() != null, HealthCertificate::getOrgId, query.getOrgId());
        wrapper.in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), HealthCertificate::getOrgId, query.getOrgIds());
        applyKeywordFilter(wrapper, query.getKeyword());
        applyEmployeeVisibilityFilter(wrapper, query);
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(HealthCertificate::getId);
        }
        wrapper.orderByDesc(HealthCertificate::getExpiryDate);

        List<HealthCertificate> all = certificateMapper.selectList(wrapper);
        List<HealthCertificateVO> voList = all.stream().map(this::toVO).collect(Collectors.toList());

        int pageNum = query.getPageNum() != null ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;
        int fromIndex = Math.min((pageNum - 1) * pageSize, voList.size());
        int toIndex = Math.min(fromIndex + pageSize, voList.size());
        List<HealthCertificateVO> pageList = voList.subList(fromIndex, toIndex);

        return PageResult.of(pageList, (long) pageNum, (long) pageSize, (long) voList.size());
    }

    @Override
    @DataScope
    public HealthCertificateDashboardVO getDashboard(HealthCertificateQueryDTO query) {
        Long orgIdParam = query.getOrgId();
        List<Long> scopedOrgIds = normalizeScopedOrgIds(query.getOrgIds());
        if (orgIdParam == null && scopedOrgIds != null && scopedOrgIds.isEmpty()) {
            return HealthCertificateDashboardVO.builder()
                    .totalCount(0)
                    .validCount(0)
                    .expiringCount(0)
                    .expiredCount(0)
                    .unregisteredCount(0)
                    .urgentWarnings(java.util.Collections.emptyList())
                    .build();
        }

        StringBuilder scopeSql = new StringBuilder();
        List<Object> scopeArgs = new ArrayList<>();
        appendOrgScopeCondition(scopeSql, scopeArgs, "health_certificate.org_id", orgIdParam, scopedOrgIds);

        // 所有统计均关联在职+启用员工
        String activeEmployeeJoin = " INNER JOIN sys_employee e ON e.id = health_certificate.employee_id AND e.deleted = 0 AND e.status = 'active'" +
                " INNER JOIN auth_user u ON u.id = e.user_id AND u.deleted = 0 AND u.status = 'active'";

        Integer totalCount = queryCount("SELECT COUNT(*) FROM health_certificate" + activeEmployeeJoin + " WHERE health_certificate.deleted = 0", scopeSql, scopeArgs);
        Integer validCount = queryCount("SELECT COUNT(*) FROM health_certificate" + activeEmployeeJoin + " WHERE health_certificate.status = 'valid' AND health_certificate.deleted = 0", scopeSql, scopeArgs);
        Integer expiringCount = queryCount("SELECT COUNT(*) FROM health_certificate" + activeEmployeeJoin + " WHERE health_certificate.status = 'expiring' AND health_certificate.deleted = 0", scopeSql, scopeArgs);
        Integer expiredCount = queryCount("SELECT COUNT(*) FROM health_certificate" + activeEmployeeJoin + " WHERE health_certificate.status = 'expired' AND health_certificate.deleted = 0", scopeSql, scopeArgs);

        // 计算未办理数：在职且启用员工数 - 有健康证员工数
        Integer activeEmployeeCount;
        {
            StringBuilder empScopeSql = new StringBuilder();
            List<Object> empScopeArgs = new ArrayList<>();
            appendOrgScopeCondition(empScopeSql, empScopeArgs, "e.org_id", orgIdParam, scopedOrgIds);
            activeEmployeeCount = queryCount(
                    "SELECT COUNT(*) FROM sys_employee e INNER JOIN auth_user u ON u.id = e.user_id AND u.deleted = 0 AND u.status = 'active' WHERE e.status = 'active' AND e.deleted = 0",
                    empScopeSql, empScopeArgs);
        }
        int unregisteredCount = Math.max(0, (activeEmployeeCount != null ? activeEmployeeCount : 0) - (totalCount != null ? totalCount : 0));

        // 获取紧急预警列表（已过期 + 即将过期，以各健康证配置的 warning_days 为准，仅在职+启用员工）
        LocalDate today = LocalDate.now();
        StringBuilder warningSql = new StringBuilder(
                "SELECT c.employee_id, c.employee_name, c.expiry_date, c.status FROM health_certificate c " +
                "INNER JOIN sys_employee e ON e.id = c.employee_id AND e.deleted = 0 AND e.status = 'active' " +
                "INNER JOIN auth_user u ON u.id = e.user_id AND u.deleted = 0 AND u.status = 'active' " +
                "WHERE c.deleted = 0 AND (c.status = 'expired' OR c.status = 'expiring')"
        );
        List<Object> warningArgs = new ArrayList<>();
        appendOrgScopeCondition(warningSql, warningArgs, "c.org_id", orgIdParam, scopedOrgIds);
        warningSql.append(" ORDER BY c.expiry_date ASC LIMIT 10");
        List<HealthCertificateDashboardVO.UrgentWarning> warnings = jdbcTemplate.query(
                warningSql.toString(),
                (rs, rowNum) -> HealthCertificateDashboardVO.UrgentWarning.builder()
                        .employeeId(rs.getLong("employee_id"))
                        .employeeName(rs.getString("employee_name"))
                        .expiryDate(rs.getDate("expiry_date").toLocalDate())
                        .remainDays((int) ChronoUnit.DAYS.between(today, rs.getDate("expiry_date").toLocalDate()))
                        .warningType(rs.getString("status"))
                        .build(),
                warningArgs.toArray()
        );

        return HealthCertificateDashboardVO.builder()
                .totalCount(totalCount != null ? totalCount : 0)
                .validCount(validCount != null ? validCount : 0)
                .expiringCount(expiringCount != null ? expiringCount : 0)
                .expiredCount(expiredCount != null ? expiredCount : 0)
                .unregisteredCount(unregisteredCount)
                .urgentWarnings(warnings)
                .build();
    }

    /** 关键字搜索：员工姓名、健康证编号、员工编号 */
    private void applyKeywordFilter(LambdaQueryWrapper<HealthCertificate> wrapper, String keyword) {
        if (StrUtil.isBlank(keyword)) return;
        // 查询匹配员工编号的员工ID列表
        List<Long> matchedEmployeeIds = jdbcTemplate.queryForList(
                "SELECT id FROM sys_employee WHERE deleted = 0 AND (employee_no LIKE ? OR real_name LIKE ?)",
                Long.class, "%" + keyword + "%", "%" + keyword + "%");
        wrapper.and(w -> {
            w.like(HealthCertificate::getEmployeeName, keyword)
                    .or().like(HealthCertificate::getCertificateNo, keyword);
            if (!matchedEmployeeIds.isEmpty()) {
                w.or().in(HealthCertificate::getEmployeeId, matchedEmployeeIds);
            }
        });
    }

    /** 实体转VO */
    private HealthCertificateVO toVO(HealthCertificate cert) {
        long remainingDays = cert.getExpiryDate() != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), cert.getExpiryDate())
                : 0;

        // 实时计算状态，确保返回给前端的状态始终最新
        String liveStatus = cert.getExpiryDate() != null
                ? calculateStatus(cert.getExpiryDate(), cert.getWarningDays() != null ? cert.getWarningDays() : DEFAULT_WARNING_DAYS)
                : cert.getStatus();

        return HealthCertificateVO.builder()
                .id(cert.getId())
                .employeeId(cert.getEmployeeId())
                .employeeName(cert.getEmployeeName())
                .certificateNo(cert.getCertificateNo())
                .issueDate(cert.getIssueDate())
                .expiryDate(cert.getExpiryDate())
                .certificateImages(cert.getCertificateImages())
                .issuingAuthority(cert.getIssuingAuthority())
                .status(liveStatus)
                .warningDays(cert.getWarningDays())
                .remainingDays(remainingDays)
                .remark(cert.getRemark())
                .orgId(cert.getOrgId())
                .createdAt(cert.getCreatedAt())
                .updatedAt(cert.getUpdatedAt())
                .build();
    }

    private void ensureCertificateAccessible(HealthCertificate cert) {
        if (cert == null || dataScopeService.isAdminUser()) {
            return;
        }
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(cert.getOrgId())) {
            throw new RuntimeException("无权访问该健康证");
        }
    }

    private void applyDataScope(LambdaQueryWrapper<HealthCertificate> wrapper, Long orgId) {
        if (dataScopeService.isAdminUser()) {
            return;
        }
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (orgId != null) {
            if (!scope.isAllowed(orgId)) {
                wrapper.isNull(HealthCertificate::getId);
            }
            return;
        }
        if (!scope.isAllAccess() && scope.getOrgIds().isEmpty()) {
            wrapper.isNull(HealthCertificate::getId);
            return;
        }
        if (!scope.isAllAccess()) {
            wrapper.in(HealthCertificate::getOrgId, scope.getOrgIds());
        }
    }

    private List<Long> normalizeScopedOrgIds(List<Long> orgIds) {
        if (orgIds == null) {
            return null;
        }
        return orgIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private Integer queryCount(String baseSql, StringBuilder scopeSql, List<Object> scopeArgs) {
        List<Object> args = new ArrayList<>(scopeArgs);
        return jdbcTemplate.queryForObject(baseSql + scopeSql, Integer.class, args.toArray());
    }

    /** 格式化剩余天数：负数显示"已过期X天" */
    private String formatRemainingDays(Long remainingDays) {
        if (remainingDays == null) return "0";
        if (remainingDays < 0) return "已过期" + Math.abs(remainingDays) + "天";
        return String.valueOf(remainingDays);
    }

    private void appendOrgScopeCondition(StringBuilder sql, List<Object> args, String column, Long orgId, List<Long> scopedOrgIds) {
        if (orgId != null) {
            sql.append(" AND ").append(column).append(" = ?");
            args.add(orgId);
            return;
        }
        if (scopedOrgIds == null) {
            return;
        }
        if (scopedOrgIds.isEmpty()) {
            sql.append(" AND 1 = 0");
            return;
        }
        sql.append(" AND ").append(column).append(" IN (")
                .append(String.join(",", java.util.Collections.nCopies(scopedOrgIds.size(), "?")))
                .append(")");
        args.addAll(scopedOrgIds);
    }

    @Override
    @DataScope
    public void exportCertificates(HealthCertificateQueryDTO query, HttpServletResponse response) {
        String operationResult = "success";
        String errorMsg = null;
        int exportCount = 0;
        String desc = "导出健康证数据";

        try {
            // 查询数据（复用列表查询逻辑）
            LambdaQueryWrapper<HealthCertificate> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(query.getStatus() != null && !query.getStatus().isBlank(), HealthCertificate::getStatus, query.getStatus());
            wrapper.eq(query.getOrgId() != null, HealthCertificate::getOrgId, query.getOrgId());
            wrapper.in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), HealthCertificate::getOrgId, query.getOrgIds());
            applyKeywordFilter(wrapper, query.getKeyword());
            applyEmployeeVisibilityFilter(wrapper, query);
            if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
                wrapper.isNull(HealthCertificate::getId);
            }
            wrapper.orderByDesc(HealthCertificate::getExpiryDate);
            List<HealthCertificateVO> voList = certificateMapper.selectList(wrapper).stream()
                    .map(this::toVO)
                    .collect(Collectors.toList());
            exportCount = voList.size();

            // 构建Excel
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("健康证数据");

                // 标题行样式
                CellStyle tipStyle = workbook.createCellStyle();
                Font tipFont = workbook.createFont();
                tipFont.setFontHeightInPoints((short) 11);
                tipStyle.setFont(tipFont);
                tipStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
                tipStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                Row tipRow = sheet.createRow(0);
                Cell tipCell = tipRow.createCell(0);
                tipCell.setCellValue("健康证数据导出（共 " + exportCount + " 条）");
                tipCell.setCellStyle(tipStyle);
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

                // 表头行样式
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setFontHeightInPoints((short) 11);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setBorderLeft(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);

                String[] headers = {"序号", "员工姓名", "健康证编号", "发证机构", "发证日期", "到期日期", "剩余天数", "状态", "备注"};
                Row headerRow = sheet.createRow(1);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                // 数据行样式
                CellStyle dataStyle = workbook.createCellStyle();
                dataStyle.setBorderBottom(BorderStyle.THIN);
                dataStyle.setBorderTop(BorderStyle.THIN);
                dataStyle.setBorderLeft(BorderStyle.THIN);
                dataStyle.setBorderRight(BorderStyle.THIN);

                // 状态映射
                java.util.Map<String, String> statusMap = java.util.Map.of(
                        "valid", "有效", "expiring", "即将过期", "expired", "已过期", "pending", "未办理"
                );

                for (int i = 0; i < voList.size(); i++) {
                    HealthCertificateVO vo = voList.get(i);
                    Row row = sheet.createRow(i + 2);
                    String[] values = {
                            String.valueOf(i + 1),
                            vo.getEmployeeName() != null ? vo.getEmployeeName() : "",
                            vo.getCertificateNo() != null ? vo.getCertificateNo() : "",
                            vo.getIssuingAuthority() != null ? vo.getIssuingAuthority() : "",
                            vo.getIssueDate() != null ? vo.getIssueDate().toString() : "",
                            vo.getExpiryDate() != null ? vo.getExpiryDate().toString() : "",
                            formatRemainingDays(vo.getRemainingDays()),
                            statusMap.getOrDefault(vo.getStatus(), vo.getStatus() != null ? vo.getStatus() : ""),
                            vo.getRemark() != null ? vo.getRemark() : ""
                    };
                    for (int j = 0; j < values.length; j++) {
                        Cell cell = row.createCell(j);
                        cell.setCellValue(values[j]);
                        cell.setCellStyle(dataStyle);
                    }
                }

                // 自动列宽
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                    int width = sheet.getColumnWidth(i) + 1000;
                    sheet.setColumnWidth(i, Math.min(width, 8000));
                }

                // 写入响应
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setCharacterEncoding("utf-8");
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                String fileName = URLEncoder.encode("健康证数据导出_" + timestamp, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
                response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");
                workbook.write(response.getOutputStream());

                desc = "导出健康证数据，共 " + exportCount + " 条";
            }
        } catch (IOException e) {
            operationResult = "failure";
            errorMsg = e.getMessage();
            log.error("导出健康证数据失败", e);
            throw new BizException("导出失败：" + e.getMessage());
        } finally {
            auditLogService.log(AuditModule.HEALTH_CERTIFICATE, AuditOperationType.EXPORT,
                    null, null, desc, null, null, operationResult, errorMsg);
        }
    }
}
