package com.xykj.health.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 晨检任务异动留痕
 */
@Data
public class HealthCheckMovementLogVO {

    private Long id;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 事件名称
     */
    private String eventName;

    /**
     * 原因编码
     */
    private String reasonCode;

    /**
     * 原因说明
     */
    private String reasonDesc;

    /**
     * 变更前摘要
     */
    private String beforeSummary;

    /**
     * 变更后摘要
     */
    private String afterSummary;

    /**
     * 留痕时间
     */
    private LocalDateTime createdAt;
}
