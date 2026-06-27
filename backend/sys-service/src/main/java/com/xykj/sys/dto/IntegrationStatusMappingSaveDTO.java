package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IntegrationStatusMappingSaveDTO {

    @NotNull(message = "配置ID不能为空")
    private Long configId;

    @NotBlank(message = "第三方状态编码不能为空")
    @Size(max = 100, message = "第三方状态编码长度不能超过100个字符")
    private String sourceStatusCode;

    @NotBlank(message = "第三方状态名称不能为空")
    @Size(max = 100, message = "第三方状态名称长度不能超过100个字符")
    private String sourceStatusName;

    @NotBlank(message = "系统标准状态不能为空")
    @Size(max = 100, message = "系统标准状态长度不能超过100个字符")
    private String targetStatusCode;

    @Min(value = 0, message = "结束同步仅支持 0 或 1")
    @Max(value = 1, message = "结束同步仅支持 0 或 1")
    private Integer finishFlag = 0;

    @Min(value = 0, message = "触发业务动作仅支持 0 或 1")
    @Max(value = 1, message = "触发业务动作仅支持 0 或 1")
    private Integer triggerBusinessAction = 0;

    @Size(max = 100, message = "动作编码长度不能超过100个字符")
    private String actionCode;

    @Min(value = 0, message = "写入附件仅支持 0 或 1")
    @Max(value = 1, message = "写入附件仅支持 0 或 1")
    private Integer writeAttachmentFlag = 0;

    private Integer sortNo = 1;

    @Min(value = 0, message = "启用状态仅支持 0 或 1")
    @Max(value = 1, message = "启用状态仅支持 0 或 1")
    private Integer enabled = 1;

    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
