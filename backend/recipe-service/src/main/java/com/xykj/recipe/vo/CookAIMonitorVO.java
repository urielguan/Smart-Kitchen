package com.xykj.recipe.vo;

import lombok.Data;

/**
 * AI监控VO
 */
@Data
public class CookAIMonitorVO {

    private String violationType;
    private String violationName;
    private String level;
    private String description;
    private String suggestion;
    private String snapshotTime;
}
