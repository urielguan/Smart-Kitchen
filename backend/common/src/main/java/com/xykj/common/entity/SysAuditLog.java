package com.xykj.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作审计日志实体（对应 sys_audit_log 表）
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String userName;
    private String realName;
    private String operationType;
    private String moduleCode;
    private String moduleName;
    private Long targetId;
    private String targetNo;
    private String operationDesc;
    private String beforeData;
    private String afterData;
    private String ipAddress;
    private String userAgent;
    private String sourceTerminal;
    private Long deviceId;
    private Long recordingId;
    private String result;
    private String errorMsg;
    private Long orgId;
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
