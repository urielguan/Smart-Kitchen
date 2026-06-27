package com.xykj.device.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.exception.BizException;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.NotificationHelper;
import com.xykj.common.service.DataScopeService;
import com.xykj.device.dto.AlertCloseDTO;
import com.xykj.device.dto.AlertDispatchDTO;
import com.xykj.device.dto.AlertDispatchQueryDTO;
import com.xykj.device.dto.AlertProcessDTO;
import com.xykj.device.dto.AlertQueryDTO;
import com.xykj.device.dto.AlertReviewDTO;
import com.xykj.device.entity.AlertDispatch;
import com.xykj.device.entity.AlertWorkOrderRecord;
import com.xykj.device.entity.DeviceAlert;
import com.xykj.device.entity.DeviceAlertRule;
import com.xykj.device.entity.DeviceInfo;
import com.xykj.device.entity.DeviceMonitorRecord;
import com.xykj.device.mapper.AlertDispatchMapper;
import com.xykj.device.mapper.AlertWorkOrderRecordMapper;
import com.xykj.device.mapper.DeviceMonitorRecordMapper;
import com.xykj.device.mapper.DeviceAlertMapper;
import com.xykj.device.mapper.DeviceAlertRuleMapper;
import com.xykj.device.mapper.DeviceInfoMapper;
import com.xykj.device.service.DeviceAlertService;
import com.xykj.device.vo.AlertDashboardVO;
import com.xykj.device.vo.AlertDetailVO;
import com.xykj.device.vo.AlertDispatchDetailVO;
import com.xykj.device.vo.AlertDispatchVO;
import com.xykj.device.vo.AlertWorkOrderRecordVO;
import com.xykj.device.vo.AlertVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAlertServiceImpl implements DeviceAlertService {

    private final DeviceAlertMapper alertMapper;
    private final DeviceInfoMapper deviceInfoMapper;
    private final DeviceAlertRuleMapper alertRuleMapper;
    private final AlertDispatchMapper dispatchMapper;
    private final AlertWorkOrderRecordMapper workOrderRecordMapper;
    private final DeviceMonitorRecordMapper deviceMonitorRecordMapper;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;
    private final NotificationHelper notificationHelper;
    private final DataScopeService dataScopeService;

    // 告警类型名称映射（与规则类型保持一致）
    private static final Map<String, String> ALERT_TYPE_NAMES = Map.ofEntries(
            Map.entry("offline", "离线告警"),
            Map.entry("device_fault", "设备故障"),
            Map.entry("threshold", "阈值告警"),
            Map.entry("ai_event", "AI事件告警"),
            // 兼容旧数据
            Map.entry("device_offline", "离线告警"),
            Map.entry("temp_abnormal", "阈值告警"),
            Map.entry("humidity_abnormal", "阈值告警"),
            Map.entry("threshold_exceed", "阈值告警"),
            Map.entry("ai_violation", "AI事件告警"),
            Map.entry("gas_abnormal", "阈值告警"),
            Map.entry("material", "物料告警")
    );

    private static final Map<String, String> ALERT_LEVEL_NAMES = Map.of(
            "info", "提示", "warning", "警告", "error", "错误", "critical", "严重"
    );

    private static final Map<String, String> STATUS_NAMES = Map.of(
            "pending", "待处理", "assigned", "已指派", "handling", "处理中",
            "handled", "已处置", "reviewed", "已复核", "closed", "已关闭"
    );

    private static final Map<String, String> DISPATCH_STATUS_NAMES = Map.of(
            "pending", "待处理", "processing", "处理中",
            "completed", "已处理", "reviewed", "已复核",
            "cancelled", "已取消", "rejected", "已驳回"
    );

    private static final Map<String, String> PRIORITY_NAMES = Map.of(
            "high", "高", "medium", "中", "low", "低"
    );

    private static final Map<String, String> DISPATCH_TYPE_NAMES = Map.of(
            "auto", "自动派单", "manual", "人工派单"
    );

    public static final String REVIEW_RESULT_APPROVED = "approved";
    public static final String REVIEW_RESULT_REJECTED = "rejected";

    private static final Map<String, String> REVIEW_RESULT_NAMES = Map.of(
            REVIEW_RESULT_APPROVED, "通过",
            REVIEW_RESULT_REJECTED, "驳回"
    );

    private static final Map<String, String> ACTION_NAMES = Map.of(
            "dispatch", "派单", "process", "处理", "complete", "完成",
            "review", "复核", "cancel", "取消"
    );

    /** 告警级别 → 自动派单优先级映射 */
    private static final Map<String, String> ALERT_LEVEL_TO_PRIORITY = Map.of(
            "critical", "high",
            "error", "high",
            "warning", "medium",
            "info", "low"
    );

    // ==================== 看板/列表/详情（保留不变） ====================

    @Override
    @DataScope
    public AlertDashboardVO getDashboard(AlertQueryDTO query) {
        AlertDashboardVO dashboard = new AlertDashboardVO();

        LambdaQueryWrapper<DeviceAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceAlert::getDeleted, 0);
        wrapper.eq(query.getOrgId() != null, DeviceAlert::getOrgId, query.getOrgId());
        wrapper.in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), DeviceAlert::getOrgId, query.getOrgIds());
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(DeviceAlert::getId);
        }
        long total = alertMapper.selectCount(wrapper);
        dashboard.setTotalCount((int) total);

        List<Map<String, Object>> levelStats = alertMapper.countByAlertLevel(query);
        for (Map<String, Object> stat : levelStats) {
            String level = (String) stat.get("alertLevel");
            int count = ((Number) stat.get("count")).intValue();
            switch (level) {
                case "critical" -> dashboard.setCriticalCount(count);
                case "error" -> dashboard.setErrorCount(count);
                case "warning" -> dashboard.setWarningCount(count);
                case "info" -> dashboard.setInfoCount(count);
            }
        }

        List<Map<String, Object>> statusStats = alertMapper.countByStatus(query);
        for (Map<String, Object> stat : statusStats) {
            String status = (String) stat.get("status");
            int count = ((Number) stat.get("count")).intValue();
            switch (status) {
                case "pending" -> dashboard.setPendingCount(count);
                case "assigned" -> dashboard.setAssignedCount(count);
                case "handled" -> dashboard.setHandledCount(count);
                case "reviewed" -> dashboard.setReviewedCount(count);
                case "closed" -> dashboard.setClosedCount(count);
            }
        }

        List<Map<String, Object>> typeStats = alertMapper.countByAlertType(query);
        List<AlertDashboardVO.AlertTypeStats> alertTypeStats = new ArrayList<>();
        for (Map<String, Object> stat : typeStats) {
            AlertDashboardVO.AlertTypeStats typeStat = new AlertDashboardVO.AlertTypeStats();
            typeStat.setAlertType((String) stat.get("alertType"));
            typeStat.setAlertTypeName(ALERT_TYPE_NAMES.getOrDefault(stat.get("alertType"), "未知"));
            typeStat.setCount(((Number) stat.get("count")).intValue());
            alertTypeStats.add(typeStat);
        }
        dashboard.setAlertTypeStats(alertTypeStats);

        List<Map<String, Object>> trendData = alertMapper.countTrend7Days(query);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        // 将有数据的天放入 Map（key=MM-dd）
        Map<String, Integer> countMap = new LinkedHashMap<>();
        for (Map<String, Object> data : trendData) {
            String dateStr;
            Object dateObj = data.get("date");
            if (dateObj instanceof java.sql.Date) {
                dateStr = ((java.sql.Date) dateObj).toLocalDate().format(formatter);
            } else if (dateObj instanceof LocalDateTime) {
                dateStr = ((LocalDateTime) dateObj).format(formatter);
            } else {
                dateStr = String.valueOf(dateObj);
            }
            countMap.put(dateStr, ((Number) data.get("count")).intValue());
        }

        // 补全最近7天（含今天），无数据的天补 0
        List<AlertDashboardVO.AlertTrend> trends = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            String dateStr = today.minusDays(i).format(formatter);
            AlertDashboardVO.AlertTrend trend = new AlertDashboardVO.AlertTrend();
            trend.setDate(dateStr);
            trend.setCount(countMap.getOrDefault(dateStr, 0));
            trends.add(trend);
        }
        dashboard.setAlertTrends(trends);

        return dashboard;
    }

    @Override
    @DataScope
    public Page<AlertVO> list(AlertQueryDTO query) {
        Page<AlertVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<AlertVO> result = alertMapper.selectAlertPage(page, query);

        for (AlertVO vo : result.getRecords()) {
            vo.setAlertTypeName(ALERT_TYPE_NAMES.getOrDefault(vo.getAlertType(), "未知"));
            vo.setAlertLevelName(ALERT_LEVEL_NAMES.getOrDefault(vo.getAlertLevel(), "未知"));
            vo.setStatusName(STATUS_NAMES.getOrDefault(vo.getStatus(), "未知"));
        }

        // 批量查询处理人姓名（assigned_to 存的是 sys_employee.id）
        Set<Long> empIds = new HashSet<>();
        for (AlertVO vo : result.getRecords()) {
            if (vo.getAssignedTo() != null) empIds.add(vo.getAssignedTo());
        }
        if (!empIds.isEmpty()) {
            Map<Long, String> nameMap = batchQueryEmployeeNames(empIds);
            for (AlertVO vo : result.getRecords()) {
                if (vo.getAssignedTo() != null) {
                    vo.setAssignedToName(nameMap.get(vo.getAssignedTo()));
                }
            }
        }

        // 批量填充告警规则名称
        Set<Long> ruleIds = new HashSet<>();
        for (AlertVO vo : result.getRecords()) {
            if (vo.getAlertRuleId() != null) ruleIds.add(vo.getAlertRuleId());
        }
        if (!ruleIds.isEmpty()) {
            Map<Long, String> ruleNameMap = new HashMap<>();
            for (Long ruleId : ruleIds) {
                DeviceAlertRule rule = alertRuleMapper.selectById(ruleId);
                if (rule != null) ruleNameMap.put(ruleId, rule.getRuleName());
            }
            for (AlertVO vo : result.getRecords()) {
                if (vo.getAlertRuleId() != null) {
                    vo.setAlertRuleName(ruleNameMap.get(vo.getAlertRuleId()));
                }
            }
        }

        return result;
    }

    @Override
    public AlertDetailVO getDetail(Long id) {
        DeviceAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw BizException.notFound("告警记录不存在");
        }

        // 数据权限校验：确认当前用户有权访问该告警所属组织
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(alert.getOrgId())) {
            throw BizException.forbidden("无权访问该告警记录");
        }

        AlertDetailVO detail = new AlertDetailVO();
        detail.setId(alert.getId());
        detail.setAlertNo(alert.getAlertNo());
        detail.setAlertType(alert.getAlertType());
        detail.setAlertTypeName(ALERT_TYPE_NAMES.getOrDefault(alert.getAlertType(), "未知"));
        detail.setAlertRuleId(alert.getAlertRuleId());
        if (alert.getAlertRuleId() != null) {
            DeviceAlertRule rule = alertRuleMapper.selectById(alert.getAlertRuleId());
            if (rule != null) detail.setAlertRuleName(rule.getRuleName());
        }
        detail.setAlertLevel(alert.getAlertLevel());
        detail.setAlertLevelName(ALERT_LEVEL_NAMES.getOrDefault(alert.getAlertLevel(), "未知"));
        detail.setDeviceId(alert.getDeviceId());
        detail.setDeviceName(alert.getDeviceName());
        detail.setAlertContent(alert.getAlertContent());
        detail.setTriggeredAt(alert.getTriggeredAt());
        detail.setStatus(alert.getStatus());
        detail.setStatusName(STATUS_NAMES.getOrDefault(alert.getStatus(), "未知"));

        if (alert.getDeviceId() != null) {
            DeviceInfo device = deviceInfoMapper.selectById(alert.getDeviceId());
            if (device != null) {
                detail.setDeviceType(device.getDeviceType());
            }
        }

        try {
            if (alert.getAlertDetail() != null) {
                detail.setAlertDetail(objectMapper.readValue(alert.getAlertDetail(), new TypeReference<Map<String, Object>>() {}));
            }
            if (alert.getAlertImages() != null) {
                detail.setAlertImages(objectMapper.readValue(alert.getAlertImages(), new TypeReference<List<String>>() {}));
            }
            if (alert.getHandleImages() != null) {
                detail.setHandleImages(objectMapper.readValue(alert.getHandleImages(), new TypeReference<List<String>>() {}));
            }
        } catch (Exception e) {
            log.error("解析JSON失败", e);
        }

        detail.setAlertVideoUrl(alert.getAlertVideoUrl());
        detail.setAssignedTo(alert.getAssignedTo());
        detail.setAssignedAt(alert.getAssignedAt());
        detail.setHandledBy(alert.getHandledBy());
        detail.setHandledAt(alert.getHandledAt());
        detail.setHandleResult(alert.getHandleResult());
        detail.setReviewedBy(alert.getReviewedBy());
        detail.setReviewedAt(alert.getReviewedAt());
        detail.setReviewResult(alert.getReviewResult());
        detail.setReviewRemark(alert.getReviewRemark());
        detail.setClosedBy(alert.getClosedBy());
        detail.setClosedAt(alert.getClosedAt());
        detail.setCloseRemark(alert.getCloseRemark());
        detail.setArchivedBy(alert.getArchivedBy());
        detail.setArchivedAt(alert.getArchivedAt());
        detail.setArchiveRemark(alert.getArchiveRemark());
        detail.setCreatedAt(alert.getCreatedAt());

        // 查询人员姓名（assignedTo/handledBy 是 sys_employee.id，reviewedBy/closedBy/archivedBy 是 auth_user.id）
        Set<Long> empIds = new HashSet<>();
        if (alert.getAssignedTo() != null) empIds.add(alert.getAssignedTo());
        if (alert.getHandledBy() != null) empIds.add(alert.getHandledBy());
        if (!empIds.isEmpty()) {
            Map<Long, String> empNameMap = batchQueryEmployeeNames(empIds);
            if (alert.getAssignedTo() != null) detail.setAssignedToName(empNameMap.get(alert.getAssignedTo()));
            if (alert.getHandledBy() != null) detail.setHandledByName(empNameMap.get(alert.getHandledBy()));
        }
        if (alert.getReviewedBy() != null) {
            Map<Long, String> userNameMap = batchQueryUserNames(buildNonNullIdSet(
                    alert.getReviewedBy(), alert.getClosedBy(), alert.getArchivedBy()));
            detail.setReviewedByName(userNameMap.get(alert.getReviewedBy()));
            if (alert.getClosedBy() != null) {
                detail.setClosedByName(userNameMap.get(alert.getClosedBy()));
            }
            if (alert.getArchivedBy() != null) {
                detail.setArchivedByName(userNameMap.get(alert.getArchivedBy()));
            }
        } else if (alert.getClosedBy() != null || alert.getArchivedBy() != null) {
            Map<Long, String> userNameMap = batchQueryUserNames(buildNonNullIdSet(
                    alert.getClosedBy(), alert.getArchivedBy()));
            if (alert.getClosedBy() != null) {
                detail.setClosedByName(userNameMap.get(alert.getClosedBy()));
            }
            if (alert.getArchivedBy() != null) {
                detail.setArchivedByName(userNameMap.get(alert.getArchivedBy()));
            }
        }

        return detail;
    }

    // ==================== 派单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> dispatch(Long id, AlertDispatchDTO dto) {
        DeviceAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw BizException.notFound("告警记录不存在");
        }
        if (!"pending".equals(alert.getStatus())) {
            throw BizException.conflict("只能对待处理状态的告警进行派单");
        }

        // 捕获派单前数据
        Map<String, Object> beforeData = new HashMap<>();
        beforeData.put("alertNo", alert.getAlertNo());
        beforeData.put("status", alert.getStatus());

        // 确定处理人
        Long handlerId;
        String handlerName;
        Long assignerId = 0L;
        String assignerName = "系统";

        if ("auto".equals(dto.getDispatchType())) {
            // 根据关联规则的派单范围筛选处理人（派单范围为空时不自动派单）
            List<Long> roleIds = resolveDispatchRoleIds(alert.getAlertRuleId());
            if (roleIds == null || roleIds.isEmpty()) {
                throw new RuntimeException("规则未配置派单范围，无法自动派单");
            }
            handlerId = dispatchMapper.selectLeastBusyHandlerByRoles(roleIds);
            if (handlerId == null) {
                throw new RuntimeException("派单范围内没有可用的处理人");
            }
            // 通过 JdbcTemplate 查询处理人姓名
            handlerName = jdbcTemplate.queryForObject(
                    "SELECT real_name FROM sys_employee WHERE id = ? AND deleted = 0", String.class, handlerId);
            if (handlerName == null) handlerName = "未知";
        } else {
            if (dto.getHandlerId() == null) {
                throw BizException.badRequest("请选择处理人");
            }
            handlerId = dto.getHandlerId();
            handlerName = jdbcTemplate.queryForObject(
                    "SELECT real_name FROM sys_employee WHERE id = ? AND deleted = 0", String.class, handlerId);
            if (handlerName == null) {
                throw BizException.notFound("处理人不存在");
            }
            assignerId = UserContext.getUserId();
            assignerName = UserContext.getRealName();
        }

        // 创建派单记录
        AlertDispatch dispatch = new AlertDispatch();
        dispatch.setDispatchNo(dispatchMapper.generateDispatchNo());
        dispatch.setAlertId(id);
        dispatch.setAlertNo(alert.getAlertNo());
        dispatch.setDispatchType(dto.getDispatchType());
        dispatch.setAssignerId(assignerId);
        dispatch.setAssignerName(assignerName);
        dispatch.setHandlerId(handlerId);
        dispatch.setHandlerName(handlerName);
        // 自动派单时设置默认截止时间（24小时后）
        if ("auto".equals(dto.getDispatchType()) && dto.getDeadline() == null) {
            dispatch.setDeadline(LocalDateTime.now().plusHours(24));
        } else {
            dispatch.setDeadline(dto.getDeadline());
        }
        dispatch.setRemark(dto.getRemark());
        dispatch.setStatus("pending");
        // 自动派单时根据告警级别确定优先级
        String priority;
        if (dto.getPriority() != null) {
            priority = dto.getPriority();
        } else if ("auto".equals(dto.getDispatchType())) {
            priority = ALERT_LEVEL_TO_PRIORITY.getOrDefault(alert.getAlertLevel(), "medium");
        } else {
            priority = "medium";
        }
        dispatch.setPriority(priority);
        dispatch.setOrgId(alert.getOrgId());
        dispatch.setTenantId(alert.getTenantId());
        dispatchMapper.insert(dispatch);

        // 更新告警状态
        alert.setStatus("assigned");
        alert.setAssignedTo(handlerId);
        alert.setAssignedAt(LocalDateTime.now());
        alertMapper.updateById(alert);

        // 工单操作记录
        Long operatorId = "auto".equals(dto.getDispatchType()) ? 0L : assignerId;
        String operatorName = "auto".equals(dto.getDispatchType()) ? "系统" : assignerName;
        createWorkOrderRecord(dispatch.getId(), id, "dispatch", "派单",
                operatorId, operatorName, "派单给" + handlerName, null);

        // 审计日志
        Map<String, Object> afterData = new HashMap<>();
        afterData.put("alertNo", alert.getAlertNo());
        afterData.put("status", "assigned");
        afterData.put("dispatchNo", dispatch.getDispatchNo());
        afterData.put("dispatchType", DISPATCH_TYPE_NAMES.getOrDefault(dto.getDispatchType(), dto.getDispatchType()));
        afterData.put("handlerId", handlerId);
        afterData.put("handlerName", handlerName);
        afterData.put("priority", dispatch.getPriority());
        if (dispatch.getDeadline() != null) {
            afterData.put("deadline", dispatch.getDeadline().toString());
        }

        String dispatchTypeDesc = "auto".equals(dto.getDispatchType()) ? "自动" : "人工";
        auditLogService.log(AuditModule.DEVICE_ALERT, AuditOperationType.DISPATCH, id, alert.getAlertNo(),
                dispatchTypeDesc + "派单：" + alert.getAlertNo() + " → " + handlerName,
                JSONUtil.toJsonStr(beforeData), JSONUtil.toJsonStr(afterData));

        // 发送通知给处理人
        try {
            Long authUserId = notificationHelper.employeeIdToAuthUserId(handlerId);
            if (authUserId != null) {
                notificationHelper.send(authUserId, "approval_todo", "alert_dispatch",
                        "告警派单通知", dispatchTypeDesc + "派单：" + alert.getAlertNo() + " → " + handlerName,
                        NotificationHelper.mapAlertLevelToRiskLevel(alert.getAlertLevel()),
                        "设备管理", dispatch.getId(), "alert_dispatch",
                        alert.getTenantId(), alert.getOrgId(),
                        "[{\"label\":\"去处理\",\"route\":\"/alert?tab=dispatches\"}]");
            }
        } catch (Exception e) {
            log.warn("告警派单通知发送失败: alertId={}", id, e);
        }

        // 返回派单结果
        Map<String, Object> result = new HashMap<>();
        result.put("dispatchId", dispatch.getId());
        result.put("dispatchNo", dispatch.getDispatchNo());
        result.put("alertNo", alert.getAlertNo());
        result.put("dispatchType", dto.getDispatchType());
        result.put("handlerId", handlerId);
        result.put("handlerName", handlerName);
        result.put("deadline", dispatch.getDeadline() != null
                ? dispatch.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null);
        result.put("status", "pending");
        return result;
    }

    // ==================== 派单工单列表 ====================

    @Override
    @DataScope
    public Page<AlertDispatchVO> listDispatch(AlertDispatchQueryDTO query) {
        // 获取当前账号对应的员工ID，用于匹配 handler_id
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            List<Long> empIds = jdbcTemplate.query(
                    "SELECT id FROM sys_employee WHERE user_id = ? AND deleted = 0 LIMIT 1",
                    (rs, rowNum) -> rs.getLong("id"), currentUserId);
            if (!empIds.isEmpty()) {
                query.setHandlerEmployeeId(empIds.get(0));
            }
        }

        Page<AlertDispatchVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<AlertDispatchVO> result = dispatchMapper.selectDispatchPage(page, query);

        for (AlertDispatchVO vo : result.getRecords()) {
            vo.setAlertTypeName(ALERT_TYPE_NAMES.getOrDefault(vo.getAlertType(), "未知"));
            vo.setAlertLevelName(ALERT_LEVEL_NAMES.getOrDefault(vo.getAlertLevel(), "未知"));
            vo.setDispatchTypeName(DISPATCH_TYPE_NAMES.getOrDefault(vo.getDispatchType(), "未知"));
            vo.setPriorityName(PRIORITY_NAMES.getOrDefault(vo.getPriority(), "中"));
            vo.setStatusName(DISPATCH_STATUS_NAMES.getOrDefault(vo.getStatus(), "未知"));
        }

        return result;
    }

    // ==================== 派单工单详情 ====================

    @Override
    public AlertDispatchDetailVO getDispatchDetail(Long dispatchId) {
        AlertDispatch dispatch = dispatchMapper.selectById(dispatchId);
        if (dispatch == null) {
            throw BizException.notFound("派单记录不存在");
        }

        AlertDispatchDetailVO detail = new AlertDispatchDetailVO();

        // 派单信息
        detail.setDispatchId(dispatch.getId());
        detail.setDispatchNo(dispatch.getDispatchNo());
        detail.setDispatchType(dispatch.getDispatchType());
        detail.setDispatchTypeName(DISPATCH_TYPE_NAMES.getOrDefault(dispatch.getDispatchType(), "未知"));
        detail.setAssignerId(dispatch.getAssignerId());
        detail.setAssignerName(dispatch.getAssignerName());
        detail.setHandlerId(dispatch.getHandlerId());
        detail.setHandlerName(dispatch.getHandlerName());
        detail.setDeadline(dispatch.getDeadline());
        detail.setPriority(dispatch.getPriority());
        detail.setPriorityName(PRIORITY_NAMES.getOrDefault(dispatch.getPriority(), "中"));
        detail.setRemark(dispatch.getRemark());
        detail.setStatus(dispatch.getStatus());
        detail.setStatusName(DISPATCH_STATUS_NAMES.getOrDefault(dispatch.getStatus(), "未知"));
        detail.setHandleResult(dispatch.getHandleResult());
        detail.setCompletedAt(dispatch.getCompletedAt());
        detail.setReviewedBy(dispatch.getReviewedBy());
        detail.setReviewedByName(null); // filled below if needed
        detail.setReviewedAt(dispatch.getReviewedAt());
        detail.setReviewResult(dispatch.getReviewResult());
        detail.setCreatedAt(dispatch.getCreatedAt());

        DeviceAlert alert = alertMapper.selectById(dispatch.getAlertId());
        if (alert != null) {
            detail.setReviewRemark(alert.getReviewRemark());
            detail.setClosedBy(alert.getClosedBy());
            detail.setClosedAt(alert.getClosedAt());
            detail.setCloseRemark(alert.getCloseRemark());
            detail.setArchivedBy(alert.getArchivedBy());
            detail.setArchivedAt(alert.getArchivedAt());
            detail.setArchiveRemark(alert.getArchiveRemark());
        }

        // 处理附件 JSON → List
        if (dispatch.getHandleAttachments() != null) {
            try {
                detail.setHandleAttachments(objectMapper.readValue(dispatch.getHandleAttachments(), new TypeReference<List<Object>>() {}));
            } catch (Exception e) {
                log.error("解析处理附件JSON失败", e);
            }
        }

        // 复核附件 JSON → List
        if (dispatch.getReviewAttachments() != null) {
            try {
                detail.setReviewAttachments(objectMapper.readValue(dispatch.getReviewAttachments(), new TypeReference<List<Object>>() {}));
            } catch (Exception e) {
                log.error("解析复核附件JSON失败", e);
            }
        }

        // 告警信息
        if (alert != null) {
            detail.setAlertId(alert.getId());
            detail.setAlertNo(alert.getAlertNo());
            detail.setAlertType(alert.getAlertType());
            detail.setAlertTypeName(ALERT_TYPE_NAMES.getOrDefault(alert.getAlertType(), "未知"));
            detail.setAlertLevel(alert.getAlertLevel());
            detail.setAlertLevelName(ALERT_LEVEL_NAMES.getOrDefault(alert.getAlertLevel(), "未知"));
            detail.setAlertStatus(alert.getStatus());
            detail.setAlertStatusName(STATUS_NAMES.getOrDefault(alert.getStatus(), "未知"));
            detail.setAlertContent(alert.getAlertContent());
            detail.setTriggeredAt(alert.getTriggeredAt());
            detail.setAlertVideoUrl(alert.getAlertVideoUrl());
            detail.setDeviceName(alert.getDeviceName());

            // 设备类型名称
            if (alert.getDeviceId() != null) {
                DeviceInfo device = deviceInfoMapper.selectById(alert.getDeviceId());
                if (device != null) {
                    detail.setDeviceType(device.getDeviceType());
                    Map<String, String> deviceTypeNames = getDictNames("device_type");
                    detail.setDeviceTypeName(deviceTypeNames.getOrDefault(device.getDeviceType(), "未知"));
                }

            }

            // 告警详情 JSON
            try {
                if (alert.getAlertDetail() != null) {
                    detail.setAlertDetail(objectMapper.readValue(alert.getAlertDetail(), new TypeReference<Map<String, Object>>() {}));
                }
                if (alert.getAlertImages() != null) {
                    detail.setAlertImages(objectMapper.readValue(alert.getAlertImages(), new TypeReference<List<String>>() {}));
                }
            } catch (Exception e) {
                log.error("解析告警JSON失败", e);
            }

            Set<Long> reviewerIds = buildNonNullIdSet(dispatch.getReviewedBy(), alert.getClosedBy(), alert.getArchivedBy());
            if (!reviewerIds.isEmpty()) {
                Map<Long, String> nameMap = batchQueryUserNames(reviewerIds);
                if (dispatch.getReviewedBy() != null) {
                    detail.setReviewedByName(nameMap.get(dispatch.getReviewedBy()));
                }
                if (alert.getClosedBy() != null) {
                    detail.setClosedByName(nameMap.get(alert.getClosedBy()));
                }
                if (alert.getArchivedBy() != null) {
                    detail.setArchivedByName(nameMap.get(alert.getArchivedBy()));
                }
            }
        }

        // 处理记录
        List<AlertWorkOrderRecord> records = workOrderRecordMapper.selectByDispatchId(dispatchId);
        List<AlertWorkOrderRecordVO> recordVOs = new ArrayList<>();
        for (AlertWorkOrderRecord r : records) {
            AlertWorkOrderRecordVO rvo = new AlertWorkOrderRecordVO();
            rvo.setId(r.getId());
            rvo.setAction(r.getAction());
            rvo.setActionName(ACTION_NAMES.getOrDefault(r.getAction(), r.getAction()));
            rvo.setOperatorId(r.getOperatorId());
            rvo.setOperatorName(r.getOperatorName());
            rvo.setContent(r.getContent());
            rvo.setCreatedAt(r.getCreatedAt());

            // 附件 JSON → List
            if (r.getAttachments() != null) {
                try {
                    rvo.setAttachments(objectMapper.readValue(r.getAttachments(), new TypeReference<List<Object>>() {}));
                } catch (Exception e) {
                    log.error("解析工单记录附件JSON失败", e);
                }
            }
            recordVOs.add(rvo);
        }
        detail.setRecords(recordVOs);

        return detail;
    }

    @Override
    public AlertDispatchDetailVO getDispatchDetailByAlertId(Long alertId) {
        DeviceAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw BizException.notFound("告警记录不存在");
        }

        // 查找关联的派单记录
        AlertDispatch dispatch = dispatchMapper.selectOne(
                new LambdaQueryWrapper<AlertDispatch>()
                        .eq(AlertDispatch::getAlertId, alertId)
                        .orderByDesc(AlertDispatch::getCreatedAt)
                        .last("LIMIT 1"));

        AlertDispatchDetailVO detail = new AlertDispatchDetailVO();

        // 告警信息（始终填充）
        detail.setAlertId(alert.getId());
        detail.setAlertNo(alert.getAlertNo());
        detail.setAlertType(alert.getAlertType());
        detail.setAlertTypeName(ALERT_TYPE_NAMES.getOrDefault(alert.getAlertType(), "未知"));
        detail.setAlertLevel(alert.getAlertLevel());
        detail.setAlertLevelName(ALERT_LEVEL_NAMES.getOrDefault(alert.getAlertLevel(), "未知"));
        detail.setAlertStatus(alert.getStatus());
        detail.setAlertStatusName(STATUS_NAMES.getOrDefault(alert.getStatus(), "未知"));
        detail.setAlertContent(alert.getAlertContent());
        detail.setTriggeredAt(alert.getTriggeredAt());
        detail.setAlertVideoUrl(alert.getAlertVideoUrl());
        detail.setDeviceName(alert.getDeviceName());
        detail.setReviewRemark(alert.getReviewRemark());
        detail.setClosedBy(alert.getClosedBy());
        detail.setClosedAt(alert.getClosedAt());
        detail.setCloseRemark(alert.getCloseRemark());
        detail.setArchivedBy(alert.getArchivedBy());
        detail.setArchivedAt(alert.getArchivedAt());
        detail.setArchiveRemark(alert.getArchiveRemark());

        if (alert.getDeviceId() != null) {
            DeviceInfo device = deviceInfoMapper.selectById(alert.getDeviceId());
            if (device != null) {
                detail.setDeviceType(device.getDeviceType());
                Map<String, String> deviceTypeNames = getDictNames("device_type");
                detail.setDeviceTypeName(deviceTypeNames.getOrDefault(device.getDeviceType(), "未知"));
            }
            // 设备已删除时 deviceType 暂无法回退，后续迭代处理
        }

        try {
            if (alert.getAlertDetail() != null) {
                detail.setAlertDetail(objectMapper.readValue(alert.getAlertDetail(), new TypeReference<Map<String, Object>>() {}));
            }
            if (alert.getAlertImages() != null) {
                detail.setAlertImages(objectMapper.readValue(alert.getAlertImages(), new TypeReference<List<String>>() {}));
            }
        } catch (Exception e) {
            log.error("解析告警JSON失败", e);
        }

        // 派单信息（如果有）
        if (dispatch != null) {
            detail.setDispatchId(dispatch.getId());
            detail.setDispatchNo(dispatch.getDispatchNo());
            detail.setDispatchType(dispatch.getDispatchType());
            detail.setDispatchTypeName(DISPATCH_TYPE_NAMES.getOrDefault(dispatch.getDispatchType(), "未知"));
            detail.setAssignerId(dispatch.getAssignerId());
            detail.setAssignerName(dispatch.getAssignerName());
            detail.setHandlerId(dispatch.getHandlerId());
            detail.setHandlerName(dispatch.getHandlerName());
            detail.setDeadline(dispatch.getDeadline());
            detail.setPriority(dispatch.getPriority());
            detail.setPriorityName(PRIORITY_NAMES.getOrDefault(dispatch.getPriority(), "中"));
            detail.setRemark(dispatch.getRemark());
            detail.setStatus(dispatch.getStatus());
            detail.setStatusName(DISPATCH_STATUS_NAMES.getOrDefault(dispatch.getStatus(), "未知"));
            detail.setHandleResult(dispatch.getHandleResult());
            detail.setCompletedAt(dispatch.getCompletedAt());
            detail.setReviewedBy(dispatch.getReviewedBy());
            detail.setReviewedAt(dispatch.getReviewedAt());
            detail.setReviewResult(dispatch.getReviewResult());
            detail.setReviewRemark(alert.getReviewRemark());
            detail.setClosedBy(alert.getClosedBy());
            detail.setClosedAt(alert.getClosedAt());
            detail.setCloseRemark(alert.getCloseRemark());
            detail.setArchivedBy(alert.getArchivedBy());
            detail.setArchivedAt(alert.getArchivedAt());
            detail.setArchiveRemark(alert.getArchiveRemark());
            detail.setCreatedAt(dispatch.getCreatedAt());

            if (dispatch.getHandleAttachments() != null) {
                try {
                    detail.setHandleAttachments(objectMapper.readValue(dispatch.getHandleAttachments(), new TypeReference<List<Object>>() {}));
                } catch (Exception e) {
                    log.error("解析处理附件JSON失败", e);
                }
            }

            // 复核附件 JSON → List
            if (dispatch.getReviewAttachments() != null) {
                try {
                    detail.setReviewAttachments(objectMapper.readValue(dispatch.getReviewAttachments(), new TypeReference<List<Object>>() {}));
                } catch (Exception e) {
                    log.error("解析复核附件JSON失败", e);
                }
            }

            // 复核人/关闭人/归档人姓名
            Set<Long> reviewerIds = buildNonNullIdSet(dispatch.getReviewedBy(), alert.getClosedBy(), alert.getArchivedBy());
            if (!reviewerIds.isEmpty()) {
                Map<Long, String> nameMap = batchQueryUserNames(reviewerIds);
                if (dispatch.getReviewedBy() != null) {
                    detail.setReviewedByName(nameMap.get(dispatch.getReviewedBy()));
                }
                if (alert.getClosedBy() != null) {
                    detail.setClosedByName(nameMap.get(alert.getClosedBy()));
                }
                if (alert.getArchivedBy() != null) {
                    detail.setArchivedByName(nameMap.get(alert.getArchivedBy()));
                }
            }

            // 处理记录
            List<AlertWorkOrderRecord> records = workOrderRecordMapper.selectByDispatchId(dispatch.getId());
            List<AlertWorkOrderRecordVO> recordVOs = new ArrayList<>();
            for (AlertWorkOrderRecord r : records) {
                AlertWorkOrderRecordVO rvo = new AlertWorkOrderRecordVO();
                rvo.setId(r.getId());
                rvo.setAction(r.getAction());
                rvo.setActionName(ACTION_NAMES.getOrDefault(r.getAction(), r.getAction()));
                rvo.setOperatorId(r.getOperatorId());
                rvo.setOperatorName(r.getOperatorName());
                rvo.setContent(r.getContent());
                rvo.setCreatedAt(r.getCreatedAt());
                if (r.getAttachments() != null) {
                    try {
                        rvo.setAttachments(objectMapper.readValue(r.getAttachments(), new TypeReference<List<Object>>() {}));
                    } catch (Exception e) {
                        log.error("解析工单记录附件JSON失败", e);
                    }
                }
                recordVOs.add(rvo);
            }
            detail.setRecords(recordVOs);
        } else {
            detail.setRecords(List.of());
        }

        return detail;
    }

    // ==================== 处理工单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processDispatch(Long dispatchId, AlertProcessDTO dto) {
        AlertDispatch dispatch = dispatchMapper.selectById(dispatchId);
        if (dispatch == null) {
            throw BizException.notFound("派单记录不存在");
        }
        if (!"pending".equals(dispatch.getStatus()) && !"processing".equals(dispatch.getStatus()) && !"rejected".equals(dispatch.getStatus())) {
            throw BizException.conflict("只能处理待处理、处理中或已驳回状态的工单");
        }

        Map<String, Object> beforeData = new HashMap<>();
        beforeData.put("dispatchNo", dispatch.getDispatchNo());
        beforeData.put("status", dispatch.getStatus());

        // 更新派单记录
        dispatch.setHandleResult(dto.getHandleResult());
        dispatch.setReviewedBy(null);
        dispatch.setReviewedAt(null);
        dispatch.setReviewResult(null);
        dispatch.setReviewAttachments(null);
        if (dto.getHandleAttachments() != null && !dto.getHandleAttachments().isEmpty()) {
            dispatch.setHandleAttachments(JSONUtil.toJsonStr(dto.getHandleAttachments()));
        }
        dispatch.setStatus("completed");
        dispatch.setCompletedAt(LocalDateTime.now());
        dispatchMapper.updateById(dispatch);

        // 更新告警状态
        DeviceAlert alert = alertMapper.selectById(dispatch.getAlertId());
        if (alert != null) {
            alert.setHandledBy(dispatch.getHandlerId());
            alert.setHandledAt(LocalDateTime.now());
            alert.setHandleResult(dto.getHandleResult());
            alert.setReviewedBy(null);
            alert.setReviewedAt(null);
            alert.setReviewResult(null);
            alert.setReviewRemark(null);
            alert.setStatus("handled");
            alertMapper.updateById(alert);
        }

        // 工单操作记录
        createWorkOrderRecord(dispatchId, dispatch.getAlertId(), "complete", "处理完成",
                UserContext.getUserId(), UserContext.getRealName(),
                "处理完成：" + dto.getHandleResult(),
                dto.getHandleAttachments() != null && !dto.getHandleAttachments().isEmpty()
                        ? JSONUtil.toJsonStr(dto.getHandleAttachments()) : null);

        // 审计日志
        Map<String, Object> afterData = new HashMap<>();
        afterData.put("dispatchNo", dispatch.getDispatchNo());
        afterData.put("status", "completed");
        afterData.put("handleResult", dto.getHandleResult());

        auditLogService.log(AuditModule.DEVICE_ALERT, AuditOperationType.PROCESS,
                dispatch.getAlertId(), dispatch.getAlertNo(),
                "处理工单：" + dispatch.getDispatchNo(),
                JSONUtil.toJsonStr(beforeData), JSONUtil.toJsonStr(afterData));
    }

    // ==================== 复核工单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewDispatch(Long dispatchId, AlertReviewDTO dto) {
        AlertDispatch dispatch = dispatchMapper.selectById(dispatchId);
        if (dispatch == null) {
            throw BizException.notFound("派单记录不存在");
        }
        if (!"completed".equals(dispatch.getStatus())) {
            throw BizException.conflict("只能复核已处理状态的工单");
        }
        validateReviewDto(dto);

        Map<String, Object> beforeData = new HashMap<>();
        beforeData.put("dispatchNo", dispatch.getDispatchNo());
        beforeData.put("status", dispatch.getStatus());

        LocalDateTime now = LocalDateTime.now();
        boolean approved = dto.isApproved();
        String dispatchStatus = approved ? "reviewed" : "rejected";
        String reviewResultName = REVIEW_RESULT_NAMES.getOrDefault(dto.getReviewResult(), dto.getReviewResult());

        // 更新派单记录
        dispatch.setReviewedBy(UserContext.getUserId());
        dispatch.setReviewedAt(now);
        dispatch.setReviewResult(dto.getReviewResult());
        if (dto.getReviewAttachments() != null && !dto.getReviewAttachments().isEmpty()) {
            dispatch.setReviewAttachments(JSONUtil.toJsonStr(dto.getReviewAttachments()));
        } else {
            dispatch.setReviewAttachments(null);
        }
        dispatch.setStatus(dispatchStatus);
        dispatchMapper.updateById(dispatch);

        // 更新告警：复核通过时才更新告警状态，驳回时告警状态保持不变
        DeviceAlert alert = alertMapper.selectById(dispatch.getAlertId());
        if (alert != null) {
            alert.setReviewedBy(UserContext.getUserId());
            alert.setReviewedAt(now);
            alert.setReviewResult(dto.getReviewResult());
            alert.setReviewRemark(normalizeBlank(dto.getReviewRemark()));
            if (approved) {
                alert.setStatus("reviewed");
            }
            alertMapper.updateById(alert);
        }

        // 工单操作记录
        String reviewRemark = normalizeBlank(dto.getReviewRemark());
        String recordContent = approved
                ? ("复核通过" + (reviewRemark != null ? "：" + reviewRemark : ""))
                : "复核驳回：" + dto.getReviewRemark();
        createWorkOrderRecord(dispatchId, dispatch.getAlertId(), "review", "复核",
                UserContext.getUserId(), UserContext.getRealName(),
                recordContent,
                dto.getReviewAttachments() != null && !dto.getReviewAttachments().isEmpty()
                        ? JSONUtil.toJsonStr(dto.getReviewAttachments()) : null);

        // 审计日志
        Map<String, Object> afterData = new HashMap<>();
        afterData.put("dispatchNo", dispatch.getDispatchNo());
        afterData.put("status", dispatchStatus);
        afterData.put("reviewResult", dto.getReviewResult());
        afterData.put("alertStatus", approved ? "reviewed" : "不变");
        afterData.put("reviewRemark", normalizeBlank(dto.getReviewRemark()));

        auditLogService.log(AuditModule.DEVICE_ALERT, AuditOperationType.STATUS_CHANGE,
                dispatch.getAlertId(), dispatch.getAlertNo(),
                "复核工单：" + dispatch.getDispatchNo() + "，结果：" + reviewResultName,
                JSONUtil.toJsonStr(beforeData), JSONUtil.toJsonStr(afterData));
    }

    // ==================== 关闭告警 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, AlertCloseDTO dto) {
        DeviceAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw BizException.notFound("告警记录不存在");
        }
        validateClose(alert, dto);

        String alertNo = alert.getAlertNo();
        String oldStatus = alert.getStatus();
        String oldCloseRemark = alert.getCloseRemark();
        LocalDateTime now = LocalDateTime.now();

        alert.setStatus("closed");
        alert.setClosedAt(now);
        alert.setClosedBy(UserContext.getUserId());
        alert.setCloseRemark(normalizeBlank(dto.getCloseRemark()));
        alert.setArchivedAt(now);
        alert.setArchivedBy(UserContext.getUserId());
        alert.setArchiveRemark(normalizeBlank(dto.getArchiveRemark()));
        alertMapper.updateById(alert);

        createWorkOrderRecord(findLatestDispatchId(id), id, "complete", "关闭告警",
                UserContext.getUserId(), UserContext.getRealName(),
                buildCloseRecordContent(dto), null);

        Map<String, Object> beforeData = new HashMap<>();
        beforeData.put("alertNo", alertNo);
        beforeData.put("status", oldStatus);
        beforeData.put("closeRemark", oldCloseRemark);

        Map<String, Object> afterData = new HashMap<>();
        afterData.put("alertNo", alertNo);
        afterData.put("status", "closed");
        afterData.put("closeRemark", normalizeBlank(dto.getCloseRemark()));
        afterData.put("archiveRemark", normalizeBlank(dto.getArchiveRemark()));

        auditLogService.log(AuditModule.DEVICE_ALERT, AuditOperationType.STATUS_CHANGE,
                id, alertNo, "关闭告警：" + alertNo,
                JSONUtil.toJsonStr(beforeData), JSONUtil.toJsonStr(afterData));

        log.info("告警关闭成功，告警ID：{}", id);
    }

    @Override
    public void tryAutoDispatch(Long alertId) {
        try {
            DeviceAlert alert = alertMapper.selectById(alertId);
            if (alert == null || !"pending".equals(alert.getStatus())) {
                return;
            }
            if (alert.getAlertRuleId() == null) {
                return;
            }
            DeviceAlertRule rule = alertRuleMapper.selectById(alert.getAlertRuleId());
            if (rule == null || rule.getAutoDispatch() == null || rule.getAutoDispatch() != 1) {
                return;
            }

            AlertDispatchDTO dto = new AlertDispatchDTO();
            dto.setDispatchType("auto");
            dispatch(alertId, dto);
            log.info("自动派单成功: alertId={}, alertNo={}", alertId, alert.getAlertNo());
        } catch (Exception e) {
            log.warn("自动派单失败，告警保持待处理状态: alertId={}, error={}", alertId, e.getMessage());
        }
    }

    // ==================== 导出 ====================

    @Override
    @DataScope
    public void exportAlerts(AlertQueryDTO query, HttpServletResponse response) {
        try {
            // 复用列表查询逻辑，不分页
            Page<AlertVO> allPage = new Page<>(1, Integer.MAX_VALUE);
            Page<AlertVO> result = alertMapper.selectAlertPage(allPage, query);

            List<AlertVO> alerts = result.getRecords();
            for (AlertVO vo : alerts) {
                vo.setAlertTypeName(ALERT_TYPE_NAMES.getOrDefault(vo.getAlertType(), "未知"));
                vo.setAlertLevelName(ALERT_LEVEL_NAMES.getOrDefault(vo.getAlertLevel(), "未知"));
                vo.setStatusName(STATUS_NAMES.getOrDefault(vo.getStatus(), "未知"));
            }

            // 批量填充处理人姓名
            Set<Long> empIds = new HashSet<>();
            for (AlertVO vo : alerts) {
                if (vo.getAssignedTo() != null) empIds.add(vo.getAssignedTo());
            }
            Map<Long, String> nameMap = batchQueryEmployeeNames(empIds);
            for (AlertVO vo : alerts) {
                if (vo.getAssignedTo() != null) {
                    vo.setAssignedToName(nameMap.get(vo.getAssignedTo()));
                }
            }

            // 批量填充告警规则名称
            Set<Long> ruleIds = new HashSet<>();
            for (AlertVO vo : alerts) {
                if (vo.getAlertRuleId() != null) ruleIds.add(vo.getAlertRuleId());
            }
            Map<Long, String> ruleNameMap = new HashMap<>();
            for (Long ruleId : ruleIds) {
                DeviceAlertRule rule = alertRuleMapper.selectById(ruleId);
                if (rule != null) ruleNameMap.put(ruleId, rule.getRuleName());
            }
            for (AlertVO vo : alerts) {
                if (vo.getAlertRuleId() != null) {
                    vo.setAlertRuleName(ruleNameMap.get(vo.getAlertRuleId()));
                }
            }

            // 批量填充物料名称
            Map<Long, String> materialNameMap = new HashMap<>();
            Set<Long> materialIds = new HashSet<>();
            for (AlertVO vo : alerts) {
                if (vo.getMaterialId() != null) materialIds.add(vo.getMaterialId());
            }
            if (!materialIds.isEmpty()) {
                try {
                    String placeholders = String.join(",", materialIds.stream().map(String::valueOf).toList());
                    jdbcTemplate.query(
                            "SELECT id, material_name FROM wms_material WHERE id IN (" + placeholders + ") AND deleted = 0",
                            rs -> {
                                materialNameMap.put(rs.getLong("id"), rs.getString("material_name"));
                            });
                } catch (Exception e) {
                    log.warn("批量查询物料名称失败", e);
                }
            }

            // 构建 Excel
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String fileName = URLEncoder.encode("告警数据导出_" + timestamp, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("告警数据");

            // 样式：表头
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 样式：数据
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.LEFT);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            Font dataFont = workbook.createFont();
            dataFont.setFontName("微软雅黑");
            dataFont.setFontHeightInPoints((short) 10);
            dataStyle.setFont(dataFont);

            String[] headers = {"序号", "告警编号", "告警类型", "告警级别", "告警内容", "关联规则", "关联设备/物料", "状态", "处理人", "触发时间"};
            int[] columnWidths = {6, 22, 14, 10, 40, 20, 22, 10, 12, 20};

            // 表头
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(30);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, columnWidths[i] * 256);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            // 数据行
            int rowNum = 1;
            for (AlertVO vo : alerts) {
                Row row = sheet.createRow(rowNum++);
                // 关联设备/物料：设备告警显示设备名，物料告警显示物料名
                String deviceOrMaterial = "";
                if (vo.getMaterialId() != null) {
                    deviceOrMaterial = materialNameMap.getOrDefault(vo.getMaterialId(), "");
                } else if (vo.getDeviceName() != null) {
                    deviceOrMaterial = vo.getDeviceName();
                }
                String[] values = {
                        String.valueOf(rowNum - 1),
                        vo.getAlertNo() != null ? vo.getAlertNo() : "",
                        vo.getAlertTypeName() != null ? vo.getAlertTypeName() : "",
                        vo.getAlertLevelName() != null ? vo.getAlertLevelName() : "",
                        vo.getAlertContent() != null ? vo.getAlertContent() : "",
                        vo.getAlertRuleName() != null ? vo.getAlertRuleName() : "",
                        deviceOrMaterial,
                        vo.getStatusName() != null ? vo.getStatusName() : "",
                        vo.getAssignedToName() != null ? vo.getAssignedToName() : "",
                        vo.getTriggeredAt() != null ? vo.getTriggeredAt().format(dtf) : ""
                };
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            workbook.write(response.getOutputStream());
            workbook.close();

            // 审计日志
            auditLogService.log(AuditModule.DEVICE_ALERT, AuditOperationType.EXPORT, null, null,
                    "导出告警数据：" + alerts.size() + "条",
                    null, JSONUtil.toJsonStr(Map.of("count", alerts.size())), "success", null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("导出告警数据失败", e);
            try {
                auditLogService.log(AuditModule.DEVICE_ALERT, AuditOperationType.EXPORT, null, null,
                        "导出告警数据失败", null, null, "failure", e.getMessage());
            } catch (Exception ignored) {
            }
            throw new BizException("导出失败");
        }
    }

    private boolean hasPermission(String permissionCode) {
        Long userId = UserContext.getUserId();
        if (userId == null) return false;
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_user_role ur " +
                        "JOIN auth_role r ON r.id = ur.role_id " +
                        "JOIN auth_role_permission rp ON rp.role_id = r.id " +
                        "JOIN auth_permission p ON p.id = rp.permission_id " +
                        "WHERE ur.user_id = ? AND r.deleted = 0 AND r.status = 'active' AND p.status = 'active' AND p.permission_code = ?",
                Long.class, userId, permissionCode);
        return count != null && count > 0L;
    }

    // ==================== 辅助方法 ====================

    /**
     * 批量查询员工姓名（通过 JdbcTemplate 查询 sys_employee）
     * @param userIds 用户ID集合
     * @return userId -> realName 映射
     */
    /**
     * 根据 sys_employee.id 批量查询用户姓名
     * 适用于 assigned_to / handled_by 等 sys_employee.id
     */
    private Map<Long, String> batchQueryEmployeeNames(Set<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) return Map.of();
        Map<Long, String> nameMap = new HashMap<>();
        try {
            String placeholders = employeeIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
            String sql = "SELECT e.id, e.real_name FROM sys_employee e WHERE e.id IN (" + placeholders + ") AND e.deleted = 0";
            jdbcTemplate.query(sql, rs -> {
                nameMap.put(rs.getLong("id"), rs.getString("real_name"));
            }, employeeIds.toArray());
        } catch (Exception e) {
            log.warn("批量查询员工姓名失败", e);
        }
        return nameMap;
    }

    /**
     * 根据 dict_type 查询字典项 code → name 映射
     * 只查启用且未删除的项
     */
    private Map<String, String> getDictNames(String dictType) {
        Map<String, String> map = new HashMap<>();
        try {
            String sql = "SELECT dict_code, dict_name FROM sys_dict WHERE dict_type = ? AND status = 'active' AND deleted = 0";
            jdbcTemplate.query(sql, rs -> {
                map.put(rs.getString("dict_code"), rs.getString("dict_name"));
            }, dictType);
        } catch (Exception e) {
            log.warn("查询字典失败: dictType={}", dictType, e);
        }
        return map;
    }

    /**
     * 根据 auth_user.id 批量查询用户姓名
     * 适用于 reviewed_by 等 UserContext.getUserId() 返回的 auth_user.id
     */
    private Map<Long, String> batchQueryUserNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        Map<Long, String> nameMap = new HashMap<>();
        try {
            String placeholders = userIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
            String sql = "SELECT u.id, u.real_name FROM auth_user u WHERE u.id IN (" + placeholders + ") AND u.deleted = 0";
            jdbcTemplate.query(sql, rs -> {
                nameMap.put(rs.getLong("id"), rs.getString("real_name"));
            }, userIds.toArray());
        } catch (Exception e) {
            log.warn("批量查询用户姓名失败", e);
        }
        return nameMap;
    }

    private Set<Long> buildNonNullIdSet(Long... ids) {
        Set<Long> result = new HashSet<>();
        if (ids == null) {
            return result;
        }
        for (Long id : ids) {
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    private void validateReviewDto(AlertReviewDTO dto) {
        if (!dto.isApproved() && !dto.isRejected()) {
            throw BizException.badRequest("复核结果仅支持 approved 或 rejected");
        }
        if (dto.isRejected() && normalizeBlank(dto.getReviewRemark()) == null) {
            throw BizException.badRequest("复核驳回时必须填写驳回原因");
        }
    }

    private void validateClose(DeviceAlert alert, AlertCloseDTO dto) {
        if (!"reviewed".equals(alert.getStatus())) {
            throw BizException.conflict("仅已复核通过的告警允许关闭");
        }
        if (!REVIEW_RESULT_APPROVED.equals(alert.getReviewResult())) {
            throw BizException.conflict("复核未通过的告警不能关闭");
        }
        if (alert.getAssignedTo() == null || alert.getAssignedAt() == null) {
            throw BizException.conflict("告警尚未完成派单，不能关闭");
        }
        if (alert.getHandledBy() == null || alert.getHandledAt() == null || normalizeBlank(alert.getHandleResult()) == null) {
            throw BizException.conflict("告警尚未完成处理，不能关闭");
        }
        if (alert.getReviewedBy() == null || alert.getReviewedAt() == null) {
            throw BizException.conflict("告警尚未完成复核，不能关闭");
        }
        if (normalizeBlank(dto.getCloseRemark()) == null) {
            throw BizException.badRequest("关闭告警必须填写关闭说明");
        }

        // 证据链完整性校验：AI 违规告警如果曾关联录像，需确认录像仍存在
        if ("ai_violation".equals(alert.getAlertType()) && alert.getRecordingId() != null) {
            Long recordingExists = deviceMonitorRecordMapper.selectCount(
                    new LambdaQueryWrapper<DeviceMonitorRecord>()
                            .eq(DeviceMonitorRecord::getId, alert.getRecordingId())
                            .eq(DeviceMonitorRecord::getDeleted, 0));
            if (recordingExists == null || recordingExists == 0) {
                throw BizException.badRequest("告警关联的证据录像已被删除，无法闭环。请解除录像关联后重试");
            }
        }
    }

    private Long findLatestDispatchId(Long alertId) {
        AlertDispatch dispatch = dispatchMapper.selectOne(
                new LambdaQueryWrapper<AlertDispatch>()
                        .eq(AlertDispatch::getAlertId, alertId)
                        .orderByDesc(AlertDispatch::getCreatedAt)
                        .last("LIMIT 1"));
        return dispatch != null ? dispatch.getId() : null;
    }

    private String buildCloseRecordContent(AlertCloseDTO dto) {
        String closeRemark = normalizeBlank(dto.getCloseRemark());
        String archiveRemark = normalizeBlank(dto.getArchiveRemark());
        if (archiveRemark == null) {
            return "告警关闭：" + closeRemark;
        }
        return "告警关闭：" + closeRemark + "；归档说明：" + archiveRemark;
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void createWorkOrderRecord(Long dispatchId, Long alertId, String action, String actionName,
                                       Long operatorId, String operatorName, String content, String attachments) {
        AlertWorkOrderRecord record = new AlertWorkOrderRecord();
        record.setDispatchId(dispatchId);
        record.setAlertId(alertId);
        record.setAction(action);
        record.setActionName(actionName);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setContent(content);
        record.setAttachments(attachments);
        workOrderRecordMapper.insert(record);
    }

    @Override
    public Map<String, Object> testDb() {
        Map<String, Object> result = new HashMap<>();
        long count = alertMapper.selectCount(
                new LambdaQueryWrapper<DeviceAlert>().eq(DeviceAlert::getDeleted, 0));
        result.put("count", count);
        return result;
    }

    /**
     * 根据告警关联规则的派单范围，解析角色ID列表
     * @return 角色ID列表，无关联规则或无派单范围时返回 null
     */
    private List<Long> resolveDispatchRoleIds(Long alertRuleId) {
        if (alertRuleId == null) return null;
        DeviceAlertRule rule = alertRuleMapper.selectById(alertRuleId);
        if (rule == null || rule.getDispatchScopeRoles() == null || rule.getDispatchScopeRoles().isBlank()) {
            return null;
        }
        List<Long> roleIds = new ArrayList<>();
        for (String idStr : rule.getDispatchScopeRoles().split(",")) {
            try {
                roleIds.add(Long.parseLong(idStr.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return roleIds.isEmpty() ? null : roleIds;
    }

    @Override
    public List<Map<String, Object>> getDispatchHandlers(Long alertId) {
        DeviceAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw BizException.notFound("告警记录不存在");
        }

        List<Long> roleIds = resolveDispatchRoleIds(alert.getAlertRuleId());

        // 未配置派单范围时返回空列表（人工派单也必须从派单范围内选择）
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }

        // 按角色过滤：查询拥有指定角色的活跃员工
        String placeholders = String.join(",", roleIds.stream().map(String::valueOf).toList());
        String sql = "SELECT DISTINCT e.id, e.real_name, e.org_id, o.org_name " +
                "FROM sys_employee e " +
                "INNER JOIN auth_user u ON e.user_id = u.id " +
                "INNER JOIN auth_user_role ur ON ur.user_id = u.id " +
                "INNER JOIN auth_role r ON r.id = ur.role_id " +
                "LEFT JOIN sys_organization o ON e.org_id = o.id " +
                "WHERE ur.role_id IN (" + placeholders + ") " +
                "AND e.status = 'active' AND u.status = 'active' " +
                "AND e.deleted = 0 AND r.deleted = 0 AND r.status = 'active' " +
                "ORDER BY e.real_name";
        return jdbcTemplate.queryForList(sql);
    }
}
