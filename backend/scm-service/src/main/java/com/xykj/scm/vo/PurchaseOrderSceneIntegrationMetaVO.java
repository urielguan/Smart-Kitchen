package com.xykj.scm.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PurchaseOrderSceneIntegrationMetaVO {

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
    private PurchaseOrderSceneIntegrationBindingVO currentBinding;
    private List<PurchaseOrderSceneIntegrationConfigOptionVO> configOptions = new ArrayList<>();
    private List<PurchaseOrderSceneIntegrationLogVO> recentSyncLogs = new ArrayList<>();
    private List<PurchaseOrderSceneIntegrationLogVO> recentCallbackLogs = new ArrayList<>();
}
