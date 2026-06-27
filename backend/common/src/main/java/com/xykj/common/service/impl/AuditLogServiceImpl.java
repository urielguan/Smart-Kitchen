package com.xykj.common.service.impl;

import com.xykj.common.context.RequestContext;
import com.xykj.common.context.UserContext;
import com.xykj.common.entity.SysAuditLog;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.mapper.SysAuditLogMapper;
import com.xykj.common.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 审计日志服务实现
 * 使用 REQUIRES_NEW 传播，确保业务事务回滚时审计日志仍能写入
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper sysAuditLogMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditModule module, AuditOperationType opType, Long targetId, String targetNo, String desc) {
        logAndReturnId(module, opType, targetId, targetNo, desc, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditModule module, AuditOperationType opType, Long targetId, String targetNo,
                    String desc, String beforeData, String afterData) {
        logAndReturnId(module, opType, targetId, targetNo, desc, beforeData, afterData, "success", null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditModule module, AuditOperationType opType, Long targetId, String targetNo,
                    String desc, String beforeData, String afterData, String result, String errorMsg) {
        logAndReturnId(module, opType, targetId, targetNo, desc, beforeData, afterData, result, errorMsg);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long logAndReturnId(AuditModule module, AuditOperationType opType, Long targetId, String targetNo, String desc) {
        return logAndReturnId(module, opType, targetId, targetNo, desc, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long logAndReturnId(AuditModule module, AuditOperationType opType, Long targetId, String targetNo,
                               String desc, String beforeData, String afterData) {
        return logAndReturnId(module, opType, targetId, targetNo, desc, beforeData, afterData, "success", null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long logAndReturnId(AuditModule module, AuditOperationType opType, Long targetId, String targetNo,
                               String desc, String beforeData, String afterData, String result, String errorMsg) {
        try {
            SysAuditLog auditLog = new SysAuditLog();
            auditLog.setUserId(UserContext.getUserId() != null ? UserContext.getUserId() : 0L);
            auditLog.setUserName(UserContext.getUsername() != null ? UserContext.getUsername() : "system");
            auditLog.setRealName(UserContext.getRealName());
            auditLog.setOrgId(UserContext.getOrgId());
            auditLog.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L);
            auditLog.setOperationType(opType.getCode());
            auditLog.setModuleCode(module.getCode());
            auditLog.setModuleName(module.getName());
            auditLog.setTargetId(targetId);
            auditLog.setTargetNo(targetNo);
            auditLog.setOperationDesc(desc);
            auditLog.setBeforeData(beforeData);
            auditLog.setAfterData(afterData);
            auditLog.setIpAddress(RequestContext.getIpAddress());
            auditLog.setUserAgent(RequestContext.getUserAgent());
            auditLog.setSourceTerminal(RequestContext.getSourceTerminal());
            auditLog.setResult(result);
            auditLog.setErrorMsg(errorMsg);
            auditLog.setCreatedAt(LocalDateTime.now());
            sysAuditLogMapper.insert(auditLog);
            return auditLog.getId();
        } catch (Exception e) {
            log.error("写入审计日志失败: module={}, opType={}, desc={}, error={}",
                    module.getCode(), opType.getCode(), desc, e.getMessage());
            return null;
        }
    }
}
