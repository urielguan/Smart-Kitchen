package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜谱详情VO
 */
@Data
public class RecipeDetailVO {

    /**
     * 菜谱ID
     */
    private Long id;

    /**
     * 菜谱编码
     */
    private String recipeCode;

    /**
     * 菜谱名称
     */
    private String recipeName;

    /**
     * 菜谱类别ID
     */
    private Long categoryId;

    /**
     * 菜谱类别名称
     */
    private String categoryName;

    /**
     * 菜谱描述
     */
    private String description;

    /**
     * 菜谱图片URL
     */
    private String imageUrl;

    /**
     * 份量（克）
     */
    private BigDecimal servingSize;

    /**
     * 目标烹饪时长（分钟）
     */
    private Integer targetCookTime;

    /**
     * 最低烹饪温度（℃）
     */
    private Integer targetTempMin;

    /**
     * 最高烹饪温度（℃）
     */
    private Integer targetTempMax;

    /**
     * 制作步骤
     */
    private String cookingSteps;

    /**
     * 单份成本（元）
     */
    private BigDecimal unitCost;

    /**
     * 营养成分
     */
    private NutritionInfoVO nutritionInfo;

    /**
     * 维生素含量
     */
    private VitaminInfoVO vitaminInfo;

    /**
     * 矿物质含量
     */
    private MineralInfoVO mineralInfo;

    /**
     * 营养评分（0-100）
     */
    private Integer nutritionScore;

    /**
     * AI优化建议
     */
    private String aiSuggestions;

    /**
     * 状态
     */
    private String status;

    /**
     * 食材列表
     */
    private List<RecipeIngredientVO> ingredients;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 营养信息VO
     */
    @Data
    public static class NutritionInfoVO {
        private BigDecimal calories;
        private BigDecimal protein;
        private BigDecimal carbohydrate;
        private BigDecimal fat;
        private BigDecimal sodium;
        private BigDecimal fiber;
    }

    /**
     * 维生素信息VO
     */
    @Data
    public static class VitaminInfoVO {
        private BigDecimal vitaminA;
        private BigDecimal vitaminB1;
        private BigDecimal vitaminB2;
        private BigDecimal vitaminC;
        private BigDecimal vitaminD;
        private BigDecimal vitaminE;
    }

    /**
     * 矿物质信息VO
     */
    @Data
    public static class MineralInfoVO {
        private BigDecimal calcium;
        private BigDecimal iron;
        private BigDecimal zinc;
    }
}
