package com.xykj.recipe.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜谱更新DTO
 */
@Data
public class RecipeUpdateDTO {

    /**
     * 菜谱名称
     */
    private String menuName;

    /**
     * 菜谱类别编码
     */
    private String menuCategory;

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
     * 制作步骤
     */
    private String cookingSteps;

    /**
     * 是否使用AI建议时长/温度
     */
    private Boolean useAiSuggestion;

    /**
     * 状态: active=启用, inactive=停用
     */
    private String status;

    /**
     * 食材列表
     */
    private List<RecipeIngredientDTO> ingredients;
}