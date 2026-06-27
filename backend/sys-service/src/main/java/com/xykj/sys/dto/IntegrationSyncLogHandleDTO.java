package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IntegrationSyncLogHandleDTO {

    @NotBlank(message = "处理状态不能为空")
    @Size(max = 50, message = "处理状态长度不能超过50个字符")
    private String handleStatus;

    @Size(max = 500, message = "处理备注长度不能超过500个字符")
    private String handleRemark;
}
