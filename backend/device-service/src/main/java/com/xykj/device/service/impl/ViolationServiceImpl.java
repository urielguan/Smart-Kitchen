package com.xykj.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.exception.BizException;
import com.xykj.device.dto.ViolationHandleDTO;
import com.xykj.device.dto.ViolationQueryDTO;
import com.xykj.device.entity.DeviceAlert;
import com.xykj.device.entity.DeviceMonitorRecord;
import com.xykj.device.entity.ViolationOperationLog;
import com.xykj.device.mapper.DeviceAlertMapper;
import com.xykj.device.mapper.DeviceMonitorRecordMapper;
import com.xykj.device.mapper.ViolationOperationLogMapper;
import com.xykj.device.config.StreamConfig;
import com.xykj.device.service.RecordingService;
import com.xykj.device.service.ViolationService;
import com.xykj.device.vo.ViolationOperationLogVO;
import com.xykj.device.vo.ViolationStatisticsVO;
import com.xykj.device.vo.ViolationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI违规识别服务实现
 * 基于 device_alert 表（alert_type = 'ai_violation'）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViolationServiceImpl implements ViolationService {

    private final DeviceAlertMapper alertMapper;
    private final DeviceMonitorRecordMapper recordMapper;
    private final ViolationOperationLogMapper operationLogMapper;
    private final RecordingService recordingService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final StreamConfig streamConfig;

    private int getEvidenceRetentionDays() { return streamConfig.getRecording().getEvidenceRetentionDays(); }

    // 违规类型映射
    private static final Map<String, String> VIOLATION_TYPES = new HashMap<>() {{
        put("no_mask", "未佩戴口罩");
        put("no_hat", "未佩戴厨师帽");
        put("smoking", "吸烟行为");
        put("phone", "使用手机");
        put("outsider", "陌生人闯入");
        put("fighting", "打架斗殴");
        put("falling", "人员跌倒");
        put("gathering", "异常聚集");
    }};

    // 告警级别映射
    private static final Map<String, String> ALERT_LEVELS = new HashMap<>() {{
        put("info", "提示");
        put("warning", "警告");
        put("error", "错误");
        put("urgent", "紧急");
        put("critical", "严重");
        put("danger", "危险");
    }};

    // 处理状态映射
    private static final Map<String, String> STATUS_MAP = new HashMap<>() {{
        put("pending", "待处理");
        put("assigned", "已指派");
        put("handling", "处理中");
        put("handled", "已处置");
        put("reviewed", "已复核");
        put("closed", "已关闭");
    }};

    @Override
    public Page<ViolationVO> getViolationList(ViolationQueryDTO query) {
        LambdaQueryWrapper<DeviceAlert> wrapper = new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getAlertType, "ai_violation")
                .eq(DeviceAlert::getDeleted, 0)
                .eq(query.getOrgId() != null, DeviceAlert::getOrgId, query.getOrgId())
                .eq(query.getDeviceId() != null, DeviceAlert::getDeviceId, query.getDeviceId())
                .eq(query.getAlertLevel() != null && !query.getAlertLevel().isEmpty(), DeviceAlert::getAlertLevel, query.getAlertLevel())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(), DeviceAlert::getStatus, query.getStatus())
                .ge(query.getStartTime() != null, DeviceAlert::getTriggeredAt, query.getStartTime())
                .le(query.getEndTime() != null, DeviceAlert::getTriggeredAt, query.getEndTime())
                .orderByDesc(DeviceAlert::getTriggeredAt);

        Page<DeviceAlert> page = alertMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        // 按violationType二次过滤（从alertDetail JSON中匹配）
        List<ViolationVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .filter(vo -> {
                    if (query.getViolationType() != null && !query.getViolationType().isEmpty()) {
                        return query.getViolationType().equals(vo.getViolationType());
                    }
                    return true;
                })
                .collect(Collectors.toList());

        Page<ViolationVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    public ViolationVO getViolationDetail(Long id) {
        DeviceAlert alert = alertMapper.selectOne(new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getId, id)
                .eq(DeviceAlert::getAlertType, "ai_violation")
                .eq(DeviceAlert::getDeleted, 0));

        if (alert == null) {
            throw new BizException("违规事件不存在");
        }

        ViolationVO vo = convertToVO(alert);

        // 解析关联录像
        resolveLinkedRecording(alert).ifPresent(record -> {
            vo.setRecordingId(record.getId());
            vo.setRecordingStartTime(record.getStartTime());
            vo.setRecordingEndTime(record.getEndTime());
            try {
                vo.setRecordingPlaybackUrl(recordingService.getPlaybackUrl(record.getId()));
            } catch (Exception e) {
                log.warn("获取录像回放地址失败, recordingId={}: {}", record.getId(), e.getMessage());
            }
        });

        return vo;
    }

    @Override
    public boolean handleViolation(Long id, ViolationHandleDTO dto) {
        DeviceAlert alert = alertMapper.selectById(id);
        if (alert == null || alert.getDeleted() == 1) {
            throw new BizException("违规事件不存在");
        }
        if (!"ai_violation".equals(alert.getAlertType())) {
            throw new BizException("该记录不是AI违规事件");
        }

        alert.setStatus(dto.getStatus());
        alert.setHandleResult(dto.getHandleRemark());

        if (dto.getHandlerId() != null) {
            alert.setHandledBy(dto.getHandlerId());
        } else if ("assigned".equals(dto.getStatus())) {
            // 如果是已指派状态，尝试从上下文获取当前用户
            try {
                Long currentUserId = getCurrentUserId();
                if (currentUserId != null) {
                    alert.setAssignedTo(currentUserId);
                    alert.setAssignedAt(LocalDateTime.now());
                }
            } catch (Exception e) {
                log.warn("获取当前用户ID失败: {}", e.getMessage());
            }
        }

        if ("resolved".equals(dto.getStatus()) || "handled".equals(dto.getStatus())) {
            alert.setHandledAt(LocalDateTime.now());
            if (alert.getHandledBy() == null) {
                try {
                    Long currentUserId = getCurrentUserId();
                    if (currentUserId != null) {
                        alert.setHandledBy(currentUserId);
                    }
                } catch (Exception e) {
                    log.warn("获取当前用户ID失败: {}", e.getMessage());
                }
            }
        }

        boolean success = alertMapper.updateById(alert) > 0;
        if (success) {
            logOperation(alert.getId(), dto.getStatus(),
                    STATUS_MAP.getOrDefault(dto.getStatus(), dto.getStatus()),
                    dto.getHandleRemark());
        }
        return success;
    }

    @Override
    public ViolationStatisticsVO getViolationStatistics(Long orgId) {
        ViolationStatisticsVO vo = new ViolationStatisticsVO();

        LambdaQueryWrapper<DeviceAlert> baseWrapper = new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getAlertType, "ai_violation")
                .eq(DeviceAlert::getDeleted, 0)
                .eq(orgId != null, DeviceAlert::getOrgId, orgId);

        // 总数
        vo.setTotalCount(alertMapper.selectCount(baseWrapper));

        // 待处理
        LambdaQueryWrapper<DeviceAlert> pendingWrapper = new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getAlertType, "ai_violation")
                .eq(DeviceAlert::getDeleted, 0)
                .eq(orgId != null, DeviceAlert::getOrgId, orgId)
                .in(DeviceAlert::getStatus, "pending", "assigned");
        vo.setPendingCount(alertMapper.selectCount(pendingWrapper));

        // 紧急（critical/error级别）
        LambdaQueryWrapper<DeviceAlert> urgentWrapper = new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getAlertType, "ai_violation")
                .eq(DeviceAlert::getDeleted, 0)
                .eq(orgId != null, DeviceAlert::getOrgId, orgId)
                .in(DeviceAlert::getAlertLevel, "critical", "error", "urgent", "danger");
        vo.setUrgentCount(alertMapper.selectCount(urgentWrapper));

        // 已处理
        LambdaQueryWrapper<DeviceAlert> resolvedWrapper = new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getAlertType, "ai_violation")
                .eq(DeviceAlert::getDeleted, 0)
                .eq(orgId != null, DeviceAlert::getOrgId, orgId)
                .in(DeviceAlert::getStatus, "handled", "reviewed", "closed");
        vo.setResolvedCount(alertMapper.selectCount(resolvedWrapper));

        // 今日新增
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LambdaQueryWrapper<DeviceAlert> todayWrapper = new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getAlertType, "ai_violation")
                .eq(DeviceAlert::getDeleted, 0)
                .eq(orgId != null, DeviceAlert::getOrgId, orgId)
                .ge(DeviceAlert::getTriggeredAt, todayStart);
        vo.setTodayCount(alertMapper.selectCount(todayWrapper));

        // 本周新增
        LocalDateTime weekStart = LocalDateTime.now().minusDays(7).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LambdaQueryWrapper<DeviceAlert> weekWrapper = new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getAlertType, "ai_violation")
                .eq(DeviceAlert::getDeleted, 0)
                .eq(orgId != null, DeviceAlert::getOrgId, orgId)
                .ge(DeviceAlert::getTriggeredAt, weekStart);
        vo.setWeekCount(alertMapper.selectCount(weekWrapper));

        // 本月新增
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LambdaQueryWrapper<DeviceAlert> monthWrapper = new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getAlertType, "ai_violation")
                .eq(DeviceAlert::getDeleted, 0)
                .eq(orgId != null, DeviceAlert::getOrgId, orgId)
                .ge(DeviceAlert::getTriggeredAt, monthStart);
        vo.setMonthCount(alertMapper.selectCount(monthWrapper));

        return vo;
    }

    @Override
    public boolean batchHandleViolations(List<Long> ids, ViolationHandleDTO dto) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择要处理的违规事件");
        }

        int updated = 0;
        for (Long id : ids) {
            try {
                DeviceAlert alert = alertMapper.selectById(id);
                if (alert != null && "ai_violation".equals(alert.getAlertType()) && alert.getDeleted() == 0) {
                    alert.setStatus(dto.getStatus());
                    alert.setHandleResult(dto.getHandleRemark());
                    if ("resolved".equals(dto.getStatus()) || "handled".equals(dto.getStatus())) {
                        alert.setHandledAt(LocalDateTime.now());
                        try {
                            Long currentUserId = getCurrentUserId();
                            if (currentUserId != null) {
                                alert.setHandledBy(currentUserId);
                            }
                        } catch (Exception e) {
                            log.warn("获取当前用户ID失败: {}", e.getMessage());
                        }
                    }
                    updated += alertMapper.updateById(alert);
                    logOperation(alert.getId(), "batch_" + dto.getStatus(),
                            "批量" + STATUS_MAP.getOrDefault(dto.getStatus(), dto.getStatus()),
                            dto.getHandleRemark());
                }
            } catch (Exception e) {
                log.warn("批量处理违规事件[{}]失败: {}", id, e.getMessage());
            }
        }
        return updated > 0;
    }

    // ========== 辅助方法 ==========

    /**
     * DeviceAlert → ViolationVO 转换
     */
    private ViolationVO convertToVO(DeviceAlert alert) {
        ViolationVO vo = new ViolationVO();
        vo.setId(alert.getId());
        vo.setAlertLevel(alert.getAlertLevel());
        vo.setAlertLevelName(ALERT_LEVELS.getOrDefault(alert.getAlertLevel(), alert.getAlertLevel()));
        vo.setDeviceId(alert.getDeviceId());
        vo.setDeviceName(alert.getDeviceName());
        vo.setOccurredAt(alert.getTriggeredAt());
        vo.setStatus(alert.getStatus());
        vo.setStatusName(STATUS_MAP.getOrDefault(alert.getStatus(), alert.getStatus()));
        vo.setVideoClipUrl(alert.getAlertVideoUrl());
        vo.setHandlerId(alert.getHandledBy());
        vo.setHandledAt(alert.getHandledAt());
        vo.setHandleRemark(alert.getHandleResult());
        vo.setCreatedAt(alert.getCreatedAt());
        vo.setRecordingId(alert.getRecordingId());

        // 从 alertDetail JSON 解析违规特有字段
        Map<String, Object> detail = parseAlertDetail(alert.getAlertDetail());
        if (detail != null) {
            vo.setViolationType((String) detail.getOrDefault("violationType", inferViolationType(alert.getAlertContent())));
            vo.setViolationTypeName(VIOLATION_TYPES.getOrDefault(vo.getViolationType(), vo.getViolationType()));
            vo.setConfidence(detail.get("confidence") != null ? ((Number) detail.get("confidence")).intValue() : null);
            vo.setDuration(detail.get("duration") != null ? ((Number) detail.get("duration")).intValue() : null);
            vo.setInvolvedCount(detail.get("involvedCount") != null ? ((Number) detail.get("involvedCount")).intValue() : null);
            vo.setLocation((String) detail.getOrDefault("location", inferLocation(alert.getDeviceName())));
        } else {
            // 无 alertDetail 时从 alertContent 推断
            vo.setViolationType(inferViolationType(alert.getAlertContent()));
            vo.setViolationTypeName(VIOLATION_TYPES.getOrDefault(vo.getViolationType(), alert.getAlertContent()));
            vo.setLocation(inferLocation(alert.getDeviceName()));
        }

        // 截图：取 alertImages JSON 数组的第一张
        String screenshot = parseFirstImage(alert.getAlertImages());
        vo.setScreenshotUrl(screenshot);

        // 处理人姓名
        if (alert.getHandledBy() != null) {
            vo.setHandlerName(resolveEmployeeName(alert.getHandledBy()));
        }

        return vo;
    }

    /**
     * 解析违规事件关联的录像段
     * 1. 若 alert.recordingId 已缓存，直接查询
     * 2. 否则按 device_id + triggered_at 时间范围匹配
     * 3. 匹配成功后回填 alert.recordingId 和 record.hasAiMarks
     */
    private Optional<DeviceMonitorRecord> resolveLinkedRecording(DeviceAlert alert) {
        // 优先使用已缓存的 recordingId
        if (alert.getRecordingId() != null) {
            DeviceMonitorRecord record = recordMapper.selectById(alert.getRecordingId());
            if (record != null) {
                return Optional.of(record);
            }
            // 缓存的ID无效（录像可能已被删除），清空后重新查找
            alert.setRecordingId(null);
        }

        // 按 device_id + 时间范围查找覆盖违规时刻的录像段
        if (alert.getDeviceId() == null || alert.getTriggeredAt() == null) {
            return Optional.empty();
        }

        try {
            DeviceMonitorRecord record = recordMapper.selectOne(
                    new LambdaQueryWrapper<DeviceMonitorRecord>()
                            .eq(DeviceMonitorRecord::getDeviceId, alert.getDeviceId())
                            .le(DeviceMonitorRecord::getStartTime, alert.getTriggeredAt())
                            .ge(DeviceMonitorRecord::getEndTime, alert.getTriggeredAt())
                            .eq(DeviceMonitorRecord::getStatus, "completed")
                            .eq(DeviceMonitorRecord::getDeleted, 0)
                            .orderByDesc(DeviceMonitorRecord::getId)
                            .last("LIMIT 1"));

            if (record != null) {
                // 回填 alert.recordingId
                alert.setRecordingId(record.getId());
                alertMapper.updateById(alert);

                // 标记录像包含 AI 违规片段
                if (record.getHasAiMarks() == null || record.getHasAiMarks() == 0) {
                    record.setHasAiMarks(1);
                }

                // 升级为证据录像，延长保留期
                if (record.getIsEvidence() == null || record.getIsEvidence() == 0) {
                    record.setIsEvidence(1);
                    record.setRetentionDays(getEvidenceRetentionDays());
                    record.setEvidenceReason("AI违规事件关联");
                    if (record.getStartTime() != null) {
                        record.setExpiresAt(record.getStartTime().plusDays(getEvidenceRetentionDays()));
                    }
                    log.info("录像升级为证据: recordingId={}, retentionDays={}, expiresAt={}",
                            record.getId(), getEvidenceRetentionDays(), record.getExpiresAt());
                }

                recordMapper.updateById(record);
                return Optional.of(record);
            }
        } catch (Exception e) {
            log.warn("解析关联录像失败, alertId={}: {}", alert.getId(), e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * 解析 alertDetail JSON
     */
    private Map<String, Object> parseAlertDetail(String alertDetail) {
        if (alertDetail == null || alertDetail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(alertDetail, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析alertDetail失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 alertContent 推断违规类型
     */
    private String inferViolationType(String alertContent) {
        if (alertContent == null) return "unknown";
        if (alertContent.contains("口罩")) return "no_mask";
        if (alertContent.contains("厨师帽") || alertContent.contains("帽子")) return "no_hat";
        if (alertContent.contains("吸烟")) return "smoking";
        if (alertContent.contains("手机")) return "phone";
        if (alertContent.contains("陌生人") || alertContent.contains("闯入")) return "outsider";
        if (alertContent.contains("打架")) return "fighting";
        if (alertContent.contains("跌倒")) return "falling";
        if (alertContent.contains("聚集")) return "gathering";
        return "unknown";
    }

    /**
     * 从设备名推断位置
     */
    private String inferLocation(String deviceName) {
        if (deviceName == null) return "未知位置";
        if (deviceName.contains("入口")) return "后厨入口";
        if (deviceName.contains("加工")) return "后厨加工区";
        if (deviceName.contains("烹饪") || deviceName.contains("灶")) return "烹饪区";
        if (deviceName.contains("存储") || deviceName.contains("冷库")) return "食材存储区";
        if (deviceName.contains("清洗") || deviceName.contains("洗碗")) return "餐具清洗区";
        return "后厨区域";
    }

    /**
     * 从 alertImages JSON 数组取第一张图片
     */
    private String parseFirstImage(String alertImages) {
        if (alertImages == null || alertImages.isEmpty()) {
            return null;
        }
        try {
            List<String> images = objectMapper.readValue(alertImages, new TypeReference<List<String>>() {});
            return images.isEmpty() ? null : images.get(0);
        } catch (Exception e) {
            // 可能是单条URL
            return alertImages;
        }
    }

    /**
     * 从 sys_employee 解析员工姓名
     */
    private String resolveEmployeeName(Long employeeId) {
        if (employeeId == null) return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM sys_employee WHERE id = ? AND deleted = 0",
                    String.class, employeeId);
        } catch (Exception e) {
            return "用户" + employeeId;
        }
    }

    /**
     * 获取当前登录用户ID
     */
    private Long getCurrentUserId() {
        try {
            return com.xykj.common.context.UserContext.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<ViolationOperationLogVO> getOperationLogs(Long alertId) {
        List<ViolationOperationLog> logs = operationLogMapper.selectList(
                new LambdaQueryWrapper<ViolationOperationLog>()
                        .eq(ViolationOperationLog::getAlertId, alertId)
                        .orderByDesc(ViolationOperationLog::getCreatedAt));

        return logs.stream().map(this::convertLogToVO).collect(Collectors.toList());
    }

    /**
     * 记录操作日志
     */
    private void logOperation(Long alertId, String action, String actionName, String content) {
        try {
            ViolationOperationLog log = new ViolationOperationLog();
            log.setAlertId(alertId);
            log.setAction(action);
            log.setActionName(actionName);
            log.setContent(content);
            log.setTerminal("web");

            Long userId = getCurrentUserId();
            if (userId != null) {
                log.setOperatorId(userId);
                log.setOperatorName(resolveEmployeeName(userId));
            }

            log.setCreatedAt(LocalDateTime.now());
            operationLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("记录操作日志失败, alertId={}: {}", alertId, e.getMessage());
        }
    }

    /**
     * ViolationOperationLog → ViolationOperationLogVO
     */
    private ViolationOperationLogVO convertLogToVO(ViolationOperationLog log) {
        ViolationOperationLogVO vo = new ViolationOperationLogVO();
        vo.setId(log.getId());
        vo.setAction(log.getAction());
        vo.setActionName(log.getActionName());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(log.getOperatorName());
        vo.setContent(log.getContent());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}
