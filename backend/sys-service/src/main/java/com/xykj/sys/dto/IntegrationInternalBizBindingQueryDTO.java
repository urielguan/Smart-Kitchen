package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IntegrationInternalBizBindingQueryDTO {

    @NotBlank(message = "业务模块不能为空")
    private String bizModule;

    @NotBlank(message = "业务场景不能为空")
    private String bizScene;

    @NotNull(message = "业务主键不能为空")
    private Long bizId;

    @NotNull(message = "组织ID不能为空")
    private Long orgId;

    @NotNull(message = "租户ID不能为空")
    private Long tenantId;

    private Long configId;

    private Integer logLimit = 5;
}
