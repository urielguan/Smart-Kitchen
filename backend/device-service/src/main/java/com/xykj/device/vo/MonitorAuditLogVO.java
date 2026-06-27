package com.xykj.device.vo;

import lombok.Data;

/**
 * 监控审计日志VO
 */
@Data
public class MonitorAuditLogVO {

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
    private String result;
    private String errorMsg;
    private String ipAddress;
    private String sourceTerminal;
    private Long deviceId;
    private Long recordingId;
    private Long orgId;
    private String createdAt;
}
