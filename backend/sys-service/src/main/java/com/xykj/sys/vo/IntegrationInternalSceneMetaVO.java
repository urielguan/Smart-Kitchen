package com.xykj.sys.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IntegrationInternalSceneMetaVO {

    private String bizModule;
    private String bizScene;
    private Long bizId;
    private Long orgId;
    private Long tenantId;
    private Long selectedConfigId;
    private String defaultMode;
    private Integer allowDocumentSwitch;
    private Integer forceThirdParty;
    private Integer allowManualFallback;
    private Integer autoCoverEnabled;
    private String externalNoFieldRule;
    private IntegrationInternalBindingSummaryVO currentBinding;
    private List<IntegrationInternalModuleOptionVO> configOptions = new ArrayList<>();
    private List<IntegrationInternalLogBriefVO> recentSyncLogs = new ArrayList<>();
    private List<IntegrationInternalLogBriefVO> recentCallbackLogs = new ArrayList<>();
}
