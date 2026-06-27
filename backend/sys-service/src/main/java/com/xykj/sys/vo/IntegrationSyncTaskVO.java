package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntegrationSyncTaskVO {

    private Long id;
    private String taskNo;
    private Long bindingId;
    private Long configId;
    private String configName;
    private String providerCode;
    private String providerName;
    private String taskType;
    private String triggerType;
    private String taskStatus;
    private LocalDateTime planExecuteAt;
    private LocalDateTime startAt;
    private LocalDateTime finishAt;
    private Integer retryCount;
    private Long operatorId;
    private String operatorName;
    private Long bizId;
    private String bizNo;
    private String bizModule;
    private String bizScene;
    private String externalNo;
    private String resultMessage;
    private Long orgId;
    private String orgName;
    private Integer retryMaxCount;
    private Boolean retryAvailable;
    private String retryDisabledReason;
    private LocalDateTime createdAt;
}
