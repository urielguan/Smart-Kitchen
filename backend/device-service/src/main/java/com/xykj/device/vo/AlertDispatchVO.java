package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警派单工单列表项VO
 */
@Data
public class AlertDispatchVO {

    private Long id;
    private String dispatchNo;

    /** 告警信息 */
    private Long alertId;
    private String alertNo;
    private String alertType;
    private String alertTypeName;
    private String alertLevel;
    private String alertLevelName;
    private String alertContent;

    /** 派单信息 */
    private String dispatchType;
    private String dispatchTypeName;
    private Long assignerId;
    private String assignerName;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime deadline;
    private String priority;
    private String priorityName;

    /** 状态 */
    private String status;
    private String statusName;

    /** 处理信息 */
    private String handleResult;
    private List<String> handleAttachments;
    private LocalDateTime completedAt;

    /** 复核信息 */
    private Long reviewedBy;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String reviewResult;

    private LocalDateTime createdAt;
}
