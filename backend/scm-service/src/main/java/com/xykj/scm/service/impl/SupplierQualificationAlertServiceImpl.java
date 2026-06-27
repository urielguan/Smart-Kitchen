package com.xykj.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.service.AuditLogService;
import com.xykj.scm.entity.Supplier;
import com.xykj.scm.mapper.SupplierMapper;
import com.xykj.scm.service.SupplierQualificationAlertService;
import com.xykj.scm.support.SupplierQualificationStatusSupport;
import com.xykj.scm.vo.SupplierQualificationAlertVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商资质临期提醒服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierQualificationAlertServiceImpl implements SupplierQualificationAlertService {

    private static final Set<String> PURCHASE_ALERT_PERMISSION_CODES = Set.of(
            "purchasePlan",
            "purchasePlan:create",
            "purchasePlan:edit",
            "purchasePlan:generateOrder",
            "purchase",
            "purchase:create",
            "purchase:edit",
            "purchase:approve"
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SupplierMapper supplierMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @Override
    public List<SupplierQualificationAlertVO> listCurrentAlerts(int limit) {
        if (!canCurrentUserReceiveAlerts()) {
            return Collections.emptyList();
        }
        LocalDate today = LocalDate.now();
        LocalDateTime generatedAt = LocalDateTime.now();
        return buildCurrentAlerts(loadCurrentOrgSuppliers(), today, generatedAt, limit);
    }

    @Override
    public int countCurrentAlerts() {
        if (!canCurrentUserReceiveAlerts()) {
            return 0;
        }
        return buildCurrentAlerts(loadCurrentOrgSuppliers(), LocalDate.now(), LocalDateTime.now(), null).size();
    }

    @Override
    public void scanAndRecordDailyAlerts() {
        LocalDate today = LocalDate.now();
        LocalDateTime generatedAt = LocalDateTime.now();
        List<SupplierQualificationAlertVO> alerts = buildCurrentAlerts(loadAllSuppliers(), today, generatedAt, null);
        if (alerts.isEmpty()) {
            log.info("供应商资质临期巡检完成，当前无临期提醒");
            return;
        }

        Map<ReminderScopeKey, List<ReminderRecipient>> recipientsByScope = queryRecipientsByScope(
                alerts.stream()
                        .map(this::toScopeKey)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
        );

        int remindCount = 0;
        for (SupplierQualificationAlertVO alert : alerts) {
            ReminderScopeKey scopeKey = toScopeKey(alert);
            if (scopeKey == null) {
                continue;
            }
            List<ReminderRecipient> recipients = recipientsByScope.getOrDefault(scopeKey, Collections.emptyList());
            logReminderAudit(alert, recipients);
            remindCount++;
        }
        log.info("供应商资质临期巡检完成，生成提醒留痕数量: {}", remindCount);
    }

    private List<SupplierQualificationAlertVO> buildCurrentAlerts(
            List<Supplier> suppliers,
            LocalDate today,
            LocalDateTime generatedAt,
            Integer limit
    ) {
        if (CollectionUtils.isEmpty(suppliers)) {
            return Collections.emptyList();
        }
        List<SupplierQualificationAlertVO> alerts = new ArrayList<>();
        for (Supplier supplier : suppliers) {
            SupplierQualificationAlertVO alert = buildAlert(supplier, today, generatedAt);
            if (alert != null) {
                alerts.add(alert);
            }
        }
        alerts.sort(Comparator
                .comparing(this::resolveAlertPriorityDays)
                .thenComparing(SupplierQualificationAlertVO::getSupplierName, Comparator.nullsLast(String::compareTo))
                .thenComparing(SupplierQualificationAlertVO::getSupplierCode, Comparator.nullsLast(String::compareTo)));
        if (limit != null && limit > 0 && alerts.size() > limit) {
            return new ArrayList<>(alerts.subList(0, limit));
        }
        return alerts;
    }

    private SupplierQualificationAlertVO buildAlert(Supplier supplier, LocalDate today, LocalDateTime generatedAt) {
        String licenseStatus = SupplierQualificationStatusSupport.resolveStatus(supplier.getLicenseExpiresAt(), today);
        String foodLicenseStatus = SupplierQualificationStatusSupport.resolveStatus(supplier.getFoodLicenseExpiresAt(), today);
        Integer licenseRemainingDays = SupplierQualificationStatusSupport.resolveRemainingDays(supplier.getLicenseExpiresAt(), today);
        Integer foodLicenseRemainingDays = SupplierQualificationStatusSupport.resolveRemainingDays(supplier.getFoodLicenseExpiresAt(), today);

        List<String> qualificationNames = new ArrayList<>();
        List<String> qualificationSummaries = new ArrayList<>();
        Integer daysRemaining = null;

        if (SupplierQualificationStatusSupport.STATUS_NEAR_EXPIRE.equals(licenseStatus)) {
            qualificationNames.add("营业执照");
            qualificationSummaries.add(buildQualificationSummary("营业执照", licenseRemainingDays, supplier.getLicenseExpiresAt()));
            daysRemaining = minDays(daysRemaining, licenseRemainingDays);
        }
        if (SupplierQualificationStatusSupport.STATUS_NEAR_EXPIRE.equals(foodLicenseStatus)) {
            qualificationNames.add("食品经营/生产许可证");
            qualificationSummaries.add(buildQualificationSummary("食品经营/生产许可证", foodLicenseRemainingDays, supplier.getFoodLicenseExpiresAt()));
            daysRemaining = minDays(daysRemaining, foodLicenseRemainingDays);
        }

        if (qualificationNames.isEmpty()) {
            return null;
        }

        SupplierQualificationAlertVO vo = new SupplierQualificationAlertVO();
        vo.setSupplierId(supplier.getId());
        vo.setSupplierCode(supplier.getSupplierCode());
        vo.setSupplierName(supplier.getSupplierName());
        vo.setOrgId(supplier.getOrgId());
        vo.setTenantId(supplier.getTenantId());
        vo.setTitle("供应商资质临期提醒");
        vo.setContent("供应商【" + supplier.getSupplierName() + "】的" + String.join("；", qualificationSummaries) + "，请及时跟进换证更新。");
        vo.setDaysRemaining(daysRemaining);
        vo.setQualificationNames(qualificationNames);
        vo.setLicenseExpiresAt(formatDate(supplier.getLicenseExpiresAt()));
        vo.setLicenseExpiryStatus(licenseStatus);
        vo.setLicenseRemainingDays(licenseRemainingDays);
        vo.setFoodLicenseExpiresAt(formatDate(supplier.getFoodLicenseExpiresAt()));
        vo.setFoodLicenseExpiryStatus(foodLicenseStatus);
        vo.setFoodLicenseRemainingDays(foodLicenseRemainingDays);
        vo.setGeneratedAt(formatDateTime(generatedAt));
        return vo;
    }

    private String buildQualificationSummary(String qualificationName, Integer daysRemaining, LocalDateTime expiresAt) {
        String expiryDate = formatDate(expiresAt);
        if (daysRemaining != null && daysRemaining == 0) {
            return qualificationName + "今日到期（" + expiryDate + "）";
        }
        return qualificationName + "将在" + daysRemaining + "天后到期（" + expiryDate + "）";
    }

    private Integer resolveAlertPriorityDays(SupplierQualificationAlertVO alert) {
        return alert.getDaysRemaining() == null ? Integer.MAX_VALUE : alert.getDaysRemaining();
    }

    private Integer minDays(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return Math.min(current, candidate);
    }

    private List<Supplier> loadCurrentOrgSuppliers() {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserContext.getOrgId() != null, Supplier::getOrgId, UserContext.getOrgId())
                .eq(UserContext.getTenantId() != null, Supplier::getTenantId, UserContext.getTenantId())
                .orderByDesc(Supplier::getUpdatedAt)
                .orderByDesc(Supplier::getId);
        return supplierMapper.selectList(wrapper);
    }

    private List<Supplier> loadAllSuppliers() {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Supplier::getUpdatedAt)
                .orderByDesc(Supplier::getId);
        return supplierMapper.selectList(wrapper);
    }

    private boolean canCurrentUserReceiveAlerts() {
        if (isAdminUser()) {
            return true;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
        }
        String placeholders = String.join(",", Collections.nCopies(PURCHASE_ALERT_PERMISSION_CODES.size(), "?"));
        String sql = """
                SELECT COUNT(DISTINCT p.permission_code)
                  FROM auth_permission p
                  JOIN auth_role_permission rp ON rp.permission_id = p.id
                  JOIN auth_role r ON r.id = rp.role_id
                  JOIN auth_user_role ur ON ur.role_id = r.id
                 WHERE ur.user_id = ?
                   AND p.status = 'active'
                   AND r.deleted = 0
                   AND r.status = 'active'
                   AND p.permission_code IN (%s)
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.addAll(PURCHASE_ALERT_PERMISSION_CODES);
        Integer count = jdbcTemplate.queryForObject(sql, params.toArray(), Integer.class);
        return count != null && count > 0;
    }

    private boolean isAdminUser() {
        return UserContext.getUsername() != null
                && "admin".equalsIgnoreCase(UserContext.getUsername().trim());
    }

    private Map<ReminderScopeKey, List<ReminderRecipient>> queryRecipientsByScope(Set<ReminderScopeKey> scopeKeys) {
        if (CollectionUtils.isEmpty(scopeKeys)) {
            return Collections.emptyMap();
        }
        Set<Long> orgIds = scopeKeys.stream()
                .map(ReminderScopeKey::orgId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (orgIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String orgPlaceholders = String.join(",", Collections.nCopies(orgIds.size(), "?"));
        String permissionPlaceholders = String.join(",", Collections.nCopies(PURCHASE_ALERT_PERMISSION_CODES.size(), "?"));
        String sql = """
                SELECT DISTINCT
                       COALESCE(e.org_id, u.org_id) AS orgId,
                       u.tenant_id AS tenantId,
                       u.id AS userId,
                       u.username AS username,
                       COALESCE(NULLIF(e.real_name, ''), NULLIF(u.real_name, ''), u.username) AS realName
                  FROM auth_user u
                  JOIN auth_user_role ur ON ur.user_id = u.id
                  JOIN auth_role r ON r.id = ur.role_id
                  JOIN auth_role_permission rp ON rp.role_id = r.id
                  JOIN auth_permission p ON p.id = rp.permission_id
                  LEFT JOIN sys_employee e ON e.user_id = u.id AND e.deleted = 0 AND e.status = 'active'
                 WHERE u.deleted = 0
                   AND u.status = 'active'
                   AND r.deleted = 0
                   AND r.status = 'active'
                   AND p.status = 'active'
                   AND COALESCE(e.org_id, u.org_id) IN (%s)
                   AND p.permission_code IN (%s)
                """.formatted(orgPlaceholders, permissionPlaceholders);

        List<Object> params = new ArrayList<>();
        params.addAll(orgIds);
        params.addAll(PURCHASE_ALERT_PERMISSION_CODES);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<ReminderScopeKey, List<ReminderRecipient>> recipientMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            ReminderScopeKey scopeKey = new ReminderScopeKey(
                    toLong(row.get("orgId")),
                    toLong(row.get("tenantId"))
            );
            if (!scopeKeys.contains(scopeKey)) {
                continue;
            }
            recipientMap.computeIfAbsent(scopeKey, key -> new ArrayList<>())
                    .add(new ReminderRecipient(
                            toLong(row.get("userId")),
                            asString(row.get("username")),
                            asString(row.get("realName"))
                    ));
        }
        return recipientMap;
    }

    private void logReminderAudit(SupplierQualificationAlertVO alert, List<ReminderRecipient> recipients) {
        ReminderScopeKey scopeKey = toScopeKey(alert);
        if (scopeKey == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("supplierId", alert.getSupplierId());
        payload.put("supplierCode", alert.getSupplierCode());
        payload.put("supplierName", alert.getSupplierName());
        payload.put("qualificationNames", alert.getQualificationNames());
        payload.put("daysRemaining", alert.getDaysRemaining());
        payload.put("licenseExpiresAt", alert.getLicenseExpiresAt());
        payload.put("licenseExpiryStatus", alert.getLicenseExpiryStatus());
        payload.put("licenseRemainingDays", alert.getLicenseRemainingDays());
        payload.put("foodLicenseExpiresAt", alert.getFoodLicenseExpiresAt());
        payload.put("foodLicenseExpiryStatus", alert.getFoodLicenseExpiryStatus());
        payload.put("foodLicenseRemainingDays", alert.getFoodLicenseRemainingDays());
        payload.put("reminderDate", alert.getGeneratedAt());
        payload.put("reminderChannel", "station_message");
        payload.put("recipientCount", recipients == null ? 0 : recipients.size());
        payload.put("recipients", recipients == null ? Collections.emptyList() : recipients);

        runWithSystemContext(scopeKey, () -> auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.UPDATE,
                alert.getSupplierId(),
                alert.getSupplierCode(),
                "供应商资质临期站内提醒推送：" + alert.getSupplierName(),
                null,
                toJson(payload)
        ));
    }

    private void runWithSystemContext(ReminderScopeKey scopeKey, Runnable action) {
        if (scopeKey == null) {
            action.run();
            return;
        }
        if (UserContext.get() != null) {
            action.run();
            return;
        }
        UserContext context = new UserContext();
        context.setUserId(0L);
        context.setUsername("system");
        context.setRealName("系统任务");
        context.setOrgId(scopeKey.orgId());
        context.setTenantId(scopeKey.tenantId());
        UserContext.set(context);
        try {
            action.run();
        } finally {
            UserContext.clear();
        }
    }

    private ReminderScopeKey toScopeKey(SupplierQualificationAlertVO alert) {
        if (alert == null || alert.getSupplierId() == null) {
            return null;
        }
        return new ReminderScopeKey(alert.getOrgId(), alert.getTenantId());
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? null : value.toLocalDate().format(DATE_FORMATTER);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("序列化供应商资质临期提醒数据失败: {}", ex.getMessage());
            return null;
        }
    }

    private record ReminderScopeKey(Long orgId, Long tenantId) {
    }

    private record ReminderRecipient(Long userId, String username, String realName) {
    }
}
