package com.xykj.device.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.device.config.CloudMonitorConfig;
import com.xykj.device.dto.DataLogReceiveDTO;
import com.xykj.device.entity.DeviceAlert;
import com.xykj.device.entity.DeviceAlertRule;
import com.xykj.device.entity.DeviceInfo;
import com.xykj.device.mapper.DeviceAlertMapper;
import com.xykj.device.mapper.DeviceAlertRuleMapper;
import com.xykj.device.mapper.DeviceInfoMapper;
import com.xykj.device.service.CloudMetric;
import com.xykj.device.service.CloudMonitorClientService;
import com.xykj.device.service.DeviceDataLogService;
import com.xykj.device.service.ThresholdAlertEngine;
import com.xykj.common.service.NotificationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * 云监控平台数据采集定时任务
 * 定时轮询过程云监控平台，获取温湿度传感器数据并写入 device_data_log
 * 支持 Nacos 配置动态刷新采集间隔
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudMonitorScheduler {

    private final CloudMonitorConfig config;
    private final CloudMonitorClientService clientService;
    private final DeviceDataLogService dataLogService;
    private final DeviceInfoMapper deviceInfoMapper;
    private final DeviceAlertMapper deviceAlertMapper;
    private final DeviceAlertRuleMapper alertRuleMapper;
    private final ObjectMapper objectMapper;
    private final ThresholdAlertEngine thresholdAlertEngine;
    private final TaskScheduler taskScheduler;
    private final NotificationHelper notificationHelper;
    private final com.xykj.device.service.DeviceAlertService deviceAlertService;

    /** 已解析的设备映射：serialNumber → ResolvedMapping */
    private final Map<String, ResolvedMapping> resolvedMappings = new ConcurrentHashMap<>();

    /** 动态调度任务 */
    private volatile ScheduledFuture<?> scheduledFuture;

    /** 上次实际轮询时间 */
    private volatile long lastPollTimeMs = 0;

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            return;
        }
        resolveDeviceMappings();
        // 使用固定短间隔调度，内部按配置的实际间隔控制是否执行
        scheduledFuture = taskScheduler.scheduleAtFixedRate(
                this::pollIfReady, java.time.Duration.ofSeconds(10));
        log.info("云监控采集任务已启动, 配置间隔={}ms", config.getPollRateMs());
    }

    @PreDestroy
    public void destroy() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    /**
     * 按 Nacos 动态配置的间隔判断是否该执行轮询
     * 配置变更后自动生效，无需重启或重新调度
     */
    private void pollIfReady() {
        long rate = config.getPollRateMs();
        if (rate <= 0) {
            rate = 300000;
        }
        long now = System.currentTimeMillis();
        if (now - lastPollTimeMs < rate) {
            return;
        }
        lastPollTimeMs = now;
        try {
            pollCloudMetrics();
        } catch (Exception e) {
            log.warn("云监控轮询执行异常: {}", e.getMessage());
        }
    }

    /**
     * 启动时将 deviceCode 解析为 deviceId
     */
    private void resolveDeviceMappings() {
        for (CloudMonitorConfig.DeviceMapping dm : config.getDevices()) {
            try {
                DeviceInfo device = deviceInfoMapper.selectOne(
                        new LambdaQueryWrapper<DeviceInfo>()
                                .eq(DeviceInfo::getDeviceCode, dm.getDeviceCode())
                                .last("LIMIT 1"));

                if (device == null) {
                    log.warn("云监控设备映射未找到: deviceCode={}, 请先在设备管理中创建该设备, 跳过", dm.getDeviceCode());
                    continue;
                }

                resolvedMappings.put(dm.getSerialNumber(),
                        new ResolvedMapping(device.getId(), device.getDeviceCode(), dm.getMetrics()));
                log.info("云监控设备映射成功: serialNumber={}, deviceId={}, deviceCode={}",
                        dm.getSerialNumber(), device.getId(), device.getDeviceCode());
            } catch (Exception e) {
                log.warn("解析云监控设备映射失败: deviceCode={}", dm.getDeviceCode(), e);
            }
        }

        if (resolvedMappings.isEmpty()) {
            log.warn("云监控平台已启用但无有效设备映射，数据采集不会执行");
        }
    }

    /**
     * 定时轮询云监控平台数据
     * 间隔通过 cloud-monitor.poll-rate-ms 配置，支持 Nacos 动态刷新
     */
    public void pollCloudMetrics() {
        if (!config.isEnabled() || resolvedMappings.isEmpty()) {
            return;
        }

        log.debug("云监控数据轮询开始, 设备数: {}", resolvedMappings.size());

        for (Map.Entry<String, ResolvedMapping> entry : resolvedMappings.entrySet()) {
            String serialNumber = entry.getKey();
            ResolvedMapping mapping = entry.getValue();
            try {
                pollDevice(serialNumber, mapping);
            } catch (Exception e) {
                log.warn("云监控轮询失败: serialNumber={}, deviceId={}, error={}",
                        serialNumber, mapping.deviceId, e.getMessage());
            }
        }

        log.debug("云监控数据轮询完成");
    }

    private void pollDevice(String serialNumber, ResolvedMapping mapping) throws Exception {
        // 检查设备状态，停用/删除/故障设备不采集
        DeviceInfo device = deviceInfoMapper.selectById(mapping.deviceId);
        if (device == null || !"active".equals(device.getStatus()) || "fault".equals(device.getOnlineStatus())) {
            return;
        }
        // 1. 查询网关ID
        String gatewayId = clientService.findGatewayId(serialNumber);
        if (gatewayId == null) {
            log.warn("网关ID未找到，跳过: serialNumber={}", serialNumber);
            return;
        }

        // 2. 获取最新指标
        List<CloudMetric> metrics = clientService.fetchLatestMetrics(gatewayId);
        if (metrics.isEmpty()) {
            log.debug("无最新指标数据: serialNumber={}", serialNumber);
            return;
        }

        // 按指标名称索引
        Map<String, CloudMetric> metricsByName = metrics.stream()
                .collect(Collectors.toMap(CloudMetric::getName, m -> m, (a, b) -> a));

        // 3. 先判断设备本轮在线/离线（阈值告警依赖此结果）
        // 优先级：云平台明确离线指标 > 数据时效校验 > 默认在线
        boolean online = true;
        LocalDateTime latestCollectedAt = null;
        long latestMetricTimestamp = 0;
        for (CloudMonitorConfig.MetricMapping mm : mapping.metrics) {
            CloudMetric metric = metricsByName.get(mm.getMetricName());
            // 记录所有指标中最新时间戳
            if (metric != null && metric.getTimestamp() > latestMetricTimestamp) {
                latestMetricTimestamp = metric.getTimestamp();
            }
        }
        // 3.1 云平台明确返回离线指标
        CloudMetric onlineStatusMetric = metricsByName.get("__internal_online_status__");
        if (onlineStatusMetric != null && onlineStatusMetric.getValue() <= 0) {
            online = false;
            log.info("云平台明确返回离线: deviceId={}", mapping.deviceId);
        } else if (latestMetricTimestamp > 0) {
            // 3.2 数据时效校验（Nacos 阈值控制状态）
            int staleThreshold = config.getStaleDataThresholdMinutes();
            if (staleThreshold > 0) {
                latestCollectedAt = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(latestMetricTimestamp), ZoneId.systemDefault());
                LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleThreshold);
                if (latestCollectedAt.isBefore(cutoff)) {
                    online = false;
                    log.info("云监控数据时效过期: deviceId={}, latestCollectedAt={}, threshold={}min",
                            mapping.deviceId, latestCollectedAt, staleThreshold);
                }
            }
        }

        // 4. 写入指标数据 + 阈值告警检查（仅本轮判定为在线时触发，离线设备不触发阈值告警）
        for (CloudMonitorConfig.MetricMapping mm : mapping.metrics) {
            CloudMetric metric = metricsByName.get(mm.getMetricName());
            if (metric == null) {
                continue;
            }

            try {
                DataLogReceiveDTO dto = new DataLogReceiveDTO();
                dto.setDeviceId(mapping.deviceId);
                dto.setDataType(mm.getDataType());
                dto.setDataValue(BigDecimal.valueOf(metric.getValue()));
                dto.setDataUnit(mm.getDataUnit());
                dto.setCollectedAt(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(metric.getTimestamp()), ZoneId.systemDefault()));

                // 记录数据来源
                Map<String, Object> json = new LinkedHashMap<>();
                json.put("source", "cloud-monitor");
                json.put("serialNumber", serialNumber);
                json.put("metricName", mm.getMetricName());
                json.put("rawValue", metric.getValue());
                dto.setDataJson(objectMapper.writeValueAsString(json));

                dataLogService.receive(dto);
            } catch (Exception e) {
                log.warn("云监控数据写入失败: deviceId={}, metric={}, error={}",
                        mapping.deviceId, mm.getMetricName(), e.getMessage());
            }

            // 量程校验 + 阈值告警检查（仅本轮在线时触发）
            if (online && ("temperature".equals(mm.getDataType()) || "humidity".equals(mm.getDataType()))) {
                double v = metric.getValue();
                boolean outOfRange = ("temperature".equals(mm.getDataType()) && (v < -10 || v > 60))
                        || ("humidity".equals(mm.getDataType()) && (v < 0 || v > 100));
                if (outOfRange) {
                    log.warn("数据超出传感器量程: deviceId={}, type={}, value={}", mapping.deviceId, mm.getDataType(), v);
                } else {
                    try {
                        thresholdAlertEngine.check(mapping.deviceId, mm.getDataType(), BigDecimal.valueOf(v));
                    } catch (Exception e) {
                        log.warn("阈值检查异常: deviceId={}, error={}", mapping.deviceId, e.getMessage());
                    }
                }
            }
        }

        // 5. 更新设备在线/离线状态
        markDeviceStatus(mapping.deviceId, online, latestCollectedAt);

        log.debug("云监控数据采集成功: serialNumber={}, deviceId={}, online={}, 写入指标数={}",
                serialNumber, mapping.deviceId, online, mapping.metrics.size());
    }

    /**
     * 更新设备在线/离线状态，并触发告警
     * 直接通过 mapper 更新，避免 UserContext NPE 问题
     * - online=true：标记在线 + 刷新心跳 + 关闭未指派的离线告警
     * - online=false：标记离线 + 按规则阈值判断是否创建离线告警
     */
    private void markDeviceStatus(Long deviceId, boolean online, LocalDateTime latestCollectedAt) {
        try {
            DeviceInfo device = deviceInfoMapper.selectById(deviceId);
            if (device == null) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();

            if (online) {
                if (!"online".equals(device.getOnlineStatus())) {
                    device.setOnlineStatus("online");
                    device.setLastHeartbeatAt(now);
                    deviceInfoMapper.updateById(device);
                    log.info("云监控标记在线: deviceId={}, {} -> online", deviceId, device.getOnlineStatus());
                } else {
                    device.setLastHeartbeatAt(now);
                    deviceInfoMapper.updateById(device);
                }
                // 恢复在线，关闭未指派的离线告警
                autoCloseUnassignedAlerts(deviceId, "offline");
            } else {
                if (!"offline".equals(device.getOnlineStatus())) {
                    device.setOnlineStatus("offline");
                    deviceInfoMapper.updateById(device);
                    log.info("云监控标记离线: deviceId={}, {} -> offline", deviceId, device.getOnlineStatus());
                }
                // 按规则阈值判断是否创建离线告警
                createOfflineAlert(device);
            }
        } catch (Exception e) {
            log.warn("更新云监控设备在线状态失败: deviceId={}, error={}", deviceId, e.getMessage());
        }
    }

    /**
     * 创建离线告警（按规则阈值判断）
     * - 无匹配规则时不告警
     * - 设备离线时长未超过规则的 offlineMinutes 时不告警（从 lastHeartbeatAt 开始计算）
     * - 去重检查上次在线之后的未关闭告警
     */
    private void createOfflineAlert(DeviceInfo device) {
        try {
            // 查找匹配的离线告警规则
            List<DeviceAlertRule> rules = alertRuleMapper.selectList(
                    new LambdaQueryWrapper<DeviceAlertRule>()
                            .eq(DeviceAlertRule::getRuleType, "offline")
                            .eq(DeviceAlertRule::getIsEnabled, 1));
            DeviceAlertRule matchedRule = null;
            for (DeviceAlertRule rule : rules) {
                if (device.getDeviceType() != null && device.getDeviceType().equals(rule.getDeviceType())
                        && isDeviceInScope(rule, device.getId())) {
                    matchedRule = rule;
                    break;
                }
            }
            if (matchedRule == null) {
                log.debug("无匹配的离线告警规则，跳过: deviceId={}, deviceType={}", device.getId(), device.getDeviceType());
                return;
            }

            // 从规则中读取 offlineMinutes 阈值
            long ruleOfflineMinutes = getRuleOfflineMinutes(matchedRule);

            // 从 lastHeartbeatAt 计算离线时长，与 DeviceHeartbeatScheduler 逻辑一致
            if (device.getLastHeartbeatAt() == null
                    || device.getLastHeartbeatAt().isBefore(LocalDateTime.now().minusMinutes(ruleOfflineMinutes))) {
                // lastHeartbeatAt 为空或距现在已超过规则阈值，满足告警条件
            } else {
                log.debug("离线时长未达告警阈值: deviceId={}, lastHeartbeatAt={}, 阈值={}min",
                        device.getId(), device.getLastHeartbeatAt(), ruleOfflineMinutes);
                return;
            }

            // 去重：只检查上次在线之后创建的未关闭告警
            LambdaQueryWrapper<DeviceAlert> dedupQuery = new LambdaQueryWrapper<DeviceAlert>()
                    .eq(DeviceAlert::getDeviceId, device.getId())
                    .eq(DeviceAlert::getAlertType, "offline")
                    .notIn(DeviceAlert::getStatus, "closed");
            if (device.getLastHeartbeatAt() != null) {
                dedupQuery.gt(DeviceAlert::getTriggeredAt, device.getLastHeartbeatAt());
            }
            Long existingAlerts = deviceAlertMapper.selectCount(dedupQuery);
            if (existingAlerts > 0) {
                return;
            }

            DeviceAlert alert = new DeviceAlert();
            alert.setAlertNo("ALT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "-" + String.format("%04d", device.getId() % 10000)
                    + "-" + matchedRule.getId());
            alert.setAlertType("offline");
            alert.setAlertRuleId(matchedRule.getId());
            alert.setAlertLevel(matchedRule.getAlertLevel() != null ? matchedRule.getAlertLevel() : "error");
            alert.setDeviceId(device.getId());
            alert.setDeviceName(device.getDeviceName());
            alert.setAlertContent("设备「" + device.getDeviceName() + "」数据时效过期，云监控判断为离线，离线超过" + ruleOfflineMinutes + "分钟");
            alert.setStatus("pending");
            alert.setTriggeredAt(LocalDateTime.now());
            alert.setOrgId(device.getOrgId());
            alert.setTenantId(device.getTenantId());
            alert.setCreatedAt(LocalDateTime.now());
            alert.setUpdatedAt(LocalDateTime.now());
            deviceAlertMapper.insert(alert);
            log.info("云监控生成离线告警: deviceId={}, alertId={}, ruleId={}, 阈值={}min",
                    device.getId(), alert.getId(), matchedRule.getId(), ruleOfflineMinutes);

            // 尝试自动派单
            deviceAlertService.tryAutoDispatch(alert.getId());

            // 通知规则配置的通知用户（需包含站内信渠道）
            try {
                String notifyUsers = matchedRule.getNotifyUsers();
                String notifyChannels = matchedRule.getNotifyChannels();
                if (notifyUsers != null && !notifyUsers.isBlank()
                        && notifyChannels != null && notifyChannels.contains("system")) {
                    List<Long> empIds = NotificationHelper.parseCsvIds(notifyUsers);
                    List<Long> authUserIds = notificationHelper.employeeIdsToAuthUserIds(empIds);
                    if (!authUserIds.isEmpty()) {
                        notificationHelper.sendBatch(authUserIds, "food_safety_alert", "equipment_alert",
                                "设备离线告警", alert.getAlertContent(),
                                NotificationHelper.mapAlertLevelToRiskLevel(
                                        matchedRule.getAlertLevel() != null ? matchedRule.getAlertLevel() : "error"),
                                "设备管理", alert.getId(), "alert",
                                device.getTenantId(), device.getOrgId(),
                                "[{\"label\":\"查看告警\",\"route\":\"/alert?id=" + alert.getId() + "\"}]");
                    }
                }
            } catch (Exception e) {
                log.warn("离线告警通知发送失败: deviceId={}, ruleId={}", device.getId(), matchedRule.getId(), e);
            }
        } catch (Exception e) {
            log.warn("创建离线告警失败: deviceId={}, error={}", device.getId(), e.getMessage());
        }
    }

    /**
     * 从规则的 conditionJson 中读取 offlineMinutes，默认 3 分钟
     */
    private long getRuleOfflineMinutes(DeviceAlertRule rule) {
        if (rule.getConditionJson() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(rule.getConditionJson());
                if (node.has("offlineMinutes") && node.get("offlineMinutes").isNumber()) {
                    long minutes = node.get("offlineMinutes").asLong();
                    if (minutes > 0) return minutes;
                }
            } catch (Exception e) {
                log.warn("解析规则conditionJson失败: ruleId={}", rule.getId(), e);
            }
        }
        return 3;
    }

    /**
     * 自动关闭未指派的告警（设备恢复在线时调用）
     * 仅关闭 pending 状态（未指派）的告警，已指派/处理中的告警不关闭
     */
    private void autoCloseUnassignedAlerts(Long deviceId, String alertType) {
        try {
            List<DeviceAlert> alerts = deviceAlertMapper.selectList(
                    new LambdaQueryWrapper<DeviceAlert>()
                            .eq(DeviceAlert::getDeviceId, deviceId)
                            .eq(DeviceAlert::getAlertType, alertType)
                            .eq(DeviceAlert::getStatus, "pending")
                            .eq(DeviceAlert::getDeleted, 0));
            for (DeviceAlert alert : alerts) {
                alert.setStatus("closed");
                alert.setUpdatedAt(LocalDateTime.now());
                deviceAlertMapper.updateById(alert);
                log.info("云监控自动关闭未指派告警: alertId={}, alertNo={}, deviceId={}", alert.getId(), alert.getAlertNo(), deviceId);
            }
        } catch (Exception e) {
            log.warn("关闭未指派告警失败: deviceId={}, error={}", deviceId, e.getMessage());
        }
    }

    private boolean isDeviceInScope(DeviceAlertRule rule, Long deviceId) {
        if (rule.getDeviceIds() == null || rule.getDeviceIds().isBlank()) {
            return false;
        }
        return Arrays.stream(rule.getDeviceIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equals(String.valueOf(deviceId)));
    }

    /**
     * 获取云监控管理的设备ID集合
     * 供其他调度器排除这些设备，避免重复管理在线/离线状态
     */
    public Set<Long> getManagedDeviceIds() {
        return resolvedMappings.values().stream()
                .map(ResolvedMapping::deviceId)
                .collect(Collectors.toSet());
    }

    /**
     * 内部解析后的设备映射
     */
    private record ResolvedMapping(
            Long deviceId,
            String deviceCode,
            List<CloudMonitorConfig.MetricMapping> metrics
    ) {}
}
