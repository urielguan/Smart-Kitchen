package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警工单操作记录实体类
 * 对应数据库表: device_alert_work_order_record
 */
@Data
@TableName("device_alert_work_order_record")
public class AlertWorkOrderRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 派单ID */
    private Long dispatchId;

    /** 告警ID */
    private Long alertId;

    /** 操作类型：dispatch/process/complete/review/cancel */
    private String action;

    /** 操作名称 */
    private String actionName;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作内容 */
    private String content;

    /** 附件（JSON数组） */
    private String attachments;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
