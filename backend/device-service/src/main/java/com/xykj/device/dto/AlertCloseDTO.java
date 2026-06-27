package com.xykj.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 告警关闭DTO
 */
@Data
public class AlertCloseDTO {

    /** 关闭说明 */
    @NotBlank(message = "关闭说明不能为空")
    private String closeRemark;

    /** 归档说明 */
    private String archiveRemark;
}
