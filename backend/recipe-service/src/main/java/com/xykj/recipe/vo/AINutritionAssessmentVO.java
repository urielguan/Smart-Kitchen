package com.xykj.recipe.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI营养评估结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AINutritionAssessmentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // 人群膳食画像类型常量
    public static final String PORTRAIT_ELDERLY = "elderly";      // 老人
    public static final String PORTRAIT_CHILD = "child";          // 儿童
    public static final String PORTRAIT_PATIENT = "patient";      // 病人
    public static final String PORTRAIT_GENERAL = "general";      // 普通成人
    public static final String PORTRAIT_TEENAGER = "teenager";    // 青少年
    public static final String PORTRAIT_WORKER = "worker";        // 体力劳动者

    // 营养等级常量
    public static final String GRADE_NEEDS_IMPROVEMENT = "needs_improvement";  // 需改进
    public static final String GRADE_GOOD = "good";                            // 良好
    public static final String GRADE_ADEQUATE = "adequate";                    // 达标
    public static final String GRADE_BALANCED = "balanced";                    // 均衡(达标别名)
    public static final String GRADE_EXCESSIVE = "excessive";                  // 过量

    // 对比状态常量
    public static final String STATUS_INSUFFICIENT = "insufficient";  // 不足
    public static final String STATUS_ADEQUATE = "adequate";          // 达标
    public static final String STATUS_EXCESSIVE = "excessive";        // 过量

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 画像营养分析
     */
    private PortraitInfo portraitInfo;

    /**
     * 所选菜谱营养分析
     */
    private SelectedRecipeNutrition selectedRecipeNutrition;

    /**
     * 营养目标对比列表
     */
    private List<NutritionComparison> nutritionComparison;

    /**
     * 营养均衡度评分
     */
    private NutritionBalanceScore nutritionBalanceScore;

    /**
     * AI优化建议
     */
    private String aiOptimizationSuggestions;

    /**
     * AI生成状态：success/not_configured/failed/fallback
     */
    private String aiStatus;

    /**
     * AI状态提示
     */
    private String aiStatusMessage;

    // ============= 兼容旧字段（保持向后兼容） =============

    /**
     * 总体营养评分（0-100）- 兼容字段
     */
    private Integer overallScore;

    /**
     * 营养等级 - 兼容字段
     */
    private String grade;

    /**
     * 营养等级描述 - 兼容字段
     */
    private String gradeDescription;

    /**
     * 评估结论 - 兼容字段
     */
    private String assessment;

    /**
     * 总热量（千卡）- 兼容字段
     */
    private BigDecimal totalCalories;

    /**
     * 总蛋白质（g）- 兼容字段
     */
    private BigDecimal totalProtein;

    /**
     * 总碳水化合物（g）- 兼容字段
     */
    private BigDecimal totalCarbohydrate;

    /**
     * 总脂肪（g）- 兼容字段
     */
    private BigDecimal totalFat;

    /**
     * 总钠（mg）- 兼容字段
     */
    private BigDecimal totalSodium;

    /**
     * 总膳食纤维（g）- 兼容字段
     */
    private BigDecimal totalFiber;

    /**
     * 人均热量（千卡）- 兼容字段
     */
    private BigDecimal avgCalories;

    /**
     * 人均蛋白质（g）- 兼容字段
     */
    private BigDecimal avgProtein;

    /**
     * 人均碳水化合物（g）- 兼容字段
     */
    private BigDecimal avgCarbohydrate;

    /**
     * 人均脂肪（g）- 兼容字段
     */
    private BigDecimal avgFat;

    /**
     * 营养建议列表 - 兼容字段
     */
    private List<String> suggestions;

    /**
     * 营养达标率（%）- 兼容字段
     */
    private BigDecimal passRate;

    /**
     * 饮食结构分析 - 兼容字段
     */
    private DietStructureAnalysis dietStructure;

    /**
     * 营养目标对比列表 - 兼容字段
     */
    private List<NutritionComparison> nutritionComparisons;

    /**
     * 就餐人数 - 兼容字段
     */
    private Integer servingCount;

    /**
     * 人群膳食画像信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortraitInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 人群膳食画像类型：elderly/child/patient/general/teenager/worker
         */
        private String portraitType;

        /**
         * 人群膳食画像名称
         */
        private String portraitName;

        /**
         * 人群营养目标
         */
        private NutritionTargets nutritionTargets;

        /**
         * 饮食限制描述
         */
        private String dietaryRestrictions;
    }

    /**
     * 营养目标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NutritionTargets implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 蛋白质目标（g）
         */
        private BigDecimal proteinTarget;

        /**
         * 碳水目标（g）
         */
        private BigDecimal carbsTarget;

        /**
         * 脂肪目标（g）
         */
        private BigDecimal fatTarget;

        /**
         * 热量目标（kcal）
         */
        private Integer caloriesTarget;

        /**
         * 纤维目标（g）
         */
        private BigDecimal fiberTarget;

        /**
         * 钠目标（mg）
         */
        private BigDecimal sodiumTarget;
    }

    /**
     * 所选菜谱营养分析
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectedRecipeNutrition implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 总蛋白质量（g）
         */
        private BigDecimal totalProtein;

        /**
         * 人均蛋白质量（g）
         */
        private BigDecimal proteinPerCapita;

        /**
         * 总碳水量（g）
         */
        private BigDecimal totalCarbs;

        /**
         * 人均碳水量（g）
         */
        private BigDecimal carbsPerCapita;

        /**
         * 总脂肪量（g）
         */
        private BigDecimal totalFat;

        /**
         * 人均脂肪量（g）
         */
        private BigDecimal fatPerCapita;

        /**
         * 总热量（kcal）
         */
        private Integer totalCalories;

        /**
         * 人均热量（kcal）
         */
        private Integer caloriesPerCapita;

        /**
         * 总纤维量（g）
         */
        private BigDecimal totalFiber;

        /**
         * 人均纤维量（g）
         */
        private BigDecimal fiberPerCapita;

        /**
         * 总钠量（mg）
         */
        private BigDecimal totalSodium;

        /**
         * 人均钠量（mg）
         */
        private BigDecimal sodiumPerCapita;
    }

    /**
     * 营养均衡度评分
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NutritionBalanceScore implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 分数（0-100）
         */
        private Integer score;

        /**
         * 等级：needs_improvement/good/adequate/excessive
         */
        private String grade;

        /**
         * 等级描述
         */
        private String gradeDescription;

        /**
         * 各维度评分
         */
        private List<DimensionScore> dimensionScores;
    }

    /**
     * 维度评分
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionScore implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 维度名称：蛋白质/碳水化合物/脂肪/热量/纤维/钠
         */
        private String dimensionName;

        /**
         * 评分（0-100）
         */
        private Integer score;

        /**
         * 状态：insufficient/adequate/excessive
         */
        private String status;

        /**
         * 说明
         */
        private String description;
    }

    /**
     * 饮食结构分析
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DietStructureAnalysis implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 蛋白质占比（%）
         */
        private BigDecimal proteinRatio;

        /**
         * 碳水化合物占比（%）
         */
        private BigDecimal carbohydrateRatio;

        /**
         * 脂肪占比（%）
         */
        private BigDecimal fatRatio;

        /**
         * 结构评价
         */
        private String evaluation;
    }

    /**
     * 营养目标对比
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NutritionComparison implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 营养名称
         */
        private String nutrientName;

        /**
         * 人均营养量（g）
         */
        private BigDecimal perCapitaAmount;

        /**
         * 人均营养量（兼容旧字段）
         */
        private BigDecimal actualValue;

        /**
         * 人群画像目标量（g）
         */
        private BigDecimal targetAmount;

        /**
         * 目标量（兼容旧字段）
         */
        private BigDecimal targetValue;

        /**
         * 对比状态：insufficient/adequate/excessive
         */
        private String comparisonStatus;

        /**
         * 对比状态（兼容旧字段）
         */
        private String status;

        /**
         * 达标百分比
         */
        private BigDecimal percentage;
    }
}
