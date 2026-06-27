package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜谱计划详情VO
 */
@Data
public class RecipePlanDetailVO {

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
     * 目标人群类型
     */
    private String targetGroup;

    /**
     * 目标人群名称
     */
    private String targetGroupName;

    /**
     * 健康状况标签(逗号分隔)
     */
    private String healthStatus;

    /**
     * 饮食限制描述
     */
    private String dietRestrictions;

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
     * AI营养评估结果
     */
    private String aiNutritionAssessment;

    /**
     * 是否使用AI推荐
     */
    private Boolean useAiRecommend;

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
     * 审核意见
     */
    private String auditRemark;

    /**
     * 制定人
     */
    private String createdByName;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 菜谱列表
     */
    private List<RecipePlanItemVO> recipes;

    private List<MealScheduleVO> mealSchedules;

    /**
     * 菜谱数量
     */
    private Integer recipeCount;

    /**
     * 是否有待审核的调整申请
     */
    private Boolean hasPendingAdjustment;
    /** 审批历史 */
    private List<RecipePlanAuditLogVO> auditLogs;

    /**
     * 菜谱计划明细VO
     */
    @Data
    public static class RecipePlanItemVO {
        private Long id;
        private Long recipeId;
        private String recipeCode;
        private String recipeName;
        private String categoryName;
        private String imageUrl;
        private String mealKey;
        private String mealType;
        private String mealName;
        private Integer mealExpectedCount;
        private Integer mealSortOrder;
        private Integer plannedServings;
        private Integer cookedServings;
        private BigDecimal unitCost;
        private BigDecimal totalCost;
        private Integer sortOrder;
        private String remark;
        private String status;
        /** 食材列表 */
        private List<IngredientBrief> ingredients;
    }

    /** 食材简要信息 */
    @Data
    public static class IngredientBrief {
        private String materialName;
        private BigDecimal quantity;
        private String unit;
        private Boolean isMain;
    }

    @Data
    public static class MealScheduleVO {
        private String mealKey;
        private String mealType;
        private String mealTypeName;
        private String mealName;
        private Integer expectedCount;
        private Integer sortOrder;
        private List<RecipePlanItemVO> recipes;
    }
}
