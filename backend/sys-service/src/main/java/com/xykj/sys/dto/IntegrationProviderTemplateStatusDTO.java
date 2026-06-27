package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IntegrationProviderTemplateStatusDTO {

    @NotBlank(message = "状态不能为空")
    private String status;
}
