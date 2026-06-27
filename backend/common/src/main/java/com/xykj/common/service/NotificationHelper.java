package com.xykj.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 统一消息通知写入工具
 * 供 device-service / wms-service / sys-service 通过 common 模块共享
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationHelper {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 发送通知给单个 auth_user
     */
    public void send(Long userId, String category, String subCategory,
                     String title, String summary, String riskLevel,
                     String sourceModule, Long relatedBusinessId, String relatedBusinessType,
                     Long tenantId, Long orgId, String executableActions) {
        try {
            String messageId = generateMessageId();
            jdbcTemplate.update(
                    "INSERT INTO sys_notification (message_id, tenant_id, user_id, category, sub_category, " +
                            "title, summary, risk_level, source_module, related_business_id, related_business_type, " +
                            "org_id, push_channels, executable_actions, allow_delete, created_by) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[\"in_app\"]', ?, 1, ?)",
                    messageId, tenantId, userId, category, subCategory,
                    title, summary, riskLevel, sourceModule, relatedBusinessId, relatedBusinessType,
                    orgId, executableActions, userId);
        } catch (Exception e) {
            log.error("发送通知失败: userId={}, title={}", userId, title, e);
        }
    }

    /**
     * 发送通知给多个 auth_user（同一内容，每人一条）
     */
    public void sendBatch(List<Long> authUserIds, String category, String subCategory,
                          String title, String summary, String riskLevel,
                          String sourceModule, Long relatedBusinessId, String relatedBusinessType,
                          Long tenantId, Long orgId, String executableActions) {
        if (authUserIds == null || authUserIds.isEmpty()) return;
        for (Long userId : authUserIds) {
            send(userId, category, subCategory, title, summary, riskLevel,
                    sourceModule, relatedBusinessId, relatedBusinessType,
                    tenantId, orgId, executableActions);
        }
    }

    /**
     * sys_employee.id -> auth_user.id（仅在职员工）
     */
    public Long employeeIdToAuthUserId(Long employeeId) {
        if (employeeId == null) return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT user_id FROM sys_employee WHERE id = ? AND deleted = 0 AND status = 'active'",
                    Long.class, employeeId);
        } catch (Exception e) {
            log.warn("employeeId -> authUserId 转换失败: employeeId={}", employeeId, e);
            return null;
        }
    }

    /**
     * sys_employee.id 列表 -> auth_user.id 列表
     */
    public List<Long> employeeIdsToAuthUserIds(List<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) return List.of();
        List<Long> result = new ArrayList<>();
        for (Long empId : employeeIds) {
            Long authUserId = employeeIdToAuthUserId(empId);
            if (authUserId != null) {
                result.add(authUserId);
            }
        }
        return result;
    }

    /**
     * 生成唯一 message_id: MSG-YYYYMMDD-NNNNN-RAND
     */
    public String generateMessageId() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int random = ThreadLocalRandom.current().nextInt(10000, 99999);
        return "MSG-" + date + "-" + random;
    }

    /**
     * 告警级别 -> 风险等级映射
     */
    public static String mapAlertLevelToRiskLevel(String alertLevel) {
        if (alertLevel == null) return "normal";
        return switch (alertLevel) {
            case "critical" -> "severe";
            case "error" -> "high";
            case "warning" -> "attention";
            default -> "normal";
        };
    }

    /**
     * 解析逗号分隔的 ID 字符串
     */
    public static List<Long> parseCsvIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String s : csv.split(",")) {
            try {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    ids.add(Long.parseLong(trimmed));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }
}
