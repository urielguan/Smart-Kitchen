package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜谱计划列表VO
 */
@Data
public class RecipePlanVO {

    /**
     * 计划ID
     */
    private Long id;

    /**
     * 计划编码
     */
    private String planCode;

    /**
     * 计划日期
     */
    private LocalDate planDate;

    /**
     * 实施开始日期
     */
    private LocalDate startDate;

    /**
     * 实施结束日期
     */
    private LocalDate endDate;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 所属组织名称
     */
    private String orgName;

    /**
     * 餐次
     */
    private String mealType;

    /**
     * 餐次名称
     */
    private String mealTypeName;

    /**
     * 就餐人数
     */
    private Integer expectedCount;

    private String mealDisplayName;

    private String expectedCountDisplay;

    private Integer mealScheduleCount;

    /**
     * 总份数
     */
    private Integer totalServings;

    /**
     * 预估总成本
     */
    private BigDecimal estimatedCost;

    /**
     * 营养达标率
     */
    private BigDecimal nutritionPassRate;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 调整模式
     */
    private String adjustmentMode;

    /**
     * 调整模式名称
     */
    private String adjustmentModeName;

    /**
     * 调整提示
     */
    private String adjustmentHint;

    /**
     * 是否允许调整
     */
    private Boolean canAdjust;

    /**
     * 调整计划状态
     */
    private String adjustmentStatus;

    /**
     * 调整计划状态名称
     */
    private String adjustmentStatusName;

    /**
     * 库存风险状态
     */
    private String stockRiskStatus;

    /**
     * 库存风险状态名称
     */
    private String stockRiskStatusName;

    /**
     * 库存风险说明
     */
    private String stockRiskMessage;

    /**
     * 最近一次库存校验时间
     */
    private LocalDateTime stockValidatedAt;

    /**
     * 下一次自动复检时间
     */
    private LocalDateTime stockNextRecheckAt;

    /**
     * 提交人
     */
    private String submittedByName;

    /**
     * 提交时间
     */
    private LocalDateTime submittedAt;

    /**
     * 审核人
     */
    private String auditedByName;

    /**
     * 审核时间
     */
    private LocalDateTime auditedAt;

    /**
     * 制定人
     */
    private String createdByName;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 菜谱数量
     */
    private Integer recipeCount;
}
