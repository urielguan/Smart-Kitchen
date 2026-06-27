package com.xykj.device.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xykj.device.entity.DeviceAlert;
import com.xykj.device.entity.DeviceAlertRule;
import com.xykj.device.entity.DeviceInfo;
import com.xykj.device.service.StreamTranscodeService;
import com.xykj.common.service.NotificationHelper;
import com.xykj.device.mapper.DeviceAlertMapper;
import com.xykj.device.mapper.DeviceAlertRuleMapper;
import com.xykj.device.mapper.DeviceInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备心跳检测定时任务
 * - 启动时将所有 online 设备重置为 offline（等待心跳恢复）
 * - 每 60 秒检查所有 online 设备，超时 3 分钟标记为 offline 并生成告警
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceHeartbeatScheduler {

    private final DeviceInfoMapper deviceInfoMapper;
    private final DeviceAlertMapper deviceAlertMapper;
    private final DeviceAlertRuleMapper alertRuleMapper;
    private final StreamTranscodeService streamTranscodeService;
    private final CloudMonitorScheduler cloudMonitorScheduler;
    private final ObjectMapper objectMapper;
    private final NotificationHelper notificationHelper;
    private final com.xykj.device.service.DeviceAlertService deviceAlertService;

    /** 默认心跳超时阈值：3 分钟（无匹配规则时使用） */
    private static final long DEFAULT_OFFLINE_MINUTES = 3;

    /** 设备离线状态变更阈值：2 分钟（心跳超时后标记离线） */
    private static final long OFFLINE_STATUS_THRESHOLD = 2;

    /** 摄像头连续离线检测计数器：deviceId → 连续失败次数 */
    private final ConcurrentHashMap<Long, Integer> cameraOfflineCounter = new ConcurrentHashMap<>();

    /**
     * 启动时重置所有 online 设备为 offline
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(rollbackFor = Exception.class)
    public void onStartup() {
        log.info("心跳检测：启动时重置所有 online 设备为 offline");

        LambdaUpdateWrapper<DeviceInfo> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(DeviceInfo::getOnlineStatus, "online")
                .set(DeviceInfo::getOnlineStatus, "offline");

        int count = deviceInfoMapper.update(null, updateWrapper);
        if (count > 0) {
            log.info("心跳检测：已重置 {} 台设备为 offline", count);
        }
    }

    /**
     * 每 60 秒检测摄像头在线状态：基于 FFmpeg 进程是否存活
     * 独立事务，避免非摄像头检测异常导致摄像头状态更新回滚
     */
    @Scheduled(fixedRate = 60000, initialDelay = 120000)
    @Transactional(rollbackFor = Exception.class)
    public void checkCameraHeartbeat() {
        checkCameraOnlineStatus();
    }

    /**
     * 每 60 秒检测非摄像头设备心跳超时
     * 独立事务，避免影响摄像头状态检测
     */
    @Scheduled(fixedRate = 60000)
    @Transactional(rollbackFor = Exception.class)
    public void checkNonCameraHeartbeatScheduled() {
        checkNonCameraHeartbeat();
    }

    /**
     * 检测摄像头在线状态：基于 FFmpeg 进程是否存活
     */
    private void checkCameraOnlineStatus() {
        LambdaQueryWrapper<DeviceInfo> cameraQuery = new LambdaQueryWrapper<>();
        cameraQuery.eq(DeviceInfo::getDeviceType, "camera")
                .eq(DeviceInfo::getStatus, "active")
                .ne(DeviceInfo::getOnlineStatus, "fault")
                .eq(DeviceInfo::getDeleted, 0);
        List<DeviceInfo> cameras = deviceInfoMapper.selectList(cameraQuery);
        if (cameras.isEmpty()) {
            return;
        }

        // 预加载所有启用的离线规则
        List<DeviceAlertRule> offlineRules = findEnabledOfflineRules();

        for (DeviceInfo camera : cameras) {
            try {
                boolean ffmpegAlive = streamTranscodeService.isTranscoding(camera.getId());
                if (ffmpegAlive) {
                    cameraOfflineCounter.remove(camera.getId());
                    camera.setLastHeartbeatAt(LocalDateTime.now());
                    if ("offline".equals(camera.getOnlineStatus())) {
                        camera.setOnlineStatus("online");
                        autoCloseUnassignedAlerts(camera.getId(), "offline");
                        log.info("摄像头 FFmpeg 检测：标记在线，deviceId={}, deviceName={}", camera.getId(), camera.getDeviceName());
                    }
                    deviceInfoMapper.updateById(camera);
                } else {
                    // FFmpeg 不存活：尝试重启
                    String hlsUrl = streamTranscodeService.restartDeviceTranscode(camera.getId());
                    boolean restartOk = false;
                    if (hlsUrl != null) {
                        // 等待 FFmpeg 初始化，验证进程是否真正存活
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                        restartOk = streamTranscodeService.isTranscoding(camera.getId());
                    }
                    if (restartOk) {
                        // 重启成功，恢复在线
                        cameraOfflineCounter.remove(camera.getId());
                        camera.setOnlineStatus("online");
                        camera.setLastHeartbeatAt(LocalDateTime.now());
                        deviceInfoMapper.updateById(camera);
                        autoCloseUnassignedAlerts(camera.getId(), "offline");
                        log.info("摄像头 FFmpeg 重启成功：恢复在线，deviceId={}, deviceName={}", camera.getId(), camera.getDeviceName());
                    } else {
                        // 重启失败（含进程立即退出的情况）
                        int failCount = cameraOfflineCounter.merge(camera.getId(), 1, Integer::sum);

                        // 1. 状态变更：首次检测失败即标记离线
                        if ("online".equals(camera.getOnlineStatus())) {
                            camera.setOnlineStatus("offline");
                            deviceInfoMapper.updateById(camera);
                            log.info("摄像头 FFmpeg 检测：标记离线，deviceId={}, deviceName={}", camera.getId(), camera.getDeviceName());
                        }

                        // 2. 告警生成：达到规则配置的时长阈值 → 生成告警
                        long alertThreshold = getOfflineMinutes(offlineRules, camera.getDeviceType(), camera.getId());
                        if (failCount >= alertThreshold) {
                            cameraOfflineCounter.remove(camera.getId());
                            createOfflineAlert(camera, "摄像头「" + camera.getDeviceName() + "」FFmpeg 进程已退出，离线超过" + alertThreshold + "分钟", offlineRules);
                            log.info("摄像头离线告警：连续{}次失败（阈值{}），deviceId={}", failCount, alertThreshold, camera.getId());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("摄像头状态检测异常，跳过: deviceId={}, error={}", camera.getId(), e.getMessage());
            }
        }
    }

    /**
     * 检测非摄像头设备的心跳超时
     * 状态变更使用固定短阈值（DEFAULT_OFFLINE_MINUTES），告警使用规则配置的时长
     */
    private void checkNonCameraHeartbeat() {
        // 预加载所有启用的离线规则
        List<DeviceAlertRule> offlineRules = findEnabledOfflineRules();
        LocalDateTime now = LocalDateTime.now();

        // 云监控管理的设备由 CloudMonitorScheduler 自行管理在线/离线状态，排除
        java.util.Set<Long> cloudManagedIds = cloudMonitorScheduler.getManagedDeviceIds();

        // 1. 查询所有 online 的启用非摄像头设备，检查是否应标记离线
        LambdaQueryWrapper<DeviceInfo> onlineQuery = new LambdaQueryWrapper<>();
        onlineQuery.eq(DeviceInfo::getOnlineStatus, "online")
                .ne(DeviceInfo::getDeviceType, "camera")
                .eq(DeviceInfo::getStatus, "active")
                .eq(DeviceInfo::getDeleted, 0);
        List<DeviceInfo> onlineDevices = deviceInfoMapper.selectList(onlineQuery);

        for (DeviceInfo device : onlineDevices) {
            try {
                if (cloudManagedIds.contains(device.getId())) continue;
                long alertMinutes = getOfflineMinutes(offlineRules, device.getDeviceType(), device.getId());
                long statusMinutes = Math.min(OFFLINE_STATUS_THRESHOLD, alertMinutes);
                if (device.getLastHeartbeatAt() == null || device.getLastHeartbeatAt().isBefore(now.minusMinutes(statusMinutes))) {
                    device.setOnlineStatus("offline");
                    deviceInfoMapper.updateById(device);
                    log.info("非摄像头设备标记离线: deviceId={}, deviceName={}, 超时{}分钟", device.getId(), device.getDeviceName(), statusMinutes);
                }
            } catch (Exception e) {
                log.warn("非摄像头设备离线处理异常: deviceId={}, error={}", device.getId(), e.getMessage());
            }
        }

        // 2. 查询所有 offline 的启用非摄像头设备，检查是否恢复在线
        LambdaQueryWrapper<DeviceInfo> recoveryQuery = new LambdaQueryWrapper<>();
        recoveryQuery.eq(DeviceInfo::getOnlineStatus, "offline")
                .ne(DeviceInfo::getDeviceType, "camera")
                .eq(DeviceInfo::getStatus, "active")
                .eq(DeviceInfo::getDeleted, 0);
        List<DeviceInfo> offlineActiveDevices = deviceInfoMapper.selectList(recoveryQuery);

        for (DeviceInfo device : offlineActiveDevices) {
            try {
                if (cloudManagedIds.contains(device.getId())) continue;
                long alertMinutes = getOfflineMinutes(offlineRules, device.getDeviceType(), device.getId());
                long statusMinutes = Math.min(OFFLINE_STATUS_THRESHOLD, alertMinutes);
                if (device.getLastHeartbeatAt() != null && device.getLastHeartbeatAt().isAfter(now.minusMinutes(statusMinutes))) {
                    // 心跳恢复，标记在线并更新最后心跳时间
                    device.setOnlineStatus("online");
                    device.setLastHeartbeatAt(now);
                    deviceInfoMapper.updateById(device);
                    autoCloseUnassignedAlerts(device.getId(), "offline");
                    log.info("非摄像头设备恢复在线: deviceId={}, deviceName={}", device.getId(), device.getDeviceName());
                }
            } catch (Exception e) {
                log.warn("非摄像头设备恢复在线检查异常: deviceId={}, error={}", device.getId(), e.getMessage());
            }
        }

        // 3. 已离线设备：达到规则告警时长则生成告警
        for (DeviceInfo device : offlineActiveDevices) {
            try {
                if (cloudManagedIds.contains(device.getId())) continue;
                if (!"offline".equals(device.getOnlineStatus())) continue; // 已恢复在线的跳过
                long alertMinutes = getOfflineMinutes(offlineRules, device.getDeviceType(), device.getId());
                LocalDateTime alertThreshold = now.minusMinutes(alertMinutes);
                if (device.getLastHeartbeatAt() == null || device.getLastHeartbeatAt().isBefore(alertThreshold)) {
                    createOfflineAlert(device, "设备「" + device.getDeviceName() + "」离线超过" + alertMinutes + "分钟", offlineRules);
                }
            } catch (Exception e) {
                log.warn("非摄像头设备离线告警检查异常: deviceId={}, error={}", device.getId(), e.getMessage());
            }
        }

        // 4. 已离线设备：告警关闭后重新生成
        for (DeviceInfo device : offlineActiveDevices) {
            try {
                if (cloudManagedIds.contains(device.getId())) continue;
                if (!"offline".equals(device.getOnlineStatus())) continue;
                long timeoutMinutes = getOfflineMinutes(offlineRules, device.getDeviceType(), device.getId());
                createOfflineAlert(device, "设备「" + device.getDeviceName() + "」心跳超时" + timeoutMinutes + "分钟，仍处于离线状态", offlineRules);
            } catch (Exception e) {
                log.warn("非摄像头设备离线告警检查异常: deviceId={}, error={}", device.getId(), e.getMessage());
            }
        }
    }

    /**
     * 创建离线告警（无匹配规则时不告警，避免重复）
     * 去重逻辑：只检查上次在线之后创建的告警，支持设备多次离线/在线循环
     */
    private void createOfflineAlert(DeviceInfo device, String content, List<DeviceAlertRule> offlineRules) {
        // 无匹配规则时不告警
        DeviceAlertRule offlineRule = matchRule(offlineRules, device.getDeviceType(), device.getId());
        if (offlineRule == null) {
            log.debug("无匹配的离线告警规则，跳过: deviceId={}, deviceType={}", device.getId(), device.getDeviceType());
            return;
        }

        // 去重：检查本次离线期间是否已有未关闭告警
        LambdaQueryWrapper<DeviceAlert> dedupQuery = new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getDeviceId, device.getId())
                .eq(DeviceAlert::getAlertType, "offline")
                .notIn(DeviceAlert::getStatus, "closed");
        // 设备曾经在线过，只检查上次在线之后创建的告警
        if (device.getLastHeartbeatAt() != null) {
            dedupQuery.gt(DeviceAlert::getTriggeredAt, device.getLastHeartbeatAt());
        }

        Long existingAlerts = deviceAlertMapper.selectCount(dedupQuery);

        if (existingAlerts == 0) {
            DeviceAlert alert = new DeviceAlert();
            alert.setAlertNo("ALT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "-" + String.format("%04d", device.getId() % 10000)
                    + "-" + offlineRule.getId());
            alert.setAlertType("offline");
            alert.setAlertRuleId(offlineRule.getId());
            alert.setAlertLevel(offlineRule.getAlertLevel() != null ? offlineRule.getAlertLevel() : "error");
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
            log.info("已生成离线告警，deviceId={}, alertId={}, ruleId={}", device.getId(), alert.getId(), offlineRule.getId());

            // 尝试自动派单
            deviceAlertService.tryAutoDispatch(alert.getId());

            // 通知规则配置的通知用户（需包含站内信渠道）
            try {
                String notifyUsers = offlineRule.getNotifyUsers();
                String notifyChannels = offlineRule.getNotifyChannels();
                if (notifyUsers != null && !notifyUsers.isBlank()
                        && notifyChannels != null && notifyChannels.contains("system")) {
                    List<Long> empIds = NotificationHelper.parseCsvIds(notifyUsers);
                    List<Long> authUserIds = notificationHelper.employeeIdsToAuthUserIds(empIds);
                    if (!authUserIds.isEmpty()) {
                        notificationHelper.sendBatch(authUserIds, "food_safety_alert", "equipment_alert",
                                "设备离线告警", content,
                                NotificationHelper.mapAlertLevelToRiskLevel(
                                        offlineRule.getAlertLevel() != null ? offlineRule.getAlertLevel() : "error"),
                                "设备管理", alert.getId(), "alert",
                                device.getTenantId(), device.getOrgId(),
                                "[{\"label\":\"查看告警\",\"route\":\"/alert?id=" + alert.getId() + "\"}]");
                    }
                }
            } catch (Exception e) {
                log.warn("离线告警通知发送失败: deviceId={}, ruleId={}", device.getId(), offlineRule.getId(), e);
            }
        }
    }

    /**
     * 自动关闭未指派的告警（设备恢复在线时调用）
     * 仅关闭 pending 状态（未指派）的告警，已指派/处理中的告警不关闭
     */
    private void autoCloseUnassignedAlerts(Long deviceId, String alertType) {
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
            log.info("自动关闭未指派告警: alertId={}, alertNo={}, deviceId={}, 原因=设备恢复在线", alert.getId(), alert.getAlertNo(), deviceId);
        }
    }

    /**
     * 查询所有启用的离线告警规则
     */
    private List<DeviceAlertRule> findEnabledOfflineRules() {
        LambdaQueryWrapper<DeviceAlertRule> query = new LambdaQueryWrapper<>();
        query.eq(DeviceAlertRule::getRuleType, "offline")
                .eq(DeviceAlertRule::getIsEnabled, 1);
        return alertRuleMapper.selectList(query);
    }

    /**
     * 从规则列表中匹配最适合的规则（优先匹配设备类型，其次通用规则）
     * 同时检查设备是否在规则的 deviceIds 范围内
     */
    private DeviceAlertRule matchRule(List<DeviceAlertRule> rules, String deviceType, Long deviceId) {
        if (rules == null || rules.isEmpty()) return null;
        for (DeviceAlertRule rule : rules) {
            if (deviceType != null && deviceType.equals(rule.getDeviceType()) && isDeviceInScope(rule, deviceId)) {
                return rule;
            }
        }
        return null;
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

    /**
     * 获取设备类型对应的离线超时分钟数，从告警规则的 conditionJson.offlineMinutes 读取
     */
    private long getOfflineMinutes(List<DeviceAlertRule> rules, String deviceType, Long deviceId) {
        DeviceAlertRule rule = matchRule(rules, deviceType, deviceId);
        if (rule != null && rule.getConditionJson() != null) {
            try {
                JsonNode node = objectMapper.readTree(rule.getConditionJson());
                if (node.has("offlineMinutes") && node.get("offlineMinutes").isNumber()) {
                    long minutes = node.get("offlineMinutes").asLong();
                    if (minutes > 0) return minutes;
                }
            } catch (JsonProcessingException e) {
                log.warn("解析规则 conditionJson 失败: ruleId={}, error={}", rule.getId(), e.getMessage());
            }
        }
        return DEFAULT_OFFLINE_MINUTES;
    }
}
