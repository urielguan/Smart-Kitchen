package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IntegrationFieldMappingSaveDTO {

    @NotNull(message = "配置ID不能为空")
    private Long configId;

    private String sourceField;
    private String sourcePath;

    @NotBlank(message = "系统字段不能为空")
    private String targetField;

    @NotBlank(message = "转换规则类型不能为空")
    private String transformType;

    private String transformRule;
    private String defaultValue;

    @Min(value = 0, message = "是否必填仅支持 0 或 1")
    @Max(value = 1, message = "是否必填仅支持 0 或 1")
    private Integer requiredFlag = 0;

    private Integer sortNo = 1;

    @Min(value = 0, message = "启用状态仅支持 0 或 1")
    @Max(value = 1, message = "启用状态仅支持 0 或 1")
    private Integer enabled = 1;

    private String errorStrategy = "fail";
    private String remark;
}
