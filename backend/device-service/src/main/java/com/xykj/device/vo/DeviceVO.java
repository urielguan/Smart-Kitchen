package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备列表项VO
 */
@Data
public class DeviceVO {

    private Long id;
    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private String deviceTypeName;
    private String deviceModel;
    private String manufacturer;
    private String locationDesc;
    private String onlineStatus;
    private String onlineStatusName;
    private String status;
    private String statusName;
    private String managerName;
    private String managerPhone;
    private Long orgId;
    private String orgName;
    private LocalDateTime lastHeartbeatAt;
    private LocalDateTime updatedAt;

    /** 设备特有摘要信息（根据类型动态展示） */
    private String typeSpecificSummary;
}
