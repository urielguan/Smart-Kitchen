package com.xykj.recipe.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 营养分析请求DTO
 */
@Data
public class NutritionAnalysisDTO {

    /**
     * 菜谱营养输入列表
     */
    @NotEmpty(message = "菜谱列表不能为空")
    private List<RecipeNutritionInput> recipes;

    /**
     * 就餐人数
     */
    @NotNull(message = "就餐人数不能为空")
    @Min(value = 1, message = "就餐人数必须大于0")
    private Integer servingCount;

    /**
     * 目标人群
     */
    private String targetGroup;

    /**
     * 健康状况（逗号分隔）
     */
    private String healthStatus;

    /**
     * 菜谱营养输入
     */
    @Data
    public static class RecipeNutritionInput {
        /**
         * 菜谱ID
         */
        @NotNull(message = "菜谱ID不能为空")
        private Long recipeId;

        /**
         * 份数
         */
        @NotNull(message = "份数不能为空")
        @Min(value = 1, message = "份数必须大于0")
        private Integer servings;
    }
}
