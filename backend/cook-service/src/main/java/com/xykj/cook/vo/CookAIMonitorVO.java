package com.xykj.cook.vo;

import lombok.Data;

/**
 * AI监控VO
 */
@Data
public class CookAIMonitorVO {

    private Integer alertIndex;
    private String violationType;
    private String violationName;
    private String level;
    private String description;
    private String suggestion;
    private String snapshotTime;
    private Boolean acknowledged;
    private String acknowledgedBy;
    private String acknowledgedAt;
}
