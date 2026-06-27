package com.xykj.sys.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class IntegrationHealthCheckVO {

    private Long configId;
    private String configName;
    private Long orgId;
    private String orgName;
    private String bizModule;
    private String bizScene;
    private String providerCode;
    private String providerName;
    private Integer enabled;
    private Integer callbackEnabled;
    private Boolean authSuccess;
    private Boolean reachable;
    private Boolean callbackReachable;
    private String authMessage;
    private String reachableMessage;
    private String callbackMessage;
    private String testMessage;
    private LocalDateTime lastTestAt;
    private String lastTestStatus;
    private String lastTestMessage;
    private BigDecimal successRate24h;
    private BigDecimal callbackSuccessRate24h;
    private Long averageDurationMs24h;
    private LocalDateTime lastSyncAt;
    private String lastSyncStatus;
    private String lastErrorMessage;
    private List<IntegrationSyncLogVO> recentFailedLogs = new ArrayList<>();
    private List<IntegrationHealthCheckLogVO> recentTestLogs = new ArrayList<>();
}
