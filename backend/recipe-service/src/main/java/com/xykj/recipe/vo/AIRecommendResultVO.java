package com.xykj.recipe.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI推荐结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIRecommendResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 推荐菜谱列表
     */
    private List<RecipeVO> recipes;

    /**
     * 预估总成本
     */
    private BigDecimal totalEstimatedCost;

    /**
     * 预算状态：within/near/exceeded
     */
    private String budgetStatus;

    /**
     * 预算警告信息
     */
    private String budgetWarning;

    /**
     * 周计划结构（当planDimension为week时）
     */
    private List<DailyPlanVO> weeklyPlan;

    /**
     * 月计划结构（当planDimension为month时）
     */
    private List<WeeklyPlanVO> monthlyPlan;

    /**
     * 推荐理由
     */
    private String recommendReason;

    /**
     * 推荐统计
     */
    private RecommendStatistics statistics;

    /**
     * 预算信息
     */
    private BudgetInfo budgetInfo;

    /**
     * 营养总览
     */
    private NutritionOverview nutritionOverview;

    /**
     * 推荐统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendStatistics implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 推荐菜品数
         */
        private Integer totalRecipes;

        /**
         * 预估总成本
         */
        private BigDecimal estimatedTotalCost;

        /**
         * 人均成本
         */
        private BigDecimal perCapitaCost;

        /**
         * 预算结余
         */
        private BigDecimal budgetRemaining;

        /**
         * 平均营养评分
         */
        private BigDecimal avgNutritionScore;
    }

    /**
     * 预算信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 预算金额
         */
        private BigDecimal budgetAmount;

        /**
         * 已使用金额
         */
        private BigDecimal usedAmount;

        /**
         * 剩余金额
         */
        private BigDecimal remainingAmount;

        /**
         * 使用百分比
         */
        private BigDecimal usedPercentage;

        /**
         * 预算状态：within/near/exceeded
         */
        private String status;

        /**
         * 预算建议
         */
        private String suggestion;
    }

    /**
     * 营养总览
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NutritionOverview implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 总蛋白质（g）
         */
        private BigDecimal totalProtein;

        /**
         * 总碳水化合物（g）
         */
        private BigDecimal totalCarbohydrate;

        /**
         * 总脂肪（g）
         */
        private BigDecimal totalFat;

        /**
         * 总热量（千卡）
         */
        private BigDecimal totalCalories;

        /**
         * 人均蛋白质（g）
         */
        private BigDecimal avgProtein;

        /**
         * 人均碳水化合物（g）
         */
        private BigDecimal avgCarbohydrate;

        /**
         * 人均脂肪（g）
         */
        private BigDecimal avgFat;

        /**
         * 人均热量（千卡）
         */
        private BigDecimal avgCalories;
    }

    /**
     * 周计划VO（用于月计划）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyPlanVO implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 周次
         */
        private Integer weekNumber;

        /**
         * 开始日期
         */
        private String startDate;

        /**
         * 结束日期
         */
        private String endDate;

        /**
         * 每日计划
         */
        private List<DailyPlanVO> dailyPlans;

        /**
         * 周成本
         */
        private BigDecimal weeklyCost;

        /**
         * 周平均营养评分
         */
        private Integer weeklyNutritionScore;

        /**
         * 推荐理由
         */
        private String recommendation;
    }

    /**
     * 每日计划VO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPlanVO implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 日期
         */
        private String date;

        /**
         * 星期几
         */
        private String dayOfWeek;

        /**
         * 餐次
         */
        private String mealType;

        /**
         * 餐次名称
         */
        private String mealTypeName;

        /**
         * 菜谱列表
         */
        private List<RecipeVO> recipes;

        /**
         * 当日成本
         */
        private BigDecimal dailyCost;

        /**
         * 当日营养评分
         */
        private Integer dailyNutritionScore;

        /**
         * 推荐理由
         */
        private String recommendation;
    }
}
