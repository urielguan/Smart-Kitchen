package com.xykj.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 违规事件处理DTO
 */
@Data
public class ViolationHandleDTO {

    /** 处理状态: assigned/processing/resolved */
    @NotBlank(message = "处理状态不能为空")
    private String status;

    /** 处理人ID */
    private Long handlerId;

    /** 处理人姓名 */
    private String handlerName;

    /** 处理备注 */
    @NotBlank(message = "处理备注不能为空")
    private String handleRemark;
}
