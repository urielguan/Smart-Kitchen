package com.xykj.cook.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 烹饪任务时间线事件VO
 * 聚合温度记录、AI预警、状态变更三类事件
 */
@Data
public class CookTimelineEventVO {

    /** 事件类型：temperature / ai_alert / status_change */
    private String eventType;

    /** 事件时间 */
    private LocalDateTime eventTime;

    /** 事件标题 */
    private String title;

    /** 事件详情 */
    private String detail;

    /** 级别：normal / warning / critical */
    private String level;

    /** 操作人（status_change 专用） */
    private String operatorName;

    /** 温度值（temperature 专用） */
    private Integer temperature;

    /** 是否异常（temperature 专用） */
    private Boolean abnormal;

    /** 违规类型（ai_alert 专用） */
    private String violationType;
}
