package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 菜谱统计VO
 */
@Data
public class RecipeStatisticsVO {

    /**
     * 菜谱总数
     */
    private Integer totalRecipes;

    /**
     * 启用菜谱数
     */
    private Integer activeRecipes;

    /**
     * 停用菜谱数
     */
    private Integer inactiveRecipes;

    /**
     * 本周新增菜谱数
     */
    private Integer weeklyNewRecipes;

    /**
     * 本月新增菜谱数
     */
    private Integer monthlyNewRecipes;

    /**
     * 食材覆盖率（%）
     */
    private BigDecimal ingredientCoverage;

    /**
     * 营养达标率（%）
     */
    private BigDecimal nutritionPassRate;

    /**
     * 平均营养评分
     */
    private BigDecimal avgNutritionScore;

    /**
     * 营养素分布
     */
    private NutritionDistribution nutritionDistribution;

    /**
     * 类别分布统计
     */
    private List<CategoryStats> categoryDistribution;

    /**
     * 本周热门菜谱TOP5
     */
    private List<HotRecipe> weeklyHotRecipes;

    /**
     * 本月热门菜谱TOP5
     */
    private List<HotRecipe> monthlyHotRecipes;

    /**
     * 菜谱评分分布（1-5星）
     */
    private RatingDistribution ratingDistribution;

    /**
     * 周计划趋势
     */
    private WeeklyTrend weeklyPlanTrend;

    /**
     * 成本分析
     */
    private CostAnalysis costAnalysis;

    /**
     * 营养素分布
     */
    @Data
    public static class NutritionDistribution {
        private BigDecimal proteinPercent;
        private BigDecimal carbsPercent;
        private BigDecimal fatPercent;
        private BigDecimal avgCalories;
        private BigDecimal avgProtein;
        private BigDecimal avgCarbs;
        private BigDecimal avgFat;
    }

    /**
     * 热门菜谱
     */
    @Data
    public static class HotRecipe {
        private Long recipeId;
        private String recipeCode;
        private String recipeName;
        private String recipeCategory;
        private String categoryName;
        private Integer serveCount;
        private Integer viewCount;
        private BigDecimal rating;
        private Integer nutritionScore;
    }

    /**
     * 类别统计
     */
    @Data
    public static class CategoryStats {
        private Long categoryId;
        private String categoryName;
        private String categoryIcon;
        private Integer recipeCount;
        private BigDecimal percentage;
        private Integer avgNutritionScore;
    }

    /**
     * 评分分布
     */
    @Data
    public static class RatingDistribution {
        private Integer fiveStar;
        private Integer fourStar;
        private Integer threeStar;
        private Integer twoStar;
        private Integer oneStar;
        private BigDecimal avgRating;
    }

    /**
     * 周计划趋势
     */
    @Data
    public static class WeeklyTrend {
        /** 每日数据点 */
        private List<TrendPoint> dailyData;
        /** 平均营养评分 */
        private BigDecimal avgNutritionScore;
        /** 计划完成率 */
        private BigDecimal completionRate;
        /** 趋势方向：up/down/stable */
        private String trendDirection;
    }

    /**
     * 趋势数据点
     */
    @Data
    public static class TrendPoint {
        private String date;
        private String dayOfWeek;
        private Integer planCount;
        private Integer recipeCount;
        private BigDecimal avgScore;
    }

    /**
     * 成本分析
     */
    @Data
    public static class CostAnalysis {
        /** 周总成本 */
        private BigDecimal weeklyTotalCost;
        /** 人均成本 */
        private BigDecimal avgCostPerMeal;
        /** 月总成本 */
        private BigDecimal monthlyTotalCost;
        /** 成本趋势 */
        private List<CostTrendPoint> costTrend;
    }

    /**
     * 成本趋势数据点
     */
    @Data
    public static class CostTrendPoint {
        private String date;
        private BigDecimal totalCost;
        private BigDecimal avgCost;
    }
}
