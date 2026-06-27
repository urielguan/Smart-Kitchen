package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警派单实体类
 * 对应数据库表: device_alert_dispatch
 */
@Data
@TableName("device_alert_dispatch")
public class AlertDispatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 派单编号 */
    private String dispatchNo;

    /** 告警ID */
    private Long alertId;

    /** 告警编号（冗余） */
    private String alertNo;

    /** 派单方式：auto=自动，manual=人工 */
    private String dispatchType;

    /** 派单人ID（自动派单为0） */
    private Long assignerId;

    /** 派单人姓名 */
    private String assignerName;

    /** 处理人ID */
    private Long handlerId;

    /** 处理人姓名 */
    private String handlerName;

    /** 处理截止时间 */
    private LocalDateTime deadline;

    /** 优先级：high/medium/low */
    private String priority;

    /** 备注 */
    private String remark;

    /** 状态：pending/processing/completed/reviewed/cancelled/rejected */
    private String status;

    /** 处理结果 */
    private String handleResult;

    /** 处理附件（JSON数组） */
    private String handleAttachments;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 复核人ID */
    private Long reviewedBy;

    /** 复核时间 */
    private LocalDateTime reviewedAt;

    /** 复核结果 */
    private String reviewResult;

    /** 复核附件（JSON数组） */
    private String reviewAttachments;

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
