package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警详情VO
 */
@Data
public class AlertDetailVO {

    private Long id;

    /** 告警编号 */
    private String alertNo;

    /** 告警类型 */
    private String alertType;

    /** 关联告警规则ID */
    private Long alertRuleId;

    /** 关联告警规则名称 */
    private String alertRuleName;

    /** 告警类型名称 */
    private String alertTypeName;

    /** 告警级别 */
    private String alertLevel;

    /** 告警级别名称 */
    private String alertLevelName;

    /** 关联设备ID */
    private Long deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 设备类型 */
    private String deviceType;

    /** 设备类型名称 */
    private String deviceTypeName;

    /** 告警内容 */
    private String alertContent;

    /** 告警详情 */
    private Object alertDetail;

    /** 告警截图 */
    private List<String> alertImages;

    /** 告警视频URL */
    private String alertVideoUrl;

    /** 触发时间 */
    private LocalDateTime triggeredAt;

    /** 状态 */
    private String status;

    /** 状态名称 */
    private String statusName;

    // ===== 指派信息 =====

    /** 指派处理人ID */
    private Long assignedTo;

    /** 处理人姓名 */
    private String assignedToName;

    /** 处理人手机号 */
    private String assignedToPhone;

    /** 指派时间 */
    private LocalDateTime assignedAt;

    // ===== 处置信息 =====

    /** 实际处理人ID */
    private Long handledBy;

    /** 处理人姓名 */
    private String handledByName;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 处理结果 */
    private String handleResult;

    /** 处理照片 */
    private List<String> handleImages;

    // ===== 复核信息 =====

    /** 复核人ID */
    private Long reviewedBy;

    /** 复核人姓名 */
    private String reviewedByName;

    /** 复核时间 */
    private LocalDateTime reviewedAt;

    /** 复核结果 */
    private String reviewResult;

    /** 复核备注 */
    private String reviewRemark;

    /** 关闭人ID */
    private Long closedBy;

    /** 关闭人姓名 */
    private String closedByName;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 关闭说明 */
    private String closeRemark;

    /** 归档人ID */
    private Long archivedBy;

    /** 归档人姓名 */
    private String archivedByName;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** 归档说明 */
    private String archiveRemark;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
