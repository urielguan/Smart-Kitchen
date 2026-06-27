package com.xykj.auth.service.impl;

import com.xykj.common.entity.SysAuditLog;
import com.xykj.common.mapper.SysAuditLogMapper;
import com.xykj.auth.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl implements LoginLogService {

    private final SysAuditLogMapper sysAuditLogMapper;

    private static final String MODULE_CODE = "auth_login";
    private static final String MODULE_NAME = "认证登录";
    private static final String OP_TYPE = "login";

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginSuccess(Long userId, String username, String realName, Long orgId, Long tenantId, String ip, String userAgent) {
        try {
            SysAuditLog auditLog = buildBaseLog(userId, username, realName, orgId, tenantId, ip, userAgent);
            auditLog.setResult("success");
            auditLog.setOperationDesc("用户登录成功");
            sysAuditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("登录成功审计写入失败，忽略并继续主流程: userId={}, username={}, error={}",
                    userId, username, e.getMessage(), e);
        }

        log.info("登录成功 - userId={}, username={}, ip={}", userId, username, ip);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(Long userId, String username, String realName, Long orgId, Long tenantId, String ip, String userAgent, String reason) {
        try {
            SysAuditLog auditLog = buildBaseLog(userId, username, realName, orgId, tenantId, ip, userAgent);
            auditLog.setResult("fail");
            auditLog.setOperationDesc("用户登录失败");
            auditLog.setErrorMsg(reason);
            sysAuditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("登录失败审计写入失败，忽略并继续主流程: userId={}, username={}, error={}",
                    userId, username, e.getMessage(), e);
        }

        log.warn("登录失败 - userId={}, username={}, ip={}, reason={}", userId, username, ip, reason);
    }

    private SysAuditLog buildBaseLog(Long userId, String username, String realName, Long orgId, Long tenantId, String ip, String userAgent) {
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setUserId(userId != null ? userId : 0L);
        auditLog.setUserName(username != null ? username : "unknown");
        auditLog.setRealName(realName);
        auditLog.setOperationType(OP_TYPE);
        auditLog.setModuleCode(MODULE_CODE);
        auditLog.setModuleName(MODULE_NAME);
        auditLog.setTargetId(userId);
        auditLog.setIpAddress(ip);
        auditLog.setUserAgent(userAgent);
        auditLog.setOrgId(orgId);
        auditLog.setTenantId(tenantId != null ? tenantId : 1L);
        auditLog.setCreatedAt(LocalDateTime.now());
        return auditLog;
    }
}
