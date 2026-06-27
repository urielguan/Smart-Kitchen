package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IntegrationSyncTaskTriggerDTO {

    @NotNull(message = "配置ID不能为空")
    private Long configId;

    @NotBlank(message = "业务模块不能为空")
    private String bizModule;

    @NotBlank(message = "业务场景不能为空")
    private String bizScene;

    @NotBlank(message = "业务主键不能为空")
    private String bizId;

    @NotBlank(message = "业务编号不能为空")
    private String bizNo;

    @NotBlank(message = "第三方外部编号不能为空")
    private String externalNo;

    private String maintenanceMode = "third_party";
    private String modeSource = "user_selected";
    private Integer modeLocked = 0;
    private String triggerType = "manual";
    private Integer queryOnly = 0;
}
