package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntegrationInternalBindingSummaryVO {

    private Long bindingId;
    private Long configId;
    private String configName;
    private String providerCode;
    private String providerName;
    private String bizModule;
    private String bizScene;
    private Long bizId;
    private String bizNo;
    private String externalNo;
    private String maintenanceMode;
    private String modeSource;
    private Integer modeLocked;
    private String syncStatus;
    private String lastErrorMessage;
    private LocalDateTime firstBindAt;
    private LocalDateTime lastSyncAt;
    private LocalDateTime nextSyncAt;
    private LocalDateTime updatedAt;
}
