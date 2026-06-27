package com.xykj.recipe.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜谱计划审批日志VO
 */
@Data
public class RecipePlanAuditLogVO {

    private Long id;

    private Long planId;

    private Integer round;

    private String action;

    private String actionName;

    private String operatorName;

    private String remark;

    private LocalDateTime createdAt;
}
