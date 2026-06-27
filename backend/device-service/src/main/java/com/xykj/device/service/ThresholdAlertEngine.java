package com.xykj.device.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.device.entity.DeviceAlert;
import com.xykj.device.entity.DeviceAlertRule;
import com.xykj.device.entity.DeviceInfo;
import com.xykj.device.mapper.DeviceAlertMapper;
import com.xykj.device.mapper.DeviceAlertRuleMapper;
import com.xykj.device.mapper.DeviceInfoMapper;
import com.xykj.common.service.NotificationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阈值告警引擎
 * 在数据采集后检查阈值规则，触发告警时自动创建 device_alert 记录
 * 支持持续时长校验：超阈值持续 N 秒后才生成告警
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdAlertEngine {

    private final DeviceAlertRuleMapper alertRuleMapper;
    private final DeviceAlertMapper deviceAlertMapper;
    private final DeviceInfoMapper deviceInfoMapper;
    private final ObjectMapper objectMapper;
    private final NotificationHelper notificationHelper;
    private final com.xykj.device.service.DeviceAlertService deviceAlertService;

    /** 风暴抑制时间窗口（分钟）：同一设备同类型在此窗口内的重复告警将被抑制 */
    private static final int STORM_WINDOW_MINUTES = 5;
    /** 风暴抑制升级阈值：被抑制告警数超过此值时自动提升严重等级 */
    private static final int STORM_ESCALATION_THRESHOLD = 3;

    /**
     * 持续时长跟踪：key = "deviceId:ruleId"，value = 首次超阈值时间
     */
    private final ConcurrentHashMap<String, LocalDateTime> durationTracker = new ConcurrentHashMap<>();

    /**
     * 上次数值正常时间跟踪：key = "deviceId:ruleId"，value = 最近一次数值正常的时间
     * 用于去重：告警已指派但数值曾恢复正常，再次超阈值时应创建新告警
     */
    private final ConcurrentHashMap<String, LocalDateTime> lastNormalTimeTracker = new ConcurrentHashMap<>();

    /**
     * 设备指标值缓存：key = "deviceId:metric"，value = {值, 时间}
     * 用于跨指标 AND 条件评估（如 温度>30 AND 湿度>80）
     */
    private final ConcurrentHashMap<String, CachedMetric> metricValueCache = new ConcurrentHashMap<>();

    /** 解析后的阈值条件（兼容新旧格式） */
    private record ParsedCondition(List<Condition> conditions, String logic, int durationSeconds) {}
    private record Condition(String metric, String operator, double value) {}
    private record ConditionResult(Condition condition, boolean met, BigDecimal currentValue) {}
    private record CachedMetric(BigDecimal value, LocalDateTime timestamp) {
        boolean isStale() {
            return Duration.between(timestamp, LocalDateTime.now()).getSeconds() > 300;
        }
    }

    /**
     * 检查单条传感器数据是否触发阈值告警
     *
     * @param deviceId  设备ID
     * @param dataType  数据类型 (temperature/humidity)
     * @param dataValue 数据值
     */
    public void check(Long deviceId, String dataType, BigDecimal dataValue) {
        List<DeviceAlertRule> rules = alertRuleMapper.selectEnabledThresholdRules();
        if (rules.isEmpty()) {
            return;
        }

        DeviceInfo device = deviceInfoMapper.selectById(deviceId);
        if (device == null || !"active".equals(device.getStatus()) || device.getDeleted() != 0) {
            return;
        }

        // 缓存当前读数，供跨指标 AND 条件评估使用
        metricValueCache.put(deviceId + ":" + dataType,
                new CachedMetric(dataValue, LocalDateTime.now()));

        // 按 deviceType 过滤匹配规则，并检查 deviceIds 范围
        List<DeviceAlertRule> matchedRules = rules.stream()
                .filter(r -> r.getDeviceType() != null && r.getDeviceType().equals(device.getDeviceType()))
                .filter(r -> isDeviceInScope(r, device.getId()))
                .toList();

        for (DeviceAlertRule rule : matchedRules) {
            evaluateRule(rule, device, dataType, dataValue);
        }
    }

    private void evaluateRule(DeviceAlertRule rule, DeviceInfo device,
                              String dataType, BigDecimal dataValue) {
        try {
            ParsedCondition parsed = parseCondition(rule.getConditionJson());

            // 优化：若所有条件都不涉及当前数据类型，跳过
            boolean anyMatch = parsed.conditions().stream()
                    .anyMatch(c -> c.metric().equals(dataType));
            if (!anyMatch) {
                return;
            }

            // 逐条件评估：当前指标用传入值，其他指标用缓存值
            List<ConditionResult> results = new ArrayList<>();
            for (Condition cond : parsed.conditions()) {
                BigDecimal condValue;
                if (cond.metric().equals(dataType)) {
                    condValue = dataValue;
                } else {
                    CachedMetric cached = metricValueCache.get(device.getId() + ":" + cond.metric());
                    if (cached == null || cached.isStale()) {
                        // 无数据或数据过期 → 条件无法满足
                        results.add(new ConditionResult(cond, false, null));
                        continue;
                    }
                    condValue = cached.value();
                }
                BigDecimal threshold = BigDecimal.valueOf(cond.value());
                boolean met = evaluateCondition(condValue, cond.operator(), threshold);
                results.add(new ConditionResult(cond, met, condValue));
            }

            // 应用 AND/OR 逻辑
            boolean triggered;
            if ("or".equals(parsed.logic())) {
                triggered = results.stream().anyMatch(ConditionResult::met);
            } else {
                triggered = results.stream().allMatch(ConditionResult::met);
            }

            String trackerKey = device.getId() + ":" + rule.getId();

            if (triggered) {
                if (parsed.durationSeconds() > 0) {
                    // 有持续时长要求：记录首次触发时间，达标后才告警
                    LocalDateTime firstTriggered = durationTracker.computeIfAbsent(trackerKey,
                            k -> LocalDateTime.now());
                    long elapsedSeconds = Duration.between(firstTriggered, LocalDateTime.now()).getSeconds();
                    if (elapsedSeconds >= parsed.durationSeconds()) {
                        createThresholdAlert(rule, device, results, parsed.logic(), trackerKey);
                        durationTracker.remove(trackerKey);
                    } else {
                        log.debug("阈值超限但未达持续时长: deviceId={}, ruleId={}, 已持续{}s/需{}s",
                                device.getId(), rule.getId(), elapsedSeconds, parsed.durationSeconds());
                    }
                } else {
                    // 无持续时长要求：立即告警
                    createThresholdAlert(rule, device, results, parsed.logic(), trackerKey);
                }
            } else {
                // 数值恢复正常：清除跟踪，记录正常时间，自动关闭该规则对应的未指派告警
                durationTracker.remove(trackerKey);
                lastNormalTimeTracker.put(trackerKey, LocalDateTime.now());
                autoCloseUnassignedAlerts(device.getId(), rule.getId());
            }
        } catch (Exception e) {
            log.warn("阈值规则评估失败: ruleId={}, error={}", rule.getId(), e.getMessage());
        }
    }

    /**
     * 解析条件 JSON，兼容新旧格式
     * 新格式: { "logic": "and|or", "conditions": [{metric, operator, value}], "duration": 60 }
     * 旧格式: { "metric": "temperature", "operator": ">", "value": 37.5, "duration": 60 }
     */
    @SuppressWarnings("unchecked")
    private ParsedCondition parseCondition(String conditionJson) throws Exception {
        Map<String, Object> raw = objectMapper.readValue(conditionJson, new TypeReference<>() {});

        String logic;
        List<Condition> conditions;
        int durationSeconds;

        if (raw.containsKey("conditions") && raw.get("conditions") instanceof List) {
            // 新格式
            logic = raw.get("logic") != null ? raw.get("logic").toString() : "and";
            List<Map<String, Object>> condList = (List<Map<String, Object>>) raw.get("conditions");
            conditions = new ArrayList<>();
            for (Map<String, Object> c : condList) {
                conditions.add(new Condition(
                        (String) c.get("metric"),
                        (String) c.get("operator"),
                        ((Number) c.get("value")).doubleValue()));
            }
            durationSeconds = raw.get("duration") != null
                    ? ((Number) raw.get("duration")).intValue() : 0;
        } else {
            // 旧格式（单条件）
            logic = "and";
            conditions = List.of(new Condition(
                    (String) raw.get("metric"),
                    (String) raw.get("operator"),
                    ((Number) raw.get("value")).doubleValue()));
            durationSeconds = raw.get("duration") != null
                    ? ((Number) raw.get("duration")).intValue() : 0;
        }

        return new ParsedCondition(conditions, logic, durationSeconds);
    }

    private boolean evaluateCondition(BigDecimal value, String operator, BigDecimal threshold) {
        return switch (operator) {
            case ">"  -> value.compareTo(threshold) > 0;
            case "<"  -> value.compareTo(threshold) < 0;
            case ">=" -> value.compareTo(threshold) >= 0;
            case "<=" -> value.compareTo(threshold) <= 0;
            default -> false;
        };
    }

    /**
     * 创建阈值告警（按规则去重：数值曾恢复正常后，允许为新的超限创建告警）
     */
    private void createThresholdAlert(DeviceAlertRule rule, DeviceInfo device,
                                       List<ConditionResult> results, String logic, String trackerKey) {
        // 去重：检查该规则是否有未关闭告警（按 alert_rule_id 精确匹配，避免误关同设备其他规则）
        LambdaQueryWrapper<DeviceAlert> dedupQuery = new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getDeviceId, device.getId())
                .eq(DeviceAlert::getAlertRuleId, rule.getId())
                .notIn(DeviceAlert::getStatus, "closed");
        // 数值曾恢复正常，只检查上次正常之后创建的告警
        LocalDateTime lastNormalTime = lastNormalTimeTracker.get(trackerKey);
        if (lastNormalTime != null) {
            dedupQuery.gt(DeviceAlert::getTriggeredAt, lastNormalTime);
        }

        Long existing = deviceAlertMapper.selectCount(dedupQuery);
        if (existing != null && existing > 0) {
            log.debug("阈值告警已存在，跳过: deviceId={}, ruleId={}", device.getId(), rule.getId());
            return;
        }

        String content = buildAlertContent(device, results, logic);

        DeviceAlert alert = new DeviceAlert();
        alert.setAlertNo("ALT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + String.format("%04d", device.getId() % 10000)
                + "-" + rule.getId());
        alert.setAlertType("threshold");
        alert.setAlertRuleId(rule.getId());
        alert.setAlertLevel(rule.getAlertLevel());
        alert.setDeviceId(device.getId());
        alert.setDeviceName(device.getDeviceName());
        alert.setAlertContent(content);
        alert.setStatus("pending");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setOrgId(device.getOrgId());
        alert.setTenantId(device.getTenantId());
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        deviceAlertMapper.insert(alert);

        // 尝试自动派单
        deviceAlertService.tryAutoDispatch(alert.getId());

        log.info("阈值告警已创建: deviceId={}, content={}", device.getId(), content);

        // 通知规则配置的通知用户（需包含站内信渠道）
        try {
            String notifyUsers = rule.getNotifyUsers();
            String notifyChannels = rule.getNotifyChannels();
            if (notifyUsers != null && !notifyUsers.isBlank()
                    && notifyChannels != null && notifyChannels.contains("system")) {
                List<Long> empIds = NotificationHelper.parseCsvIds(notifyUsers);
                List<Long> authUserIds = notificationHelper.employeeIdsToAuthUserIds(empIds);
                if (!authUserIds.isEmpty()) {
                    notificationHelper.sendBatch(authUserIds, "food_safety_alert", "equipment_alert",
                            "设备阈值告警", content,
                            NotificationHelper.mapAlertLevelToRiskLevel(rule.getAlertLevel()),
                            "设备管理", alert.getId(), "alert",
                            device.getTenantId(), device.getOrgId(),
                            "[{\"label\":\"查看告警\",\"route\":\"/alert?id=" + alert.getId() + "\"}]");
                }
            }
        } catch (Exception e) {
            log.warn("阈值告警通知发送失败: deviceId={}, ruleId={}", device.getId(), rule.getId(), e);
        }

        // 告警风暴检测：检查时间窗口内同设备同规则是否有未关闭告警
        LocalDateTime stormWindowStart = LocalDateTime.now().minusMinutes(STORM_WINDOW_MINUTES);
        List<DeviceAlert> recentAlerts = deviceAlertMapper.selectList(
                new LambdaQueryWrapper<DeviceAlert>()
                        .eq(DeviceAlert::getDeviceId, device.getId())
                        .eq(DeviceAlert::getAlertType, alert.getAlertType())
                        .eq(DeviceAlert::getAlertRuleId, rule.getId())
                        .ne(DeviceAlert::getId, alert.getId())
                        .ge(DeviceAlert::getTriggeredAt, stormWindowStart)
                        .notIn(DeviceAlert::getStatus, "closed")
                        .eq(DeviceAlert::getDeleted, 0));

        if (!recentAlerts.isEmpty()) {
            // 找到原告警：优先找同规则的非抑制告警，否则找最早的非抑制告警
            DeviceAlert primaryAlert = recentAlerts.stream()
                    .filter(a -> a.getSuppressed() == null || a.getSuppressed() == 0)
                    .findFirst()
                    .orElse(null);

            if (primaryAlert != null) {
                // 将当前告警标记为被抑制
                alert.setSuppressed(1);
                alert.setStormGroupId(primaryAlert.getStormGroupId() != null
                        ? primaryAlert.getStormGroupId()
                        : "STM-" + primaryAlert.getId());
                deviceAlertMapper.updateById(alert);

                // 递增原告警的被抑制计数
                int newCount = (primaryAlert.getSuppressedCount() != null ? primaryAlert.getSuppressedCount() : 0) + 1;
                primaryAlert.setSuppressedCount(newCount);
                if (primaryAlert.getStormGroupId() == null) {
                    primaryAlert.setStormGroupId("STM-" + primaryAlert.getId());
                }

                // 风暴升级：被抑制数超过阈值时提升告警等级
                if (newCount >= STORM_ESCALATION_THRESHOLD) {
                    String escalated = escalateAlertLevel(primaryAlert.getAlertLevel());
                    if (!escalated.equals(primaryAlert.getAlertLevel())) {
                        log.info("告警风暴升级: alertId={}, {} → {}, suppressedCount={}",
                                primaryAlert.getId(), primaryAlert.getAlertLevel(), escalated, newCount);
                        primaryAlert.setAlertLevel(escalated);
                    }
                }
                deviceAlertMapper.updateById(primaryAlert);

                log.info("告警被风暴抑制: alertId={}, primaryAlertId={}, stormGroupId={}, suppressedCount={}",
                        alert.getId(), primaryAlert.getId(), primaryAlert.getStormGroupId(), newCount);
            }
        }
    }

    /**
     * 构建告警内容（仅显示满足的条件）
     * 单条件：设备「X」温度当前值 38.5℃，超出阈值 >37.5℃
     * 多条件：设备「X」温度 38.5℃ > 37.5℃ 且 湿度 85%RH > 80%RH
     */
    private String buildAlertContent(DeviceInfo device, List<ConditionResult> results, String logic) {
        String logicWord = "or".equals(logic) ? " 或 " : " 且 ";

        // 只保留满足的条件（OR 时部分满足即可触发，不满足的条件不展示）
        List<ConditionResult> metResults = results.stream()
                .filter(r -> r.met() && r.currentValue() != null)
                .toList();

        if (metResults.size() <= 1) {
            ConditionResult r = metResults.isEmpty() ? null : metResults.get(0);
            if (r != null) {
                String metricName = getMetricName(r.condition().metric());
                String unit = getUnit(r.condition().metric());
                return String.format("设备「%s」%s当前值 %s%s，超出阈值 %s%s",
                        device.getDeviceName(), metricName, r.currentValue(), unit,
                        r.condition().operator(), BigDecimal.valueOf(r.condition().value()));
            }
        }

        List<String> parts = new ArrayList<>();
        for (ConditionResult r : metResults) {
            String metricName = getMetricName(r.condition().metric());
            String unit = getUnit(r.condition().metric());
            parts.add(String.format("%s %s%s %s %s%s",
                    metricName, r.currentValue(), unit, r.condition().operator(),
                    BigDecimal.valueOf(r.condition().value()), unit));
        }

        return String.format("设备「%s」%s，超出阈值", device.getDeviceName(), String.join(logicWord, parts));
    }

    /**
     * 自动关闭该规则对应的未指派告警（数值恢复正常时调用）
     * 按 alert_rule_id 精确匹配，避免误关同设备其他规则的告警
     */
    private void autoCloseUnassignedAlerts(Long deviceId, Long ruleId) {
        List<DeviceAlert> alerts = deviceAlertMapper.selectList(
                new LambdaQueryWrapper<DeviceAlert>()
                        .eq(DeviceAlert::getDeviceId, deviceId)
                        .eq(DeviceAlert::getAlertRuleId, ruleId)
                        .eq(DeviceAlert::getStatus, "pending")
                        .eq(DeviceAlert::getDeleted, 0));
        for (DeviceAlert alert : alerts) {
            alert.setStatus("closed");
            alert.setUpdatedAt(LocalDateTime.now());
            deviceAlertMapper.updateById(alert);
            log.info("自动关闭未指派告警: alertId={}, alertNo={}, deviceId={}, ruleId={}, 原因=数值恢复正常", alert.getId(), alert.getAlertNo(), deviceId, ruleId);
        }
    }

    private String mapAlertType(String metric) {
        return switch (metric) {
            case "temperature" -> "temp_abnormal";
            case "humidity" -> "humidity_abnormal";
            case "gas" -> "gas_abnormal";
            default -> "threshold";
        };
    }

    /**
     * 告警等级升级：info → warning → error → critical（已到 critical 不再升级）
     */
    private String escalateAlertLevel(String currentLevel) {
        return switch (currentLevel) {
            case "info" -> "warning";
            case "warning" -> "error";
            case "error" -> "critical";
            default -> currentLevel; // critical 或未知等级不再升级
        };
    }
    private String getMetricName(String metric) {
        return switch (metric) {
            case "temperature" -> "温度";
            case "humidity" -> "湿度";
            case "gas" -> "气体浓度";
            default -> metric;
        };
    }

    private String getUnit(String metric) {
        return switch (metric) {
            case "temperature" -> "℃";
            case "humidity" -> "%RH";
            case "gas" -> "ppm";
            default -> "";
        };
    }

    /**
     * 检查设备是否在规则的适用范围内
     * deviceIds 为空 → 该规则不对任何设备生效
     * deviceIds 非空 → 检查 deviceId 是否在列表中
     */
    private boolean isDeviceInScope(DeviceAlertRule rule, Long deviceId) {
        if (rule.getDeviceIds() == null || rule.getDeviceIds().isBlank()) {
            return false;
        }
        return Arrays.stream(rule.getDeviceIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equals(String.valueOf(deviceId)));
    }
}
