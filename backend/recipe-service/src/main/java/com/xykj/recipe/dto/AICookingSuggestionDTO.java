package com.xykj.recipe.dto;

import lombok.Data;

import java.util.List;

/**
 * AI烹饪参数建议请求DTO
 */
@Data
public class AICookingSuggestionDTO {

    /**
     * 菜谱名称
     */
    private String menuName;

    /**
     * 制作步骤
     */
    private String cookingSteps;

    /**
     * 食材列表
     */
    private List<IngredientInfo> ingredients;

    /**
     * 食材信息
     */
    @Data
    public static class IngredientInfo {
        private String materialName;
    }
}
