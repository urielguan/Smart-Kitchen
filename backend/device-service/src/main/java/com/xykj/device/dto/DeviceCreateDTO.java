package com.xykj.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新增设备请求
 */
@Data
public class DeviceCreateDTO {

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    private String deviceModel;
    private String manufacturer;
    private String sn;
    private String macAddress;
    private String ipAddress;
    private String locationDesc;

    /** SIP协议配置（JSON字符串） */
    private String sipConfig;

    /** 设备特有配置参数（JSON字符串） */
    private String configParams;

    private LocalDate installDate;
    private LocalDate warrantyExpiresAt;
    private Integer maintenanceCycleDays;
    private LocalDate nextMaintenanceAt;

    /** 业务状态：active/inactive/maintenance */
    private String status;

    /** 负责人ID */
    private Long managerId;
    /** 负责人姓名 */
    private String managerName;
    /** 负责人电话 */
    private String managerPhone;

    @NotNull(message = "所属组织不能为空")
    private Long orgId;

    private String remark;
}
