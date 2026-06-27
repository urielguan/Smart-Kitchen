package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI营养分析结果VO
 */
@Data
public class AINutritionAnalysisVO {

    /**
     * 菜谱ID
     */
    private Long recipeId;

    /**
     * 菜谱名称
     */
    private String recipeName;

    /**
     * 营养成分
     */
    private NutritionInfo nutritionInfo;

    /**
     * 维生素含量
     */
    private VitaminInfo vitaminInfo;

    /**
     * 矿物质含量
     */
    private MineralInfo mineralInfo;

    /**
     * 分析时间
     */
    private LocalDateTime analysisTime;
    private BigDecimal dataCompleteness;
    private Integer missingMaterialCount;
    private List<String> missingMaterials;

    @Data
    public static class NutritionInfo {
        private BigDecimal protein;
        private BigDecimal carbohydrate;
        private BigDecimal fat;
        private Integer calories;
    }

    @Data
    public static class VitaminInfo {
        private BigDecimal vitaminA;
        private BigDecimal vitaminB1;
        private BigDecimal vitaminB2;
        private BigDecimal vitaminC;
    }

    @Data
    public static class MineralInfo {
        private BigDecimal calcium;
        private BigDecimal iron;
        private BigDecimal zinc;
    }
}
