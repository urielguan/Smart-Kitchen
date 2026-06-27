package com.xykj.device.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警派单工单详情VO（聚合告警信息 + 派单信息 + 处理记录）
 */
@Data
public class AlertDispatchDetailVO {

    // ===== 告警信息 =====

    private Long alertId;
    private String alertNo;
    private String alertType;
    private String alertTypeName;
    private String alertLevel;
    private String alertLevelName;
    private String alertStatus;
    private String alertStatusName;
    private String alertContent;
    private Object alertDetail;
    private List<String> alertImages;
    private String alertVideoUrl;
    private String deviceName;
    private String deviceType;
    private String deviceTypeName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime triggeredAt;

    // ===== 派单信息 =====

    private Long dispatchId;
    private String dispatchNo;
    private String dispatchType;
    private String dispatchTypeName;
    private Long assignerId;
    private String assignerName;
    private Long handlerId;
    private String handlerName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    private String priority;
    private String priorityName;
    private String remark;
    private String status;
    private String statusName;
    private String handleResult;
    private List<Object> handleAttachments;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    private Long reviewedBy;
    private String reviewedByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewedAt;

    private String reviewResult;
    private String reviewRemark;
    private List<Object> reviewAttachments;

    private Long closedBy;
    private String closedByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closedAt;

    private String closeRemark;
    private Long archivedBy;
    private String archivedByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime archivedAt;

    private String archiveRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // ===== 工单处理记录 =====

    private List<AlertWorkOrderRecordVO> records;
}
