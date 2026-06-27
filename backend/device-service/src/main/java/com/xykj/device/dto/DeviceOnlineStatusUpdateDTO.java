package com.xykj.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 设备在线状态更新入参
 */
@Data
public class DeviceOnlineStatusUpdateDTO {

    /** 在线状态：online/offline/fault */
    @NotBlank(message = "在线状态不能为空")
    private String onlineStatus;

    /** 变更原因 */
    private String reason;

    /** 更新来源：system/manual */
    private String sourceType;
}
