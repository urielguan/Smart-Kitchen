package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备状态履历VO
 */
@Data
public class DeviceStatusLogVO {

    private String statusType;
    private String statusTypeName;
    private String fromStatus;
    private String fromStatusName;
    private String toStatus;
    private String toStatusName;
    private String sourceType;
    private String sourceTypeName;
    private String reason;
    private Long operatorId;
    private String operatorName;
    private String result;
    private LocalDateTime createdAt;
}
