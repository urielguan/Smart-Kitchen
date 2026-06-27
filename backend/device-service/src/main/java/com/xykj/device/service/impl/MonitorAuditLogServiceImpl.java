package com.xykj.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.context.RequestContext;
import com.xykj.common.context.UserContext;
import com.xykj.common.entity.SysAuditLog;
import com.xykj.common.mapper.SysAuditLogMapper;
import com.xykj.common.service.AuditLogService;
import com.xykj.device.dto.MonitorAuditLogDTO;
import com.xykj.device.dto.MonitorAuditLogQueryDTO;
import com.xykj.device.service.MonitorAuditLogService;
import com.xykj.device.vo.MonitorAuditLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 监控审计日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorAuditLogServiceImpl implements MonitorAuditLogService {

    private final SysAuditLogMapper sysAuditLogMapper;
    private final AuditLogService auditLogService;

    private static final Set<String> MONITOR_MODULE_CODES = new HashSet<>(Arrays.asList(
            "device_monitor", "device_recording", "device_violation",
            "device_clip", "device_screenshot", "device_evidence"
    ));

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void logFrontendAction(MonitorAuditLogDTO dto) {
        String desc = "前端操作: " + dto.getAction();
        if (dto.getDeviceName() != null) {
            desc += ", 设备: " + dto.getDeviceName();
        }
        if (dto.getExtra() != null) {
            desc += ", " + dto.getExtra();
        }

        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setUserId(UserContext.getUserId() != null ? UserContext.getUserId() : 0L);
        auditLog.setUserName(UserContext.getUsername() != null ? UserContext.getUsername() : "system");
        auditLog.setRealName(UserContext.getRealName());
        auditLog.setOrgId(UserContext.getOrgId());
        auditLog.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L);
        auditLog.setOperationType(dto.getAction());
        auditLog.setModuleCode("device_monitor");
        auditLog.setModuleName("实时监控");
        auditLog.setTargetId(dto.getDeviceId());
        auditLog.setOperationDesc(desc);
        auditLog.setIpAddress(RequestContext.getIpAddress());
        auditLog.setUserAgent(RequestContext.getUserAgent());
        auditLog.setSourceTerminal(RequestContext.getSourceTerminal());
        auditLog.setDeviceId(dto.getDeviceId());
        auditLog.setRecordingId(dto.getRecordingId());
        auditLog.setResult("success");
        auditLog.setCreatedAt(LocalDateTime.now());

        try {
            sysAuditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("前端审计日志写入失败: {}", e.getMessage());
        }
    }

    @Override
    public List<MonitorAuditLogVO> getAuditLogList(MonitorAuditLogQueryDTO query) {
        LambdaQueryWrapper<SysAuditLog> wrapper = buildQueryWrapper(query);
        wrapper.orderByDesc(SysAuditLog::getCreatedAt);

        Page<SysAuditLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<SysAuditLog> result = sysAuditLogMapper.selectPage(page, wrapper);

        return result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Long getAuditLogCount(MonitorAuditLogQueryDTO query) {
        LambdaQueryWrapper<SysAuditLog> wrapper = buildQueryWrapper(query);
        return sysAuditLogMapper.selectCount(wrapper);
    }

    private LambdaQueryWrapper<SysAuditLog> buildQueryWrapper(MonitorAuditLogQueryDTO query) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();

        if (query.getModuleCode() != null) {
            wrapper.eq(SysAuditLog::getModuleCode, query.getModuleCode());
        } else {
            wrapper.in(SysAuditLog::getModuleCode, MONITOR_MODULE_CODES);
        }

        wrapper.eq(query.getOperationType() != null, SysAuditLog::getOperationType, query.getOperationType());
        wrapper.eq(query.getSourceTerminal() != null, SysAuditLog::getSourceTerminal, query.getSourceTerminal());
        wrapper.eq(query.getDeviceId() != null, SysAuditLog::getDeviceId, query.getDeviceId());
        wrapper.eq(query.getRecordingId() != null, SysAuditLog::getRecordingId, query.getRecordingId());
        wrapper.eq(query.getOrgId() != null, SysAuditLog::getOrgId, query.getOrgId());

        if (query.getStartTime() != null) {
            wrapper.ge(SysAuditLog::getCreatedAt, LocalDateTime.parse(query.getStartTime(), DTF));
        }
        if (query.getEndTime() != null) {
            wrapper.le(SysAuditLog::getCreatedAt, LocalDateTime.parse(query.getEndTime(), DTF));
        }

        return wrapper;
    }

    private MonitorAuditLogVO toVO(SysAuditLog entity) {
        MonitorAuditLogVO vo = new MonitorAuditLogVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setUserName(entity.getUserName());
        vo.setRealName(entity.getRealName());
        vo.setOperationType(entity.getOperationType());
        vo.setModuleCode(entity.getModuleCode());
        vo.setModuleName(entity.getModuleName());
        vo.setTargetId(entity.getTargetId());
        vo.setTargetNo(entity.getTargetNo());
        vo.setOperationDesc(entity.getOperationDesc());
        vo.setResult(entity.getResult());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setIpAddress(entity.getIpAddress());
        vo.setSourceTerminal(entity.getSourceTerminal());
        vo.setDeviceId(entity.getDeviceId());
        vo.setRecordingId(entity.getRecordingId());
        vo.setOrgId(entity.getOrgId());
        if (entity.getCreatedAt() != null) {
            vo.setCreatedAt(entity.getCreatedAt().format(DTF));
        }
        return vo;
    }
}
