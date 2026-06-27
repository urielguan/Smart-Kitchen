package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntegrationSyncLogVO {

    private Long id;
    private Long taskId;
    private String taskNo;
    private Long bindingId;
    private Long configId;
    private String configName;
    private String configNameSnapshot;
    private String providerCode;
    private String providerName;
    private String providerNameSnapshot;
    private Long bizId;
    private String bizNo;
    private String bizModule;
    private String bizScene;
    private String externalNo;
    private String taskType;
    private String requestPayload;
    private String requestHeaders;
    private String requestBody;
    private String responsePayload;
    private String normalizedPayload;
    private String syncStatus;
    private String errorCode;
    private String errorMessage;
    private Long durationMs;
    private Long auditLogId;
    private String resultMessage;
    private String writeBackResult;
    private String triggerType;
    private Long operatorId;
    private String operatorName;
    private Long orgId;
    private String orgName;
    private String orgNameSnapshot;
    private String handleStatus;
    private Long handledBy;
    private String handledByName;
    private LocalDateTime handledAt;
    private String handleRemark;
    private LocalDateTime createdAt;
}
