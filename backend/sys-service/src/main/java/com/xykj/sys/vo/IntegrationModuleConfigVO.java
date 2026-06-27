package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class IntegrationModuleConfigVO {

    private Long id;
    private Long orgId;
    private String orgName;
    private String bizModule;
    private String bizScene;
    private String providerCode;
    private String providerName;
    private String configName;
    private Integer enabled;
    private String defaultMode;
    private Integer allowDocumentSwitch;
    private Integer forceThirdParty;
    private String triggerStrategy;
    private Integer allowManualFallback;
    private Integer autoCoverEnabled;
    private String autoCoverStrategy;
    private Integer allowManualConfirmCover;
    private Integer attachmentPullEnabled;
    private Integer callbackEnabled;
    private String callbackUrl;
    private String externalNoFieldRule;
    private String accessTokenUrl;
    private String refreshTokenUrl;
    private String tokenRequestMethod;
    private Integer syncFrequencyMinutes;
    private String scheduleCron;
    private Long timeoutMs;
    private Integer retryMaxCount;
    private LocalDateTime lastSyncAt;
    private String lastSyncStatus;
    private String lastErrorMessage;
    private String remark;
    private List<IntegrationSecretMaskedVO> secrets = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
