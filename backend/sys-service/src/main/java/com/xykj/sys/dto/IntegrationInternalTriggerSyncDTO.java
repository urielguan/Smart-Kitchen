package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IntegrationInternalTriggerSyncDTO {

    @NotNull(message = "配置ID不能为空")
    private Long configId;

    @NotBlank(message = "业务模块不能为空")
    private String bizModule;

    @NotBlank(message = "业务场景不能为空")
    private String bizScene;

    @NotNull(message = "业务主键不能为空")
    private Long bizId;

    @NotBlank(message = "业务编号不能为空")
    private String bizNo;

    @NotBlank(message = "第三方外部编号不能为空")
    private String externalNo;

    @NotNull(message = "组织ID不能为空")
    private Long orgId;

    @NotNull(message = "租户ID不能为空")
    private Long tenantId;

    private String maintenanceMode = "third_party";

    private String modeSource = "business_scene";

    private Integer modeLocked = 0;

    private String triggerType = "manual";

    private Integer queryOnly = 0;

    private Long operatorId;

    private String operatorName;

    private String operatorUsername;
}
