package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IntegrationFieldMappingDuplicateCheckDTO {

    private Long id;

    @NotNull(message = "配置ID不能为空")
    private Long configId;

    @NotBlank(message = "系统字段不能为空")
    private String targetField;

    @Min(value = 0, message = "启用状态仅支持 0 或 1")
    @Max(value = 1, message = "启用状态仅支持 0 或 1")
    private Integer enabled = 1;
}
