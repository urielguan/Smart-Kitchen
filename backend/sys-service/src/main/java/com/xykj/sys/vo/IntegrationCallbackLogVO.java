package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntegrationCallbackLogVO {

    private Long id;
    private Long bindingId;
    private Long configId;
    private String configName;
    private String providerCode;
    private String providerName;
    private String callbackUri;
    private String callbackHeaders;
    private String callbackPayload;
    private String clientIp;
    private String signResult;
    private String idempotentKey;
    private String processStatus;
    private String processResult;
    private String errorMessage;
    private Long bizId;
    private String bizNo;
    private String bizModule;
    private String bizScene;
    private String externalNo;
    private Long taskId;
    private String taskNo;
    private Long syncLogId;
    private Long auditLogId;
    private Long orgId;
    private String orgName;
    private LocalDateTime createdAt;
}
