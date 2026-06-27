package com.xykj.device.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.entity.SysAuditLog;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.mapper.SysAuditLogMapper;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.device.dto.DeviceCreateDTO;
import com.xykj.device.dto.DeviceOnlineStatusUpdateDTO;
import com.xykj.device.dto.DeviceQueryDTO;
import com.xykj.device.dto.DeviceUpdateDTO;
import com.xykj.device.dto.DeviceImportDTO;
import com.xykj.device.dto.DeviceImportResultDTO;
import com.xykj.device.dto.DeviceBatchItemResultDTO;
import com.xykj.device.dto.DeviceBatchOperationResultDTO;
import com.xykj.device.entity.DeviceAlert;
import com.xykj.device.entity.DeviceInfo;
import com.xykj.device.entity.DeviceMonitorRecord;
import com.xykj.device.event.DeviceOnlineStatusEvent;
import com.xykj.device.mapper.DeviceAlertMapper;
import com.xykj.device.mapper.DeviceAlertRuleMapper;
import com.xykj.device.entity.DeviceAlertRule;
import com.xykj.device.mapper.DeviceInfoMapper;
import com.xykj.device.mapper.DeviceMonitorRecordMapper;
import com.xykj.device.service.DeviceInfoService;
import com.xykj.device.service.RecordingProcessService;
import com.xykj.device.service.StreamTranscodeService;
import com.xykj.device.vo.DeviceDashboardVO;
import com.xykj.device.vo.DeviceDetailVO;
import com.xykj.device.vo.DeviceStatusLogVO;
import com.xykj.device.vo.DeviceVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceInfoServiceImpl extends ServiceImpl<DeviceInfoMapper, DeviceInfo> implements DeviceInfoService {

    private static final String ONLINE_STATUS_ONLINE = "online";
    private static final String ONLINE_STATUS_OFFLINE = "offline";
    private static final String ONLINE_STATUS_FAULT = "fault";

    private static final String BUSINESS_STATUS_ACTIVE = "active";
    private static final String BUSINESS_STATUS_INACTIVE = "inactive";
    private static final String BUSINESS_STATUS_MAINTENANCE = "maintenance";

    private static final String STATUS_TYPE_BUSINESS = "business";
    private static final String STATUS_TYPE_ONLINE = "online";

    private static final String SOURCE_TYPE_SYSTEM = "system";
    private static final String SOURCE_TYPE_MANUAL = "manual";
    private static final String SOURCE_TYPE_CREATE = "create";

    private static final String ALERT_TYPE_DEVICE_OFFLINE = "offline";
    private static final String ALERT_TYPE_DEVICE_FAULT = "device_fault";

    private static final String DEVICE_EDIT_PERMISSION = "device:edit";
    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final Long DEFAULT_USER_ID = 0L;

    private static final Set<String> ONLINE_STATUS_VALUES = Set.of(
            ONLINE_STATUS_ONLINE, ONLINE_STATUS_OFFLINE, ONLINE_STATUS_FAULT
    );
    private static final Set<String> BUSINESS_STATUS_VALUES = Set.of(
            BUSINESS_STATUS_ACTIVE, BUSINESS_STATUS_INACTIVE, BUSINESS_STATUS_MAINTENANCE
    );
    private static final Set<String> OPEN_ALERT_STATUSES = Set.of("pending", "assigned", "handling");
    private static final Set<String> DEVICE_STATE_ALERT_TYPES = Set.of(ALERT_TYPE_DEVICE_OFFLINE, ALERT_TYPE_DEVICE_FAULT);
    private static final Set<String> ROLE_CODE_ALLOWLIST = Set.of(
            "SUPER_ADMIN", "ORG_ADMIN", "DEVICE_ADMIN", "DEVICE_MANAGER", "OPS", "OPS_ADMIN", "DEVICE_OPS"
    );
    private static final List<String> BUSINESS_STATUS_ROLE_KEYWORDS = List.of("设备管理员", "组织管理员", "运维", "管理员");
    private static final List<String> ONLINE_STATUS_ROLE_KEYWORDS = List.of("运维", "管理员");

    private final ObjectMapper objectMapper;
    private final DataScopeService dataScopeService;
    private final AuditLogService auditLogService;
    private final DeviceAlertMapper deviceAlertMapper;
    private final DeviceAlertRuleMapper alertRuleMapper;
    private final com.xykj.device.service.DeviceAlertService deviceAlertService;
    private final SysAuditLogMapper sysAuditLogMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DeviceMonitorRecordMapper deviceMonitorRecordMapper;
    private final StreamTranscodeService streamTranscodeService;
    private final RecordingProcessService recordingProcessService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /** 导入并发控制：按租户ID互斥 */
    private static final ConcurrentHashMap<Long, Boolean> importLocks = new ConcurrentHashMap<>();

    /** 设备类型名称映射 */
    private static final Map<String, String> DEVICE_TYPE_MAP = new LinkedHashMap<>();
    /** 在线状态名称映射 */
    private static final Map<String, String> ONLINE_STATUS_MAP = new HashMap<>();
    /** 设备状态名称映射 */
    private static final Map<String, String> STATUS_MAP = new HashMap<>();
    /** 状态来源名称映射 */
    private static final Map<String, String> STATUS_SOURCE_MAP = new HashMap<>();

    static {
        DEVICE_TYPE_MAP.put("camera", "监控摄像头");
        DEVICE_TYPE_MAP.put("sensor", "温湿度传感器");
        DEVICE_TYPE_MAP.put("scale", "食材检测设备");
        DEVICE_TYPE_MAP.put("gas_detector", "气体监测设备");
        DEVICE_TYPE_MAP.put("sample_terminal", "智能留样设备");
        DEVICE_TYPE_MAP.put("health_terminal", "智能晨检设备");

        ONLINE_STATUS_MAP.put(ONLINE_STATUS_ONLINE, "在线");
        ONLINE_STATUS_MAP.put(ONLINE_STATUS_OFFLINE, "离线");
        ONLINE_STATUS_MAP.put(ONLINE_STATUS_FAULT, "故障");

        STATUS_MAP.put(BUSINESS_STATUS_ACTIVE, "启用");
        STATUS_MAP.put(BUSINESS_STATUS_INACTIVE, "停用");
        STATUS_MAP.put(BUSINESS_STATUS_MAINTENANCE, "维护中");

        STATUS_SOURCE_MAP.put(SOURCE_TYPE_SYSTEM, "系统自动");
        STATUS_SOURCE_MAP.put(SOURCE_TYPE_MANUAL, "人工修正");
        STATUS_SOURCE_MAP.put(SOURCE_TYPE_CREATE, "初始建档");
    }

    @Override
    @DataScope
    public DeviceDashboardVO getDashboard(DeviceQueryDTO query) {
        DeviceDashboardVO vo = new DeviceDashboardVO();

        LambdaQueryWrapper<DeviceInfo> scopedBase = buildScopedWrapper(query);
        vo.setTotalCount((int) count(scopedBase));

        vo.setOnlineCount((int) count(buildScopedWrapper(query)
                .eq(DeviceInfo::getStatus, BUSINESS_STATUS_ACTIVE)
                .eq(DeviceInfo::getOnlineStatus, ONLINE_STATUS_ONLINE)));

        vo.setOfflineCount((int) count(buildScopedWrapper(query)
                .eq(DeviceInfo::getStatus, BUSINESS_STATUS_ACTIVE)
                .eq(DeviceInfo::getOnlineStatus, ONLINE_STATUS_OFFLINE)));

        // 报警数量：统计 device_alert 表中未关闭的告警
        Long orgId = query.getOrgId();
        List<Long> orgIds = query.getOrgIds();
        LambdaQueryWrapper<DeviceAlert> alertWrapper = new LambdaQueryWrapper<>();
        alertWrapper.ne(DeviceAlert::getStatus, "closed");
        alertWrapper.eq(DeviceAlert::getDeleted, 0);
        if (orgId != null) {
            alertWrapper.eq(DeviceAlert::getOrgId, orgId);
        } else if (orgIds != null && !orgIds.isEmpty()) {
            alertWrapper.in(DeviceAlert::getOrgId, orgIds);
        }
        vo.setAlertCount(Math.toIntExact(deviceAlertMapper.selectCount(alertWrapper)));

        vo.setMaintenanceCount((int) count(buildScopedWrapper(query)
                .eq(DeviceInfo::getStatus, BUSINESS_STATUS_MAINTENANCE)));

        List<DeviceDashboardVO.DeviceTypeStat> typeStats = new ArrayList<>();
        for (Map.Entry<String, String> entry : DEVICE_TYPE_MAP.entrySet()) {
            int typeCount = (int) count(buildScopedWrapper(query)
                    .eq(DeviceInfo::getDeviceType, entry.getKey()));
            if (typeCount <= 0) {
                continue;
            }

            DeviceDashboardVO.DeviceTypeStat stat = new DeviceDashboardVO.DeviceTypeStat();
            stat.setDeviceType(entry.getKey());
            stat.setDeviceTypeName(entry.getValue());
            stat.setCount(typeCount);
            stat.setOnlineCount((int) count(buildScopedWrapper(query)
                    .eq(DeviceInfo::getDeviceType, entry.getKey())
                    .eq(DeviceInfo::getStatus, BUSINESS_STATUS_ACTIVE)
                    .eq(DeviceInfo::getOnlineStatus, ONLINE_STATUS_ONLINE)));
            stat.setOfflineCount((int) count(buildScopedWrapper(query)
                    .eq(DeviceInfo::getDeviceType, entry.getKey())
                    .eq(DeviceInfo::getStatus, BUSINESS_STATUS_ACTIVE)
                    .eq(DeviceInfo::getOnlineStatus, ONLINE_STATUS_OFFLINE)));
            typeStats.add(stat);
        }
        vo.setDeviceTypeStats(typeStats);

        return vo;
    }

    @Override
    @DataScope
    public Page<DeviceVO> list(DeviceQueryDTO query) {
        Page<DeviceInfo> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<DeviceInfo> wrapper = buildScopedWrapper(query);
        wrapper.eq(StrUtil.isNotBlank(query.getDeviceType()), DeviceInfo::getDeviceType, query.getDeviceType());
        wrapper.eq(StrUtil.isNotBlank(query.getOnlineStatus()), DeviceInfo::getOnlineStatus, query.getOnlineStatus());
        wrapper.eq(StrUtil.isNotBlank(query.getStatus()), DeviceInfo::getStatus, query.getStatus());
        if (StrUtil.isNotBlank(query.getDeviceName())) {
            wrapper.and(w -> w
                    .like(DeviceInfo::getDeviceName, query.getDeviceName())
                    .or()
                    .like(DeviceInfo::getDeviceCode, query.getDeviceName())
            );
        }
        wrapper.orderByDesc(DeviceInfo::getUpdatedAt);

        Page<DeviceInfo> result = page(page, wrapper);

        List<DeviceVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        Page<DeviceVO> resultPage = new Page<>(query.getPageNum(), query.getPageSize());
        resultPage.setRecords(voList);
        resultPage.setTotal(result.getTotal());
        return resultPage;
    }

    @Override
    public DeviceDetailVO getDetail(Long id) {
        DeviceInfo device = getDeviceOrThrow(id);
        return convertToDetailVO(device);
    }

    @Override
    public List<DeviceStatusLogVO> getStatusLogs(Long id) {
        DeviceInfo device = getDeviceOrThrow(id);
        List<SysAuditLog> auditLogs = sysAuditLogMapper.selectList(
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(SysAuditLog::getModuleCode, AuditModule.DEVICE_INFO.getCode())
                        .eq(SysAuditLog::getTargetId, device.getId())
                        .orderByDesc(SysAuditLog::getCreatedAt)
        );
        return auditLogs.stream()
                .map(this::convertAuditLogToStatusLog)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.DEVICE_INFO,
            operationType = AuditOperationType.CREATE,
            targetId = "#result",
            desc = "'新增设备：' + #dto.deviceName",
            mapper = DeviceInfoMapper.class
    )
    public Long create(DeviceCreateDTO dto) {
        Long targetOrgId = UserContext.getOrgId() != null ? UserContext.getOrgId() : dto.getOrgId();
        ensureOrgAllowed(targetOrgId);

        // 租户内设备编码唯一性校验
        Long tenantId = resolveTenantId();
        Long existCount = count(new LambdaQueryWrapper<DeviceInfo>()
                .eq(DeviceInfo::getDeviceCode, dto.getDeviceCode())
                .eq(DeviceInfo::getTenantId, tenantId));
        if (existCount > 0) {
            throw BizException.badRequest("设备编码已存在: " + dto.getDeviceCode());
        }

        DeviceInfo device = new DeviceInfo();
        BeanUtils.copyProperties(dto, device);
        device.setOnlineStatus(ONLINE_STATUS_OFFLINE);
        device.setStatus(normalizeBusinessStatus(dto.getStatus(), BUSINESS_STATUS_ACTIVE));
        device.setConfigParams(dto.getConfigParams());
        device.setOrgId(targetOrgId);
        device.setTenantId(resolveTenantId());

        save(device);
        applyBusinessStatusRules(device, device.getStatus(), "新增设备初始化业务状态");
        recordStatusHistory(device, STATUS_TYPE_ONLINE, null, ONLINE_STATUS_OFFLINE, SOURCE_TYPE_CREATE, "新增设备初始化在线状态");
        recordStatusHistory(device, STATUS_TYPE_BUSINESS, null, device.getStatus(), SOURCE_TYPE_CREATE, "新增设备初始化业务状态");

        log.info("新增设备成功: id={}, code={}, name={}", device.getId(), device.getDeviceCode(), device.getDeviceName());
        return device.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.DEVICE_INFO,
            operationType = AuditOperationType.UPDATE,
            targetId = "#id",
            desc = "'编辑设备：' + #entity.deviceName",
            mapper = DeviceInfoMapper.class
    )
    public void update(Long id, DeviceUpdateDTO dto) {
        DeviceInfo device = getDeviceOrThrow(id);
        String oldBusinessStatus = device.getStatus();
        String targetBusinessStatus = oldBusinessStatus;
        String businessStatusReason = null;
        boolean businessStatusChanged = false;

        if (dto.getStatus() != null) {
            targetBusinessStatus = normalizeBusinessStatus(dto.getStatus(), null);
            if (!Objects.equals(oldBusinessStatus, targetBusinessStatus)) {
                ensureBusinessStatusManageAllowed();
                validateBusinessStatusTransition(oldBusinessStatus, targetBusinessStatus);
                businessStatusReason = normalizeRequiredReason(dto.getStatusChangeReason(), "请输入设备状态变更原因");
                businessStatusChanged = true;
                device.setStatus(targetBusinessStatus);
            }
        }

        if (dto.getDeviceName() != null) device.setDeviceName(dto.getDeviceName());
        if (dto.getDeviceModel() != null) device.setDeviceModel(dto.getDeviceModel());
        if (dto.getManufacturer() != null) device.setManufacturer(dto.getManufacturer());
        if (dto.getSn() != null) device.setSn(dto.getSn());
        if (dto.getMacAddress() != null) device.setMacAddress(dto.getMacAddress());
        if (dto.getIpAddress() != null) device.setIpAddress(dto.getIpAddress());
        if (dto.getLocationDesc() != null) device.setLocationDesc(dto.getLocationDesc());
        if (dto.getConfigParams() != null) device.setConfigParams(dto.getConfigParams());
        if (dto.getInstallDate() != null) device.setInstallDate(dto.getInstallDate());
        if (dto.getWarrantyExpiresAt() != null) device.setWarrantyExpiresAt(dto.getWarrantyExpiresAt());
        if (dto.getMaintenanceCycleDays() != null) device.setMaintenanceCycleDays(dto.getMaintenanceCycleDays());
        if (dto.getNextMaintenanceAt() != null) device.setNextMaintenanceAt(dto.getNextMaintenanceAt());
        if (dto.getRemark() != null) device.setRemark(dto.getRemark());
        if (dto.getManagerName() != null) device.setManagerName(dto.getManagerName());
        if (dto.getManagerPhone() != null) device.setManagerPhone(dto.getManagerPhone());
        if (dto.getOrgId() != null) {
            ensureOrgAllowed(dto.getOrgId());
            device.setOrgId(dto.getOrgId());
        }

        // 状态从启用变为非启用时，检查活跃录像和未关闭告警（与 toggleDeviceStatusInternal 保持一致）
        if (businessStatusChanged && BUSINESS_STATUS_ACTIVE.equals(oldBusinessStatus) && !BUSINESS_STATUS_ACTIVE.equals(targetBusinessStatus)) {
            Long activeRecordings = deviceMonitorRecordMapper.selectCount(
                    new LambdaQueryWrapper<DeviceMonitorRecord>()
                            .eq(DeviceMonitorRecord::getDeviceId, id)
                            .eq(DeviceMonitorRecord::getStatus, "recording"));
            if (activeRecordings != null && activeRecordings > 0) {
                throw BizException.badRequest("该设备存在进行中的录像任务，无法禁用");
            }

            Long activeAlerts = deviceAlertMapper.selectCount(
                    new LambdaQueryWrapper<DeviceAlert>()
                            .eq(DeviceAlert::getDeviceId, id)
                            .ne(DeviceAlert::getStatus, "closed"));
            if (activeAlerts != null && activeAlerts > 0) {
                throw BizException.badRequest("该设备存在未关闭的告警，无法禁用");
            }
        }

        boolean updated = updateById(device);
        if (!updated) {
            throw BizException.conflict("该设备已被其他用户修改，请刷新页面后重试");
        }

        if (businessStatusChanged) {
            // 从启用变为停用/维护中/故障时：停止 FFmpeg 进程 + 标记离线
            if (BUSINESS_STATUS_ACTIVE.equals(oldBusinessStatus) && !BUSINESS_STATUS_ACTIVE.equals(targetBusinessStatus)) {
                if ("camera".equals(device.getDeviceType())) {
                    streamTranscodeService.stopTranscode(id);
                    recordingProcessService.stopRecording(id);
                }
                device.setOnlineStatus("offline");
                updateById(device);
            }
            applyBusinessStatusRules(device, targetBusinessStatus, businessStatusReason);
            recordStatusHistory(device, STATUS_TYPE_BUSINESS, oldBusinessStatus, targetBusinessStatus, SOURCE_TYPE_MANUAL, businessStatusReason);
        }

        log.info("编辑设备成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.DEVICE_INFO,
            operationType = AuditOperationType.DELETE,
            targetId = "#id",
            desc = "'删除设备：' + #entity.deviceName",
            mapper = DeviceInfoMapper.class
    )
    public void delete(Long id) {
        DeviceInfo device = getDeviceOrThrow(id);
        deleteDeviceInternal(device);
    }

    @Override
    public DeviceBatchOperationResultDTO batchDelete(List<Long> ids) {
        return executeBatchOperation(ids, null, this::deleteDeviceInternal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOnlineStatus(Long id, DeviceOnlineStatusUpdateDTO dto) {
        DeviceInfo device = getDeviceOrThrow(id);
        String currentStatus = device.getOnlineStatus();
        String targetStatus = normalizeOnlineStatus(dto.getOnlineStatus());
        String sourceType = normalizeSourceType(dto.getSourceType());

        if (Objects.equals(currentStatus, targetStatus)) {
            if (SOURCE_TYPE_SYSTEM.equals(sourceType)) {
                device.setLastHeartbeatAt(LocalDateTime.now());
                updateById(device);
                log.info("设备心跳刷新完成但在线状态未变化: id={}, onlineStatus={}", id, currentStatus);
                return;
            }
            throw BizException.badRequest("在线状态未发生变化");
        }

        String reason;
        if (SOURCE_TYPE_MANUAL.equals(sourceType)) {
            ensureOnlineStatusManageAllowed();
            validateManualOnlineStatusTransition(currentStatus, targetStatus);
            reason = normalizeRequiredReason(dto.getReason(), "请输入在线状态修正原因");
        } else {
            validateSystemOnlineStatusTransition(targetStatus);
            reason = StrUtil.blankToDefault(StrUtil.trim(dto.getReason()), buildSystemOnlineStatusReason(targetStatus));
            if (ONLINE_STATUS_FAULT.equals(currentStatus)) {
                device.setLastHeartbeatAt(LocalDateTime.now());
                updateById(device);
                log.info("忽略系统心跳对人工故障状态的覆盖: id={}, currentStatus={}, targetStatus={}", id, currentStatus, targetStatus);
                return;
            }
        }

        device.setOnlineStatus(targetStatus);
        if (ONLINE_STATUS_ONLINE.equals(targetStatus)) {
            device.setLastHeartbeatAt(LocalDateTime.now());
            // 摄像头恢复在线时启动 FFmpeg 进程
            if ("camera".equals(device.getDeviceType())) {
                String configParams = device.getConfigParams();
                if (configParams != null) {
                    try {
                        Map<String, Object> config = objectMapper.readValue(configParams,
                                new com.fasterxml.jackson.core.type.TypeReference<>() {});
                        String rtspUrl = (String) config.get("rtspUrl");
                        if (rtspUrl == null) {
                            String streamUrl = (String) config.get("streamUrl");
                            if (streamUrl != null && streamUrl.startsWith("rtsp://")) {
                                rtspUrl = streamUrl;
                            }
                        }
                        if (rtspUrl == null) {
                            for (Map.Entry<String, Object> entry : config.entrySet()) {
                                String key = entry.getKey();
                                if (key == null) continue;
                                String normalizedKey = key.toLowerCase(java.util.Locale.ROOT);
                                if (!normalizedKey.contains("rtsp") && !normalizedKey.contains("stream")) {
                                    continue;
                                }
                                Object value = entry.getValue();
                                if (value == null) continue;
                                String candidate = value.toString().trim();
                                if (candidate.startsWith("rtsp://")) {
                                    rtspUrl = candidate;
                                    break;
                                }
                            }
                        }
                        if (rtspUrl != null) {
                            streamTranscodeService.startTranscode(id, rtspUrl);
                            Object recordingEnabled = config.get("recordingEnabled");
                            if (recordingEnabled != null && Boolean.TRUE.equals(Boolean.parseBoolean(recordingEnabled.toString()))) {
                                recordingProcessService.startRecording(id, rtspUrl);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("故障恢复后启动FFmpeg失败: deviceId={}, error={}", id, e.getMessage());
                    }
                }
            }
        } else if ("camera".equals(device.getDeviceType())) {
            // 摄像头设为离线/故障时停止 FFmpeg 进程
            streamTranscodeService.stopTranscode(id);
            recordingProcessService.stopRecording(id);
        }
        updateById(device);

        applyOnlineStatusRules(device, targetStatus, sourceType, reason);
        recordStatusHistory(device, STATUS_TYPE_ONLINE, currentStatus, targetStatus, sourceType, reason);

        // 发布设备状态变更事件（SSE 推送）
        eventPublisher.publishEvent(new DeviceOnlineStatusEvent(
                this, device.getId(), device.getDeviceName(), device.getDeviceType(), currentStatus, targetStatus));

        log.info("更新设备在线状态成功: id={}, from={}, to={}, sourceType={}", id, currentStatus, targetStatus, sourceType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
            module = AuditModule.DEVICE_INFO,
            operationType = AuditOperationType.UPDATE,
            targetId = "#id",
            desc = "'切换设备状态'",
            mapper = DeviceInfoMapper.class
    )
    public void toggleStatus(Long id) {
        DeviceInfo device = getDeviceOrThrow(id);
        toggleDeviceStatusInternal(device);
    }

    @Override
    public DeviceBatchOperationResultDTO batchEnable(List<Long> ids) {
        return executeBatchOperation(ids, BUSINESS_STATUS_ACTIVE, this::toggleDeviceStatusInternal);
    }

    @Override
    public DeviceBatchOperationResultDTO batchDisable(List<Long> ids) {
        return executeBatchOperation(ids, BUSINESS_STATUS_INACTIVE, this::toggleDeviceStatusInternal);
    }

    @Override
    @DataScope
    public void exportDevices(DeviceQueryDTO query, HttpServletResponse response) {
        try {
            LambdaQueryWrapper<DeviceInfo> wrapper = buildListWrapper(query);
            List<DeviceInfo> devices = list(wrapper);

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("设备列表");

                String[] headers = {"设备编码", "设备名称", "设备类型", "设备型号", "厂商",
                        "序列号", "MAC地址", "IP地址", "安装位置", "所属组织",
                        "在线状态", "设备状态", "负责人姓名", "负责人电话",
                        "维保周期(天)", "安装日期", "保修到期", "下次维保",
                        "最后心跳时间", "备注"};
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    headerRow.createCell(i).setCellValue(headers[i]);
                }

                // 批量解析组织名称
                Set<Long> orgIds = devices.stream()
                        .map(DeviceInfo::getOrgId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                Map<Long, String> orgNameMap = resolveOrgNames(orgIds);

                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                int rowNum = 1;
                for (DeviceInfo d : devices) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(d.getDeviceCode() != null ? d.getDeviceCode() : "");
                    row.createCell(1).setCellValue(d.getDeviceName() != null ? d.getDeviceName() : "");
                    row.createCell(2).setCellValue(DEVICE_TYPE_MAP.getOrDefault(d.getDeviceType(), d.getDeviceType() != null ? d.getDeviceType() : ""));
                    row.createCell(3).setCellValue(d.getDeviceModel() != null ? d.getDeviceModel() : "");
                    row.createCell(4).setCellValue(d.getManufacturer() != null ? d.getManufacturer() : "");
                    row.createCell(5).setCellValue(d.getSn() != null ? d.getSn() : "");
                    row.createCell(6).setCellValue(d.getMacAddress() != null ? d.getMacAddress() : "");
                    row.createCell(7).setCellValue(d.getIpAddress() != null ? d.getIpAddress() : "");
                    row.createCell(8).setCellValue(d.getLocationDesc() != null ? d.getLocationDesc() : "");
                    row.createCell(9).setCellValue(orgNameMap.getOrDefault(d.getOrgId(), ""));
                    row.createCell(10).setCellValue(ONLINE_STATUS_MAP.getOrDefault(d.getOnlineStatus(), d.getOnlineStatus() != null ? d.getOnlineStatus() : ""));
                    row.createCell(11).setCellValue(STATUS_MAP.getOrDefault(d.getStatus(), d.getStatus() != null ? d.getStatus() : ""));
                    row.createCell(12).setCellValue(d.getManagerName() != null ? d.getManagerName() : "");
                    row.createCell(13).setCellValue(d.getManagerPhone() != null ? d.getManagerPhone() : "");
                    row.createCell(14).setCellValue(d.getMaintenanceCycleDays() != null ? String.valueOf(d.getMaintenanceCycleDays()) : "");
                    row.createCell(15).setCellValue(d.getInstallDate() != null ? d.getInstallDate().toString() : "");
                    row.createCell(16).setCellValue(d.getWarrantyExpiresAt() != null ? d.getWarrantyExpiresAt().toString() : "");
                    row.createCell(17).setCellValue(d.getNextMaintenanceAt() != null ? d.getNextMaintenanceAt().toString() : "");
                    row.createCell(18).setCellValue(d.getLastHeartbeatAt() != null ? d.getLastHeartbeatAt().format(dtf) : "");
                    row.createCell(19).setCellValue(d.getRemark() != null ? d.getRemark() : "");
                }

                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }

                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setCharacterEncoding("utf-8");
                String fileName = URLEncoder.encode("设备列表", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
                response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
                workbook.write(response.getOutputStream());
            }

            log.info("导出设备列表，共{}条", devices.size());
        } catch (IOException e) {
            throw BizException.badRequest("导出设备列表失败: " + e.getMessage());
        }
    }

    @Override
    public DeviceImportResultDTO importDevices(MultipartFile file) {
        if (file.isEmpty()) {
            throw BizException.badRequest("上传的文件为空");
        }

        Long tenantId = UserContext.getTenantId() != null ? UserContext.getTenantId() : DEFAULT_TENANT_ID;
        // 并发导入互斥：同一租户同一时间只允许一个导入操作 (AC-DEV-113~120)
        if (importLocks.putIfAbsent(tenantId, Boolean.TRUE) != null) {
            throw BizException.conflict("当前有其他导入任务正在进行中，请稍后重试");
        }
        try {
            return doImportDevices(file);
        } finally {
            importLocks.remove(tenantId);
        }
    }

    private DeviceImportResultDTO doImportDevices(MultipartFile file) {
        // 阶段1: 解析Excel（仅捕获解析异常）
        List<DeviceImportDTO> importList;
        try {
            importList = EasyExcel.read(file.getInputStream())
                    .head(DeviceImportDTO.class)
                    .sheet()
                    .headRowNumber(2)
                    .doReadSync();
        } catch (Exception e) {
            throw BizException.badRequest("Excel格式与模板不符，请先下载导入模板");
        }

        if (importList == null || importList.isEmpty()) {
            throw BizException.badRequest("Excel文件中没有数据行，请按模板填写后重新上传");
        }

        // 阶段2: 逐行校验与插入（数据库异常转为行级错误，不中断整个导入）
        int total = importList.size();
        int successCount = 0;
        int failCount = 0;
        List<DeviceImportDTO> failList = new ArrayList<>();

        Set<String> validDeviceTypes = resolveDeviceTypes();
        Set<String> seenCodes = new HashSet<>();
        Long importTenantId = UserContext.getTenantId() != null ? UserContext.getTenantId() : DEFAULT_TENANT_ID;

        for (int i = 0; i < importList.size(); i++) {
            DeviceImportDTO dto = importList.get(i);
            dto.setRowNum(i + 3); // 提示行+表头+数据从第3行开始
            dto.setSuccess(false);

            // 字段规范化：去除前后空格
            if (dto.getDeviceCode() != null) {
                dto.setDeviceCode(dto.getDeviceCode().trim());
            }
            if (dto.getDeviceName() != null) {
                dto.setDeviceName(dto.getDeviceName().trim());
            }
            if (dto.getDeviceType() != null) {
                dto.setDeviceType(dto.getDeviceType().trim());
            }
            if (dto.getSn() != null) {
                dto.setSn(dto.getSn().trim());
            }
            if (dto.getMacAddress() != null) {
                dto.setMacAddress(dto.getMacAddress().trim());
            }

            // 空白行跳过（所有关键字段都为空时视为空白行）
            if (StrUtil.isBlank(dto.getDeviceCode()) && StrUtil.isBlank(dto.getDeviceName())
                    && StrUtil.isBlank(dto.getDeviceType())) {
                continue;
            }

            // 校验必填字段
            if (StrUtil.isBlank(dto.getDeviceCode())) {
                dto.setErrorMessage("设备编码不能为空");
                failList.add(dto);
                failCount++;
                continue;
            }
            if (StrUtil.isBlank(dto.getDeviceName())) {
                dto.setErrorMessage("设备名称不能为空");
                failList.add(dto);
                failCount++;
                continue;
            }
            if (StrUtil.isBlank(dto.getDeviceType())) {
                dto.setErrorMessage("设备类型不能为空");
                failList.add(dto);
                failCount++;
                continue;
            }
            if (!validDeviceTypes.contains(dto.getDeviceType())) {
                dto.setErrorMessage("无效的设备类型: " + dto.getDeviceType());
                failList.add(dto);
                failCount++;
                continue;
            }

            // 文件内去重：同一文件中不允许出现重复设备编码
            if (seenCodes.contains(dto.getDeviceCode())) {
                dto.setErrorMessage("设备编码在文件中重复");
                failList.add(dto);
                failCount++;
                continue;
            }
            seenCodes.add(dto.getDeviceCode());

            // 检查编码唯一性（租户内）
            Long existing = count(new LambdaQueryWrapper<DeviceInfo>()
                    .eq(DeviceInfo::getDeviceCode, dto.getDeviceCode())
                    .eq(DeviceInfo::getTenantId, importTenantId));
            if (existing > 0) {
                dto.setErrorMessage("设备编码已存在（当前为新增模式，不支持更新已有设备）");
                failList.add(dto);
                failCount++;
                continue;
            }

            // 插入（行级try-catch，数据库异常转为行级失败）
            try {
                DeviceInfo device = new DeviceInfo();
                device.setDeviceCode(dto.getDeviceCode());
                device.setDeviceName(dto.getDeviceName());
                device.setDeviceType(dto.getDeviceType());
                device.setDeviceModel(dto.getDeviceModel());
                device.setManufacturer(dto.getManufacturer());
                device.setSn(dto.getSn());
                device.setMacAddress(dto.getMacAddress());
                device.setIpAddress(dto.getIpAddress());
                device.setLocationDesc(dto.getLocationDesc());
                device.setManagerName(dto.getManagerName());
                device.setManagerPhone(dto.getManagerPhone());
                device.setMaintenanceCycleDays(dto.getMaintenanceCycleDays());
                // 日期字段：String → LocalDate 安全解析
                if (StrUtil.isNotBlank(dto.getInstallDate())) {
                    device.setInstallDate(LocalDate.parse(dto.getInstallDate().trim()));
                }
                if (StrUtil.isNotBlank(dto.getWarrantyExpiresAt())) {
                    device.setWarrantyExpiresAt(LocalDate.parse(dto.getWarrantyExpiresAt().trim()));
                }
                if (StrUtil.isNotBlank(dto.getNextMaintenanceAt())) {
                    device.setNextMaintenanceAt(LocalDate.parse(dto.getNextMaintenanceAt().trim()));
                }
                device.setRemark(dto.getRemark());
                device.setOnlineStatus("offline");
                device.setStatus("active");
                device.setOrgId(UserContext.getOrgId());
                device.setTenantId(importTenantId);
                save(device);
                dto.setSuccess(true);
                successCount++;
            } catch (Exception e) {
                dto.setErrorMessage("数据保存失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
                failList.add(dto);
                failCount++;
            }
        }

        // 阶段3: 生成错误详情和错误文件
        String errorFileBase64 = null;
        List<DeviceImportResultDTO.ImportErrorDetail> errorDetails = new ArrayList<>();

        if (!failList.isEmpty()) {
            // 错误详情列表
            for (DeviceImportDTO fail : failList) {
                errorDetails.add(new DeviceImportResultDTO.ImportErrorDetail(
                        fail.getRowNum(),
                        fail.getDeviceCode(),
                        fail.getDeviceName(),
                        fail.getErrorMessage()
                ));
            }

            // 生成错误Excel文件（Base64编码，前端直接下载）
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("导入错误");

                String[] headers = {"设备编码", "设备名称", "设备类型", "设备型号", "厂商",
                        "序列号", "MAC地址", "IP地址", "安装位置", "负责人姓名", "负责人电话",
                        "维保周期(天)", "安装日期", "保修到期", "下次维保", "备注", "错误原因"};
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    headerRow.createCell(i).setCellValue(headers[i]);
                }

                int rowNum = 1;
                for (DeviceImportDTO fail : failList) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(fail.getDeviceCode() != null ? fail.getDeviceCode() : "");
                    row.createCell(1).setCellValue(fail.getDeviceName() != null ? fail.getDeviceName() : "");
                    row.createCell(2).setCellValue(fail.getDeviceType() != null ? fail.getDeviceType() : "");
                    row.createCell(3).setCellValue(fail.getDeviceModel() != null ? fail.getDeviceModel() : "");
                    row.createCell(4).setCellValue(fail.getManufacturer() != null ? fail.getManufacturer() : "");
                    row.createCell(5).setCellValue(fail.getSn() != null ? fail.getSn() : "");
                    row.createCell(6).setCellValue(fail.getMacAddress() != null ? fail.getMacAddress() : "");
                    row.createCell(7).setCellValue(fail.getIpAddress() != null ? fail.getIpAddress() : "");
                    row.createCell(8).setCellValue(fail.getLocationDesc() != null ? fail.getLocationDesc() : "");
                    row.createCell(9).setCellValue(fail.getManagerName() != null ? fail.getManagerName() : "");
                    row.createCell(10).setCellValue(fail.getManagerPhone() != null ? fail.getManagerPhone() : "");
                    row.createCell(11).setCellValue(fail.getMaintenanceCycleDays() != null ? String.valueOf(fail.getMaintenanceCycleDays()) : "");
                    row.createCell(12).setCellValue(fail.getInstallDate() != null ? fail.getInstallDate() : "");
                    row.createCell(13).setCellValue(fail.getWarrantyExpiresAt() != null ? fail.getWarrantyExpiresAt() : "");
                    row.createCell(14).setCellValue(fail.getNextMaintenanceAt() != null ? fail.getNextMaintenanceAt() : "");
                    row.createCell(15).setCellValue(fail.getRemark() != null ? fail.getRemark() : "");
                    row.createCell(16).setCellValue(fail.getErrorMessage());
                }

                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                workbook.write(baos);
                errorFileBase64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch (IOException ignored) {
                // 错误文件生成失败不影响主流程
            }
        }

        return new DeviceImportResultDTO(total, successCount, failCount, !failList.isEmpty(), errorFileBase64, errorDetails);
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("设备导入模板");

            // 提示行 — 设备类型可选值从字典表动态获取
            Row tipRow = sheet.createRow(0);
            Set<String> deviceTypes = resolveDeviceTypes();
            // 构建 code=中文名 格式便于用户对照
            Map<String, String> typeLabelMap = resolveDeviceTypeLabels();
            String typeHint = "请勿修改表头，带*为必填项。设备类型可选值: " +
                    deviceTypes.stream()
                            .map(code -> code + (typeLabelMap.containsKey(code) ? "(" + typeLabelMap.get(code) + ")" : ""))
                            .sorted()
                            .collect(Collectors.joining("/")) +
                    "。日期格式: yyyy-MM-dd";
            tipRow.createCell(0).setCellValue(typeHint);

            // 表头行（带*为必填）
            String[] headers = {"设备编码*", "设备名称*", "设备类型*", "设备型号", "厂商",
                    "序列号", "MAC地址", "IP地址", "安装位置", "负责人姓名", "负责人电话",
                    "维保周期(天)", "安装日期", "保修到期", "下次维保", "备注"};
            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // 示例数据
            Row exampleRow = sheet.createRow(2);
            exampleRow.createCell(0).setCellValue("DEV-001");
            exampleRow.createCell(1).setCellValue("厨房温度传感器");
            exampleRow.createCell(2).setCellValue("sensor");
            exampleRow.createCell(3).setCellValue("TH-200");
            exampleRow.createCell(4).setCellValue("XX厂商");
            exampleRow.createCell(5).setCellValue("SN-20260101");
            exampleRow.createCell(6).setCellValue("AA:BB:CC:DD:EE:FF");
            exampleRow.createCell(7).setCellValue("192.168.1.100");
            exampleRow.createCell(8).setCellValue("一楼厨房");
            exampleRow.createCell(9).setCellValue("张三");
            exampleRow.createCell(10).setCellValue("13800138000");
            exampleRow.createCell(11).setCellValue("90");
            exampleRow.createCell(12).setCellValue("2026-01-01");
            exampleRow.createCell(13).setCellValue("2027-01-01");
            exampleRow.createCell(14).setCellValue("2026-04-01");
            exampleRow.createCell(15).setCellValue("示例备注");

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("设备导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            workbook.write(response.getOutputStream());
        } catch (IOException e) {
            throw BizException.badRequest("下载导入模板失败: " + e.getMessage());
        }
    }

    private DeviceBatchOperationResultDTO executeBatchOperation(List<Long> ids, String targetStatus,
                                                               java.util.function.Consumer<DeviceInfo> executor) {
        List<Long> normalizedIds = normalizeBatchIds(ids);
        DeviceBatchOperationResultDTO result = new DeviceBatchOperationResultDTO();
        result.setTotalCount(normalizedIds.size());

        for (Long id : normalizedIds) {
            DeviceInfo device = getById(id);
            if (device == null) {
                result.getFailedItems().add(new DeviceBatchItemResultDTO(id, null, null, false, "设备不存在", "NOT_FOUND"));
                continue;
            }
            try {
                ensureOrgAllowed(device.getOrgId());
                if (targetStatus != null && Objects.equals(device.getStatus(), targetStatus)) {
                    result.getSkippedItems().add(new DeviceBatchItemResultDTO(
                            device.getId(),
                            device.getDeviceCode(),
                            device.getDeviceName(),
                            false,
                            BUSINESS_STATUS_ACTIVE.equals(targetStatus) ? "设备已启用，已跳过" : "设备已停用，已跳过",
                            "ALREADY_IN_TARGET_STATUS"
                    ));
                    continue;
                }
                transactionTemplate.executeWithoutResult(status -> executor.accept(device));
                result.getSuccessIds().add(device.getId());
            } catch (BizException ex) {
                result.getFailedItems().add(new DeviceBatchItemResultDTO(
                        device.getId(),
                        device.getDeviceCode(),
                        device.getDeviceName(),
                        false,
                        ex.getMessage(),
                        ex.getCode()
                ));
            } catch (Exception ex) {
                log.error("批量设备操作失败: id={}, error={}", device.getId(), ex.getMessage(), ex);
                result.getFailedItems().add(new DeviceBatchItemResultDTO(
                        device.getId(),
                        device.getDeviceCode(),
                        device.getDeviceName(),
                        false,
                        "系统异常，请稍后重试",
                        "SYSTEM_ERROR"
                ));
            }
        }

        result.setSuccessCount(result.getSuccessIds().size());
        result.setFailCount(result.getFailedItems().size());
        result.setSkippedCount(result.getSkippedItems().size());
        return result;
    }

    private List<Long> normalizeBatchIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BizException.badRequest("设备ID列表不能为空");
        }
        List<Long> normalized = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            throw BizException.badRequest("设备ID列表不能为空");
        }
        return normalized;
    }

    private void deleteDeviceInternal(DeviceInfo device) {
        Long id = device.getId();

        // 检查设备是否被活跃烹饪任务绑定
        Long boundCookTasks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cook_task WHERE device_id = ? AND status IN ('pending','in_progress') AND deleted = 0",
                Long.class, id);
        if (boundCookTasks != null && boundCookTasks > 0) {
            throw BizException.badRequest("该设备已被 " + boundCookTasks + " 个待烹饪/烹饪中的任务绑定，无法删除");
        }

        Long activeRecordings = deviceMonitorRecordMapper.selectCount(
                new LambdaQueryWrapper<DeviceMonitorRecord>()
                        .eq(DeviceMonitorRecord::getDeviceId, id)
                        .eq(DeviceMonitorRecord::getStatus, "recording"));
        if (activeRecordings != null && activeRecordings > 0) {
            throw BizException.badRequest("该设备存在进行中的录像任务，无法删除");
        }

        Long activeAlerts = deviceAlertMapper.selectCount(
                new LambdaQueryWrapper<DeviceAlert>()
                        .eq(DeviceAlert::getDeviceId, id)
                        .ne(DeviceAlert::getStatus, "closed"));
        if (activeAlerts != null && activeAlerts > 0) {
            throw BizException.badRequest("该设备存在未关闭的告警，无法删除");
        }

        if ("camera".equals(device.getDeviceType())) {
            try {
                streamTranscodeService.stopTranscode(id);
            } catch (Exception e) {
                log.warn("删除设备时停止转码失败: deviceId={}, error={}", id, e.getMessage());
            }
            try {
                recordingProcessService.stopRecording(id);
            } catch (Exception e) {
                log.warn("删除设备时停止录像失败: deviceId={}, error={}", id, e.getMessage());
            }
        }

        recordStatusHistory(device, STATUS_TYPE_BUSINESS, device.getStatus(), "deleted", SOURCE_TYPE_MANUAL, "删除设备");

        device.setDeviceCode(device.getDeviceCode() + "_del_" + id);
        updateById(device);
        removeById(id);
        removeDeviceFromAlertRules(id);
        log.info("删除设备成功: id={}, name={}", id, device.getDeviceName());
    }

    private void toggleDeviceStatusInternal(DeviceInfo device) {
        Long id = device.getId();
        String oldBusinessStatus = device.getStatus();

        if (BUSINESS_STATUS_ACTIVE.equals(device.getStatus())) {
            // 检查设备是否被活跃烹饪任务绑定
            Long boundCookTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM cook_task WHERE device_id = ? AND status IN ('pending','in_progress') AND deleted = 0",
                    Long.class, id);
            if (boundCookTasks != null && boundCookTasks > 0) {
                throw BizException.badRequest("该设备已被 " + boundCookTasks + " 个待烹饪/烹饪中的任务绑定，无法禁用");
            }

            Long activeRecordings = deviceMonitorRecordMapper.selectCount(
                    new LambdaQueryWrapper<DeviceMonitorRecord>()
                            .eq(DeviceMonitorRecord::getDeviceId, id)
                            .eq(DeviceMonitorRecord::getStatus, "recording"));
            if (activeRecordings != null && activeRecordings > 0) {
                throw BizException.badRequest("该设备存在进行中的录像任务，无法禁用");
            }

            Long activeAlerts = deviceAlertMapper.selectCount(
                    new LambdaQueryWrapper<DeviceAlert>()
                            .eq(DeviceAlert::getDeviceId, id)
                            .ne(DeviceAlert::getStatus, "closed"));
            if (activeAlerts != null && activeAlerts > 0) {
                throw BizException.badRequest("该设备存在未关闭的告警，无法禁用");
            }

            if ("camera".equals(device.getDeviceType())) {
                try {
                    streamTranscodeService.stopTranscode(id);
                } catch (Exception e) {
                    log.warn("禁用设备时停止转码失败: deviceId={}, error={}", id, e.getMessage());
                }
                try {
                    recordingProcessService.stopRecording(id);
                } catch (Exception e) {
                    log.warn("禁用设备时停止录像失败: deviceId={}, error={}", id, e.getMessage());
                }
            }

            device.setStatus(BUSINESS_STATUS_INACTIVE);
            device.setOnlineStatus(ONLINE_STATUS_OFFLINE);
            updateById(device);

            applyBusinessStatusRules(device, BUSINESS_STATUS_INACTIVE, "禁用设备");
            recordStatusHistory(device, STATUS_TYPE_BUSINESS, oldBusinessStatus, BUSINESS_STATUS_INACTIVE, SOURCE_TYPE_MANUAL, "禁用设备");
            log.info("设备已禁用: id={}, name={}", id, device.getDeviceName());
        } else {
            device.setStatus(BUSINESS_STATUS_ACTIVE);
            device.setOnlineStatus(ONLINE_STATUS_OFFLINE);
            updateById(device);

            if ("camera".equals(device.getDeviceType())) {
                try {
                    streamTranscodeService.restartDeviceTranscode(id);
                } catch (Exception e) {
                    log.warn("启用设备时重启转码失败: deviceId={}, error={}", id, e.getMessage());
                }
            }

            applyBusinessStatusRules(device, BUSINESS_STATUS_ACTIVE, "启用设备");
            recordStatusHistory(device, STATUS_TYPE_BUSINESS, oldBusinessStatus, BUSINESS_STATUS_ACTIVE, SOURCE_TYPE_MANUAL, "启用设备");
            log.info("设备已启用: id={}, name={}", id, device.getDeviceName());
        }

        eventPublisher.publishEvent(new DeviceOnlineStatusEvent(
                this, device.getId(), device.getDeviceName(), device.getDeviceType(),
                oldBusinessStatus, device.getStatus()));
    }

    // ========== 转换方法 ==========

    private LambdaQueryWrapper<DeviceInfo> buildListWrapper(DeviceQueryDTO query) {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(query.getDeviceType()), DeviceInfo::getDeviceType, query.getDeviceType());
        wrapper.eq(StrUtil.isNotBlank(query.getOnlineStatus()), DeviceInfo::getOnlineStatus, query.getOnlineStatus());
        wrapper.eq(StrUtil.isNotBlank(query.getStatus()), DeviceInfo::getStatus, query.getStatus());
        if (StrUtil.isNotBlank(query.getDeviceName())) {
            wrapper.and(w -> w
                    .like(DeviceInfo::getDeviceName, query.getDeviceName())
                    .or()
                    .like(DeviceInfo::getDeviceCode, query.getDeviceName())
            );
        }
        wrapper.eq(query.getOrgId() != null, DeviceInfo::getOrgId, query.getOrgId());
        wrapper.in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), DeviceInfo::getOrgId, query.getOrgIds());
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(DeviceInfo::getId);
        }
        wrapper.orderByDesc(DeviceInfo::getUpdatedAt);
        return wrapper;
    }

    private DeviceVO convertToVO(DeviceInfo device) {
        DeviceVO vo = new DeviceVO();
        vo.setId(device.getId());
        vo.setDeviceCode(device.getDeviceCode());
        vo.setDeviceName(device.getDeviceName());
        vo.setDeviceType(device.getDeviceType());
        vo.setDeviceTypeName(DEVICE_TYPE_MAP.getOrDefault(device.getDeviceType(), device.getDeviceType()));
        vo.setDeviceModel(device.getDeviceModel());
        vo.setManufacturer(device.getManufacturer());
        vo.setLocationDesc(device.getLocationDesc());
        vo.setOnlineStatus(device.getOnlineStatus());
        vo.setOnlineStatusName(ONLINE_STATUS_MAP.getOrDefault(device.getOnlineStatus(), device.getOnlineStatus()));
        vo.setStatus(device.getStatus());
        vo.setStatusName(STATUS_MAP.getOrDefault(device.getStatus(), device.getStatus()));
        vo.setLastHeartbeatAt(device.getLastHeartbeatAt());
        vo.setUpdatedAt(device.getUpdatedAt());
        vo.setManagerName(device.getManagerName());
        vo.setManagerPhone(device.getManagerPhone());
        vo.setOrgId(device.getOrgId());
        vo.setOrgName(resolveOrgName(device.getOrgId()));
        vo.setTypeSpecificSummary(buildTypeSpecificSummary(device));
        return vo;
    }

    private DeviceDetailVO convertToDetailVO(DeviceInfo device) {
        DeviceDetailVO vo = new DeviceDetailVO();
        BeanUtils.copyProperties(device, vo);
        vo.setDeviceTypeName(DEVICE_TYPE_MAP.getOrDefault(device.getDeviceType(), device.getDeviceType()));
        vo.setOnlineStatusName(ONLINE_STATUS_MAP.getOrDefault(device.getOnlineStatus(), device.getOnlineStatus()));
        vo.setStatusName(STATUS_MAP.getOrDefault(device.getStatus(), device.getStatus()));
        vo.setConfigParams(parseJsonToMap(device.getConfigParams()));
        vo.setOrgName(resolveOrgName(device.getOrgId()));

        Map<String, Object> pos3d = new HashMap<>();
        if (device.getPositionX() != null) pos3d.put("x", device.getPositionX());
        if (device.getPositionY() != null) pos3d.put("y", device.getPositionY());
        if (device.getPositionZ() != null) pos3d.put("z", device.getPositionZ());
        if (!pos3d.isEmpty()) vo.setPosition3d(pos3d);
        vo.setModel3dScale(device.getModel3dScale());

        if (vo.getManagerName() == null || vo.getManagerPhone() == null) {
            Map<String, Object> config = parseJsonToMap(device.getConfigParams());
            if (config != null) {
                if (vo.getManagerName() == null) vo.setManagerName((String) config.get("managerName"));
                if (vo.getManagerPhone() == null) vo.setManagerPhone((String) config.get("managerPhone"));
                if (vo.getManagerId() == null) vo.setManagerId(toLong(config.get("managerId")));
            }
        }

        return vo;
    }

    private String resolveOrgName(Long orgId) {
        if (orgId == null) return null;
        try {
            List<String> names = jdbcTemplate.queryForList(
                    "SELECT org_name FROM sys_organization WHERE id = ? AND deleted = 0 LIMIT 1",
                    String.class, orgId);
            return names.isEmpty() ? null : names.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private DeviceStatusLogVO convertAuditLogToStatusLog(SysAuditLog auditLog) {
        Map<String, Object> before = parseJsonToMap(auditLog.getBeforeData());
        Map<String, Object> after = parseJsonToMap(auditLog.getAfterData());
        String statusType = firstNonBlank(asString(after == null ? null : after.get("statusType")),
                asString(before == null ? null : before.get("statusType")));
        if (StrUtil.isBlank(statusType)) {
            return null;
        }

        DeviceStatusLogVO vo = new DeviceStatusLogVO();
        vo.setStatusType(statusType);
        vo.setStatusTypeName(resolveStatusTypeName(statusType));
        vo.setFromStatus(asString(before == null ? null : before.get("status")));
        vo.setToStatus(asString(after == null ? null : after.get("status")));
        vo.setFromStatusName(resolveStatusName(statusType, vo.getFromStatus(),
                asString(before == null ? null : before.get("statusName"))));
        vo.setToStatusName(resolveStatusName(statusType, vo.getToStatus(),
                asString(after == null ? null : after.get("statusName"))));
        vo.setSourceType(firstNonBlank(asString(after == null ? null : after.get("sourceType")),
                asString(before == null ? null : before.get("sourceType"))));
        vo.setSourceTypeName(STATUS_SOURCE_MAP.getOrDefault(vo.getSourceType(), vo.getSourceType()));
        vo.setReason(firstNonBlank(asString(after == null ? null : after.get("reason")),
                asString(before == null ? null : before.get("reason"))));
        vo.setOperatorId(auditLog.getUserId());
        vo.setOperatorName(StrUtil.blankToDefault(auditLog.getRealName(), auditLog.getUserName()));
        vo.setResult(auditLog.getResult());
        vo.setCreatedAt(auditLog.getCreatedAt());
        return vo;
    }

    /** 生成设备特有摘要信息 */
    private String buildTypeSpecificSummary(DeviceInfo device) {
        Map<String, Object> config = parseJsonToMap(device.getConfigParams());
        if (config == null) return "";

        return switch (device.getDeviceType()) {
            case "camera" -> "区域: " + config.getOrDefault("area", "-") + " | AI: " + config.getOrDefault("aiAlgorithm", "-");
            case "sensor" -> "位置: " + config.getOrDefault("location", "-") + " | 频率: " + config.getOrDefault("collectFrequency", "-");
            case "scale" -> "品类: " + config.getOrDefault("detectCategory", "-") + " | 校准: " + config.getOrDefault("calibrationTime", "-");
            case "gas_detector" -> "气体: " + config.getOrDefault("gasType", "-") + " | 阈值: " + config.getOrDefault("alertThreshold", "-");
            case "sample_terminal" -> "烹饪区: " + config.getOrDefault("cookingArea", "-") + " | 精度: " + config.getOrDefault("accuracy", "-") + "%";
            case "health_terminal" -> "位置: " + config.getOrDefault("location", "-") + " | 体温阈值: " + config.getOrDefault("tempThreshold", "-");
            default -> "";
        };
    }

    // ========== 规则与联动 ==========

    private void applyBusinessStatusRules(DeviceInfo device, String targetStatus, String reason) {
        if (BUSINESS_STATUS_ACTIVE.equals(targetStatus)) {
            triggerDeviceAvailabilityAlertIfNeeded(device, SOURCE_TYPE_MANUAL, reason);
            return;
        }
        closeDeviceStateAlerts(device, "设备业务状态变更为" + STATUS_MAP.getOrDefault(targetStatus, targetStatus) + "，告警自动抑制");
    }

    private void applyOnlineStatusRules(DeviceInfo device, String targetStatus, String sourceType, String reason) {
        if (!BUSINESS_STATUS_ACTIVE.equals(device.getStatus())) {
            return;
        }

        if (ONLINE_STATUS_ONLINE.equals(targetStatus)) {
            closeSpecificAlertTypes(device.getId(), Set.of(ALERT_TYPE_DEVICE_FAULT), "设备恢复在线，关闭故障告警");
            return;
        }

        if (ONLINE_STATUS_OFFLINE.equals(targetStatus)) {
            closeSpecificAlertTypes(device.getId(), Set.of(ALERT_TYPE_DEVICE_FAULT), "设备状态调整为离线，关闭故障告警");
            return;
        }

        closeSpecificAlertTypes(device.getId(), Set.of(ALERT_TYPE_DEVICE_OFFLINE), "设备已确认故障，关闭离线告警");
        ensureDeviceAlert(device, ALERT_TYPE_DEVICE_FAULT, "error",
                "设备【" + device.getDeviceName() + "】故障，已推送维保通知", sourceType, reason);
    }

    private void triggerDeviceAvailabilityAlertIfNeeded(DeviceInfo device, String sourceType, String reason) {
        if (ONLINE_STATUS_OFFLINE.equals(device.getOnlineStatus())) {
            ensureDeviceAlert(device, ALERT_TYPE_DEVICE_OFFLINE, "warning",
                    "设备【" + device.getDeviceName() + "】离线，已生成运维待办", sourceType, reason);
            return;
        }
        if (ONLINE_STATUS_FAULT.equals(device.getOnlineStatus())) {
            ensureDeviceAlert(device, ALERT_TYPE_DEVICE_FAULT, "error",
                    "设备【" + device.getDeviceName() + "】故障，已推送维保通知", sourceType, reason);
            return;
        }
        closeDeviceStateAlerts(device, "设备业务状态启用且设备在线，无需保留设备状态告警");
    }

    private void ensureDeviceAlert(DeviceInfo device, String alertType, String alertLevel,
                                   String content, String sourceType, String reason) {
        // 离线告警：无匹配规则时不告警
        DeviceAlertRule matchedRule = null;
        if (ALERT_TYPE_DEVICE_OFFLINE.equals(alertType)) {
            matchedRule = findMatchingOfflineRule(device.getDeviceType(), device.getId());
            if (matchedRule == null) {
                log.info("无匹配的离线告警规则，跳过: deviceId={}, deviceType={}", device.getId(), device.getDeviceType());
                return;
            }
        }

        // 去重：只检查上次在线之后创建的未关闭告警，支持设备多次故障/恢复循环
        LambdaQueryWrapper<DeviceAlert> dedupQuery = new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getDeviceId, device.getId())
                .eq(DeviceAlert::getAlertType, alertType)
                .eq(DeviceAlert::getDeleted, 0)
                .in(DeviceAlert::getStatus, OPEN_ALERT_STATUSES);
        if (device.getLastHeartbeatAt() != null) {
            dedupQuery.gt(DeviceAlert::getTriggeredAt, device.getLastHeartbeatAt());
        }
        DeviceAlert existing = deviceAlertMapper.selectOne(dedupQuery.last("LIMIT 1"));
        if (existing != null) {
            return;
        }

        DeviceAlert alert = new DeviceAlert();
        alert.setAlertNo(generateAlertNo());
        alert.setAlertType(alertType);
        alert.setAlertLevel(alertLevel);
        if (matchedRule != null) {
            alert.setAlertRuleId(matchedRule.getId());
        }
        alert.setDeviceId(device.getId());
        alert.setDeviceName(device.getDeviceName());
        alert.setAlertContent(content);
        alert.setAlertDetail(toJson(buildAlertDetail(device, alertType, sourceType, reason)));
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setStatus("pending");
        alert.setOrgId(device.getOrgId());
        alert.setTenantId(device.getTenantId() != null ? device.getTenantId() : resolveTenantId());
        deviceAlertMapper.insert(alert);

        // 尝试自动派单
        deviceAlertService.tryAutoDispatch(alert.getId());

        auditLogService.log(
                AuditModule.DEVICE_ALERT,
                AuditOperationType.CREATE,
                alert.getId(),
                alert.getAlertNo(),
                "设备状态联动触发告警",
                null,
                toJson(buildAlertDetail(device, alertType, sourceType, reason))
        );
    }

    /**
     * 查找匹配的离线告警规则（同时检查 deviceIds 范围）
     */
    private DeviceAlertRule findMatchingOfflineRule(String deviceType, Long deviceId) {
        LambdaQueryWrapper<DeviceAlertRule> query = new LambdaQueryWrapper<>();
        query.eq(DeviceAlertRule::getRuleType, "offline")
                .eq(DeviceAlertRule::getIsEnabled, 1);
        List<DeviceAlertRule> rules = alertRuleMapper.selectList(query);
        if (rules.isEmpty()) return null;
        for (DeviceAlertRule rule : rules) {
            if (deviceType != null && deviceType.equals(rule.getDeviceType()) && isDeviceInScope(rule, deviceId)) return rule;
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
        return java.util.Arrays.stream(rule.getDeviceIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equals(String.valueOf(deviceId)));
    }

    /**
     * 设备删除后，从所有告警规则的 deviceIds 中移除该设备ID
     */
    private void removeDeviceFromAlertRules(Long deviceId) {
        try {
            String deviceIdStr = String.valueOf(deviceId);
            List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                    "SELECT id, device_ids FROM device_alert_rule WHERE device_ids LIKE ? AND deleted = 0",
                    "%" + deviceIdStr + "%");
            for (Map<String, Object> row : rules) {
                Long ruleId = ((Number) row.get("id")).longValue();
                String deviceIds = (String) row.get("device_ids");
                if (deviceIds == null || deviceIds.isBlank()) continue;
                String updated = java.util.Arrays.stream(deviceIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty() && !s.equals(deviceIdStr))
                        .collect(java.util.stream.Collectors.joining(","));
                jdbcTemplate.update("UPDATE device_alert_rule SET device_ids = ? WHERE id = ?",
                        updated.isEmpty() ? null : updated, ruleId);
                log.info("从告警规则中移除设备: ruleId={}, deviceId={}, 更新后deviceIds={}", ruleId, deviceId, updated);
            }
        } catch (Exception e) {
            log.warn("从告警规则中移除设备ID失败（不影响设备删除）: deviceId={}, error={}", deviceId, e.getMessage());
        }
    }

    private void closeDeviceStateAlerts(DeviceInfo device, String reason) {
        closeSpecificAlertTypes(device.getId(), DEVICE_STATE_ALERT_TYPES, reason);
    }

    /**
     * 关闭未指派的设备状态告警（仅关闭 pending 状态，已指派/处理中的不关闭）
     */
    private void closeSpecificAlertTypes(Long deviceId, Set<String> alertTypes, String reason) {
        if (deviceId == null || alertTypes == null || alertTypes.isEmpty()) {
            return;
        }
        List<DeviceAlert> alerts = deviceAlertMapper.selectList(new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getDeviceId, deviceId)
                .eq(DeviceAlert::getDeleted, 0)
                .in(DeviceAlert::getAlertType, alertTypes)
                .eq(DeviceAlert::getStatus, "pending"));
        if (alerts.isEmpty()) {
            return;
        }

        Long operatorId = resolveCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        for (DeviceAlert alert : alerts) {
            alert.setStatus("closed");
            alert.setHandledBy(operatorId);
            alert.setHandledAt(now);
            alert.setHandleResult(reason);
            deviceAlertMapper.updateById(alert);
            log.info("自动关闭未指派告警: alertId={}, alertNo={}, 原因={}", alert.getId(), alert.getAlertNo(), reason);
        }
    }

    private void recordStatusHistory(DeviceInfo device, String statusType, String beforeStatus,
                                     String afterStatus, String sourceType, String reason) {
        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("statusType", statusType);
        beforeData.put("statusTypeName", resolveStatusTypeName(statusType));
        beforeData.put("status", beforeStatus);
        beforeData.put("statusName", resolveStatusName(statusType, beforeStatus, null));
        beforeData.put("deviceId", device.getId());
        beforeData.put("deviceCode", device.getDeviceCode());
        beforeData.put("deviceName", device.getDeviceName());

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("statusType", statusType);
        afterData.put("statusTypeName", resolveStatusTypeName(statusType));
        afterData.put("status", afterStatus);
        afterData.put("statusName", resolveStatusName(statusType, afterStatus, null));
        afterData.put("deviceId", device.getId());
        afterData.put("deviceCode", device.getDeviceCode());
        afterData.put("deviceName", device.getDeviceName());
        afterData.put("sourceType", sourceType);
        afterData.put("sourceTypeName", STATUS_SOURCE_MAP.getOrDefault(sourceType, sourceType));
        afterData.put("reason", reason);

        auditLogService.log(
                AuditModule.DEVICE_INFO,
                AuditOperationType.UPDATE,
                device.getId(),
                device.getDeviceCode(),
                STATUS_TYPE_BUSINESS.equals(statusType) ? "设备业务状态变更" : "设备在线状态变更",
                toJson(beforeData),
                toJson(afterData)
        );
    }

    private void validateBusinessStatusTransition(String currentStatus, String targetStatus) {
        boolean valid = switch (currentStatus) {
            case BUSINESS_STATUS_ACTIVE -> BUSINESS_STATUS_ACTIVE.equals(targetStatus)
                    || BUSINESS_STATUS_INACTIVE.equals(targetStatus)
                    || BUSINESS_STATUS_MAINTENANCE.equals(targetStatus);
            case BUSINESS_STATUS_INACTIVE -> BUSINESS_STATUS_INACTIVE.equals(targetStatus)
                    || BUSINESS_STATUS_ACTIVE.equals(targetStatus);
            case BUSINESS_STATUS_MAINTENANCE -> BUSINESS_STATUS_MAINTENANCE.equals(targetStatus)
                    || BUSINESS_STATUS_ACTIVE.equals(targetStatus)
                    || BUSINESS_STATUS_INACTIVE.equals(targetStatus);
            default -> false;
        };
        if (!valid) {
            throw BizException.badRequest("当前设备状态不支持流转到目标状态");
        }
    }

    private void validateManualOnlineStatusTransition(String currentStatus, String targetStatus) {
        boolean valid = switch (currentStatus) {
            case ONLINE_STATUS_ONLINE -> ONLINE_STATUS_FAULT.equals(targetStatus);
            case ONLINE_STATUS_OFFLINE -> ONLINE_STATUS_FAULT.equals(targetStatus);
            case ONLINE_STATUS_FAULT -> ONLINE_STATUS_ONLINE.equals(targetStatus)
                    || ONLINE_STATUS_OFFLINE.equals(targetStatus);
            default -> false;
        };
        if (!valid) {
            throw BizException.badRequest("人工修正仅支持故障确认或故障恢复，禁止手工改写在线/离线状态");
        }
    }

    private void validateSystemOnlineStatusTransition(String targetStatus) {
        if (!ONLINE_STATUS_ONLINE.equals(targetStatus) && !ONLINE_STATUS_OFFLINE.equals(targetStatus)) {
            throw BizException.badRequest("系统心跳仅允许更新在线或离线状态");
        }
    }

    private void ensureBusinessStatusManageAllowed() {
        if (dataScopeService.isAdminUser()) {
            return;
        }
        if (!hasPermission(DEVICE_EDIT_PERMISSION) || !matchesRoleRequirement(BUSINESS_STATUS_ROLE_KEYWORDS)) {
            throw BizException.forbidden("仅设备管理员、运维或组织管理员可修改设备状态");
        }
    }

    private void ensureOnlineStatusManageAllowed() {
        if (dataScopeService.isAdminUser()) {
            return;
        }
        if (!hasPermission(DEVICE_EDIT_PERMISSION) || !matchesRoleRequirement(ONLINE_STATUS_ROLE_KEYWORDS)) {
            throw BizException.forbidden("仅运维或管理员可人工修正在线状态");
        }
    }

    private boolean matchesRoleRequirement(List<String> roleNameKeywords) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
        }
        List<Map<String, Object>> roleRows = jdbcTemplate.queryForList(
                "SELECT UPPER(TRIM(COALESCE(r.role_code, ''))) AS roleCode, COALESCE(r.role_name, '') AS roleName " +
                        "FROM auth_user_role ur " +
                        "JOIN auth_role r ON r.id = ur.role_id " +
                        "WHERE ur.user_id = ? AND r.deleted = 0 AND r.status = 'active'",
                userId
        );
        for (Map<String, Object> row : roleRows) {
            String roleCode = asString(row.get("roleCode"));
            String roleName = asString(row.get("roleName"));
            if (ROLE_CODE_ALLOWLIST.contains(roleCode)) {
                return true;
            }
            if (StrUtil.isNotBlank(roleName) && roleNameKeywords.stream().anyMatch(roleName::contains)) {
                return true;
            }
        }
        return false;
    }

    private LambdaQueryWrapper<DeviceInfo> buildScopedWrapper(DeviceQueryDTO query) {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getOrgId() != null, DeviceInfo::getOrgId, query.getOrgId());
        wrapper.in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), DeviceInfo::getOrgId, query.getOrgIds());
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(DeviceInfo::getId);
        }
        return wrapper;
    }

    private DeviceInfo getDeviceOrThrow(Long id) {
        DeviceInfo device = getById(id);
        if (device == null) {
            throw BizException.notFound("设备不存在");
        }
        ensureOrgAllowed(device.getOrgId());
        return device;
    }

    private void ensureOrgAllowed(Long orgId) {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(orgId)) {
            throw BizException.forbidden("无权访问该组织数据");
        }
    }

    /**
     * 从字典表获取有效的设备类型值集合（动态，而非硬编码 DEVICE_TYPE_MAP）
     */
    private Set<String> resolveDeviceTypes() {
        List<String> codes = jdbcTemplate.queryForList(
                "SELECT dict_code FROM sys_dict WHERE dict_type = 'device_type' AND status = 'active' AND deleted = 0",
                String.class);
        // 合并硬编码值作为兜底（确保即使字典表为空也能导入）
        Set<String> result = new HashSet<>(codes);
        result.addAll(DEVICE_TYPE_MAP.keySet());
        return result;
    }

    /**
     * 从字典表获取设备类型 code→中文名 映射（动态，而非硬编码）
     */
    private Map<String, String> resolveDeviceTypeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT dict_code, dict_name FROM sys_dict WHERE dict_type = 'device_type' AND status = 'active' AND deleted = 0 ORDER BY sort_order",
                rs -> { labels.put(rs.getString("dict_code"), rs.getString("dict_name")); });
        // 硬编码兜底
        labels.putAll(DEVICE_TYPE_MAP);
        return labels;
    }

    /**
     * 批量解析组织名称（orgId → orgName）
     */
    private Map<Long, String> resolveOrgNames(Set<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> result = new HashMap<>();
        String placeholders = orgIds.stream().map(id -> "?").collect(Collectors.joining(","));
        jdbcTemplate.query(
                "SELECT id, org_name FROM sys_organization WHERE id IN (" + placeholders + ") AND deleted = 0",
                rs -> { result.put(rs.getLong("id"), rs.getString("org_name")); },
                orgIds.toArray());
        return result;
    }

    private String normalizeBusinessStatus(String status, String defaultStatus) {
        String normalized = StrUtil.trimToNull(status);
        if (normalized == null) {
            return defaultStatus;
        }
        if (!BUSINESS_STATUS_VALUES.contains(normalized)) {
            throw BizException.badRequest("无效的设备状态");
        }
        return normalized;
    }

    private String normalizeOnlineStatus(String onlineStatus) {
        String normalized = StrUtil.trimToNull(onlineStatus);
        if (normalized == null || !ONLINE_STATUS_VALUES.contains(normalized)) {
            throw BizException.badRequest("无效的在线状态");
        }
        return normalized;
    }

    private String normalizeSourceType(String sourceType) {
        String normalized = StrUtil.blankToDefault(StrUtil.trim(sourceType), SOURCE_TYPE_SYSTEM);
        if (!SOURCE_TYPE_SYSTEM.equals(normalized) && !SOURCE_TYPE_MANUAL.equals(normalized)) {
            throw BizException.badRequest("无效的状态更新来源");
        }
        return normalized;
    }

    private String normalizeRequiredReason(String reason, String message) {
        String normalized = StrUtil.trimToNull(reason);
        if (normalized == null) {
            throw BizException.badRequest(message);
        }
        return normalized;
    }

    private String buildSystemOnlineStatusReason(String targetStatus) {
        if (ONLINE_STATUS_ONLINE.equals(targetStatus)) {
            return "系统心跳检测恢复在线";
        }
        return "系统心跳超时判定设备离线";
    }

    private Map<String, Object> buildAlertDetail(DeviceInfo device, String alertType, String sourceType, String reason) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("deviceId", device.getId());
        detail.put("deviceCode", device.getDeviceCode());
        detail.put("deviceName", device.getDeviceName());
        detail.put("deviceType", device.getDeviceType());
        detail.put("deviceTypeName", DEVICE_TYPE_MAP.getOrDefault(device.getDeviceType(), device.getDeviceType()));
        detail.put("onlineStatus", device.getOnlineStatus());
        detail.put("onlineStatusName", ONLINE_STATUS_MAP.getOrDefault(device.getOnlineStatus(), device.getOnlineStatus()));
        detail.put("businessStatus", device.getStatus());
        detail.put("businessStatusName", STATUS_MAP.getOrDefault(device.getStatus(), device.getStatus()));
        detail.put("alertType", alertType);
        detail.put("sourceType", sourceType);
        detail.put("sourceTypeName", STATUS_SOURCE_MAP.getOrDefault(sourceType, sourceType));
        detail.put("reason", reason);
        detail.put("generatedAt", LocalDateTime.now());
        detail.put("todoGenerated", ALERT_TYPE_DEVICE_OFFLINE.equals(alertType));
        detail.put("maintenanceNotificationPushed", ALERT_TYPE_DEVICE_FAULT.equals(alertType));
        return detail;
    }

    private boolean hasPermission(String permissionCode) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " +
                        "FROM auth_user_role ur " +
                        "JOIN auth_role r ON r.id = ur.role_id " +
                        "JOIN auth_role_permission rp ON rp.role_id = r.id " +
                        "JOIN auth_permission p ON p.id = rp.permission_id " +
                        "WHERE ur.user_id = ? " +
                        "  AND r.deleted = 0 " +
                        "  AND r.status = 'active' " +
                        "  AND p.status = 'active' " +
                        "  AND p.permission_code = ?",
                Long.class,
                userId,
                permissionCode
        );
        return count != null && count > 0L;
    }

    private String resolveStatusTypeName(String statusType) {
        return STATUS_TYPE_BUSINESS.equals(statusType) ? "业务状态" : "在线状态";
    }

    private String resolveStatusName(String statusType, String status, String fallback) {
        if (StrUtil.isNotBlank(fallback)) {
            return fallback;
        }
        if (StrUtil.isBlank(status)) {
            return null;
        }
        return STATUS_TYPE_BUSINESS.equals(statusType)
                ? STATUS_MAP.getOrDefault(status, status)
                : ONLINE_STATUS_MAP.getOrDefault(status, status);
    }

    private Long resolveTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : DEFAULT_TENANT_ID;
    }

    private Long resolveCurrentUserId() {
        return UserContext.getUserId() != null ? UserContext.getUserId() : DEFAULT_USER_ID;
    }

    private String generateAlertNo() {
        return "DA-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("序列化JSON失败: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseJsonToMap(String json) {
        if (StrUtil.isBlank(json)) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析JSON失败: {}", json, e);
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
