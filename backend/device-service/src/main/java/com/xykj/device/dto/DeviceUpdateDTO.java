package com.xykj.device.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 编辑设备请求
 */
@Data
public class DeviceUpdateDTO {

    private String deviceName;
    private String deviceType;
    private String deviceModel;
    private String manufacturer;
    private String sn;
    private String macAddress;
    private String ipAddress;
    private String locationDesc;

    private String sipConfig;
    private String configParams;

    private LocalDate installDate;
    private LocalDate warrantyExpiresAt;
    private Integer maintenanceCycleDays;
    private LocalDate nextMaintenanceAt;

    /** 状态：active/inactive/maintenance */
    private String status;

    /** 业务状态变更原因 */
    private String statusChangeReason;

    private Long managerId;
    private String managerName;
    private String managerPhone;

    private String remark;

    /** 所属组织ID */
    private Long orgId;
}
