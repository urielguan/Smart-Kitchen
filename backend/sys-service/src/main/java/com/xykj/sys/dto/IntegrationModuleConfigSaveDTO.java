package com.xykj.sys.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IntegrationModuleConfigSaveDTO {

    @NotNull(message = "所属组织不能为空")
    private Long orgId;

    @NotBlank(message = "业务模块不能为空")
    private String bizModule;

    @NotBlank(message = "业务场景不能为空")
    private String bizScene;

    @NotBlank(message = "第三方平台不能为空")
    private String providerCode;

    @NotBlank(message = "配置名称不能为空")
    private String configName;

    private Integer enabled = 1;
    private String defaultMode = "manual";
    private Integer allowDocumentSwitch = 1;
    private Integer forceThirdParty = 0;
    private String triggerStrategy = "manual";
    private Integer allowManualFallback = 1;
    private Integer autoCoverEnabled = 0;
    private String autoCoverStrategy = "merge";
    private Integer allowManualConfirmCover = 1;
    private Integer attachmentPullEnabled = 0;
    private Integer callbackEnabled = 0;
    private String callbackUrl;
    private String externalNoFieldRule;
    private String accessTokenUrl;
    private String refreshTokenUrl;
    private String tokenRequestMethod = "POST";
    private Integer syncFrequencyMinutes = 60;
    private String scheduleCron;
    private Long timeoutMs = 10000L;
    private Integer retryMaxCount = 3;
    private String remark;

    @Valid
    private List<IntegrationSecretInputDTO> secrets = new ArrayList<>();
}
