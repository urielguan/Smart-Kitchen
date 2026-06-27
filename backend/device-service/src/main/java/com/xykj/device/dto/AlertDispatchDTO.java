package com.xykj.device.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警派单表单
 */
@Data
public class AlertDispatchDTO {

    /** 派单方式：auto=自动，manual=人工 */
    private String dispatchType;

    /** 处理人ID（人工派单时必填） */
    private Long handlerId;

    /** 优先级：high/medium/low */
    private String priority;

    /** 处理截止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    /** 备注 */
    private String remark;
}
