package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IntegrationStatusMappingDuplicateCheckDTO {

    private Long id;

    @NotNull(message = "配置ID不能为空")
    private Long configId;

    @NotBlank(message = "第三方状态编码不能为空")
    @Size(max = 100, message = "第三方状态编码长度不能超过100个字符")
    private String sourceStatusCode;
}
