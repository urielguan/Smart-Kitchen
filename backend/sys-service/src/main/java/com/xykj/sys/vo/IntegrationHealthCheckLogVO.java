package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntegrationHealthCheckLogVO {

    private Long id;
    private String testStatus;
    private Boolean authSuccess;
    private Boolean reachable;
    private Boolean callbackReachable;
    private String authMessage;
    private String reachabilityMessage;
    private String callbackMessage;
    private String testMessage;
    private String errorCode;
    private String errorMessage;
    private String requestPayload;
    private String requestHeaders;
    private String requestBody;
    private String responsePayload;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdAt;
}
