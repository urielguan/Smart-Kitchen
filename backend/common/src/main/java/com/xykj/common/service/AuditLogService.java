package com.xykj.common.service;

import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;

/**
 * 审计日志服务
 */
public interface AuditLogService {

    void log(AuditModule module, AuditOperationType opType, Long targetId, String targetNo, String desc);

    void log(AuditModule module, AuditOperationType opType, Long targetId, String targetNo,
             String desc, String beforeData, String afterData);

    void log(AuditModule module, AuditOperationType opType, Long targetId, String targetNo,
             String desc, String beforeData, String afterData, String result, String errorMsg);

    Long logAndReturnId(AuditModule module, AuditOperationType opType, Long targetId, String targetNo, String desc);

    Long logAndReturnId(AuditModule module, AuditOperationType opType, Long targetId, String targetNo,
                        String desc, String beforeData, String afterData);

    Long logAndReturnId(AuditModule module, AuditOperationType opType, Long targetId, String targetNo,
                        String desc, String beforeData, String afterData, String result, String errorMsg);
}
