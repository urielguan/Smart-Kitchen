package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜谱列表VO
 */
@Data
public class RecipeVO {

    /**
     * 菜谱ID
     */
    private Long id;

    /**
     * 菜谱编码
     */
    private String menuCode;

    /**
     * 菜谱名称
     */
    private String menuName;

    /**
     * 菜谱类别ID
     */
    private Long categoryId;

    /**
     * 菜谱类别编码
     */
    private String menuCategory;

    /**
     * 菜谱类别名称
     */
    private String categoryName;

    /**
     * 菜谱图片URL
     */
    private String imageUrl;

    /**
     * 烹饪时长（分钟）
     */
    private Integer cookingTime;

    /**
     * 最低烹饪温度（℃）
     */
    private Integer cookingTempMin;

    /**
     * 最高烹饪温度（℃）
     */
    private Integer cookingTempMax;

    /**
     * 营养成分
     */
    private NutritionInfoVO nutritionInfo;

    /**
     * 营养评分（0-100）
     */
    private Integer nutritionScore;

    /**
     * 营养完整度（0-100）
     */
    private BigDecimal dataCompleteness;

    /**
     * 缺失物料映射数
     */
    private Integer missingMaterialCount;

    /**
     * 缺失物料名称
     */
    private List<String> missingMaterials;

    /**
     * 状态
     */
    private String status;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * AI推荐理由
     */
    private String recommendReason;

    /**
     * AI推荐优先级（1-5，5最高）
     */
    private Integer recommendPriority;

    /**
     * 单份成本（元）
     */
    private BigDecimal unitCost;

    /**
     * 预估成本（元）
     */
    private BigDecimal estimatedCost;

    /**
     * 食材列表
     */
    private List<RecipeIngredientVO> ingredients;

    /**
     * 营养信息VO
     */
    @Data
    public static class NutritionInfoVO {
        private BigDecimal protein;
        private BigDecimal carbohydrate;
        private BigDecimal fat;
        private BigDecimal calories;
    }
}
