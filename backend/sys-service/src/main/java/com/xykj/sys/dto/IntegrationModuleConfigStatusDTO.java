package com.xykj.sys.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IntegrationModuleConfigStatusDTO {

    @NotNull(message = "启用状态不能为空")
    private Integer enabled;
}
