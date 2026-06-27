package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntegrationInternalLogBriefVO {

    private Long id;
    private String logType;
    private Long bindingId;
    private Long configId;
    private String providerCode;
    private String providerName;
    private String externalNo;
    private String status;
    private String triggerType;
    private String taskNo;
    private String message;
    private String errorMessage;
    private LocalDateTime createdAt;
}
