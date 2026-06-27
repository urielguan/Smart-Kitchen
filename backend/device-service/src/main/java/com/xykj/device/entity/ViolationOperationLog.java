package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 违规事件操作日志实体
 */
@Data
@TableName("violation_operation_log")
public class ViolationOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联告警ID */
    private Long alertId;

    /** 操作类型 */
    private String action;

    /** 操作名称 */
    private String actionName;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作内容/备注 */
    private String content;

    /** 操作终端 */
    private String terminal;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
