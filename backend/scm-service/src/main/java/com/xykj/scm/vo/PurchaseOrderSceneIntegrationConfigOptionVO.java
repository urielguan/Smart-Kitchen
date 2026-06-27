package com.xykj.scm.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PurchaseOrderSceneIntegrationConfigOptionVO {

    private Long id;
    private String configName;
    private String providerCode;
    private String providerName;
    private String defaultMode;
    private Integer allowDocumentSwitch;
    private Integer forceThirdParty;
    private Integer allowManualFallback;
    private Integer autoCoverEnabled;
    private Integer callbackEnabled;
    private Integer syncFrequencyMinutes;
    private String externalNoFieldRule;
    private String lastSyncStatus;
    private String lastErrorMessage;
    private LocalDateTime lastSyncAt;
}
