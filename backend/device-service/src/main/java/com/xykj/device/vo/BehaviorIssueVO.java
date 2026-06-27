package com.xykj.device.vo;

import lombok.Data;

/**
 * 行为问题VO
 */
@Data
public class BehaviorIssueVO {

    /** 问题ID */
    private Long id;

    /** 问题类型 */
    private String issueType;

    /** 问题名称 */
    private String issueName;

    /** 问题描述 */
    private String description;

    /** 发生次数 */
    private Integer occurrenceCount;

    /** 严重程度: low/medium/high */
    private String severity;

    /** 严重程度名称 */
    private String severityName;

    /** 建议措施 */
    private String suggestion;
}
