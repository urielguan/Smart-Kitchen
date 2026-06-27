package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备详情VO
 */
@Data
public class DeviceDetailVO {

    // ===== 基础信息 =====
    private Long id;
    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private String deviceTypeName;
    private String deviceModel;
    private String manufacturer;
    private String sn;
    private String macAddress;
    private String ipAddress;
    private String locationDesc;

    // ===== 设备状态 =====
    private String onlineStatus;
    private String onlineStatusName;
    private String status;
    private String statusName;
    private LocalDateTime lastHeartbeatAt;

    // ===== 负责人与组织 =====
    private Long managerId;
    private String managerName;
    private String managerPhone;
    private Long orgId;
    private String orgName;

    // ===== 日期与维保 =====
    private LocalDate installDate;
    private LocalDate warrantyExpiresAt;
    private Integer maintenanceCycleDays;
    private LocalDate lastMaintenanceAt;
    private LocalDate nextMaintenanceAt;

    // ===== SIP协议配置 =====
    private Map<String, Object> sipConfig;

    // ===== 设备特有配置 =====
    private Map<String, Object> configParams;

    // ===== 3D定位信息 =====
    private Object position3d;
    private String model3dUrl;
    private Object model3dScale;

    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
