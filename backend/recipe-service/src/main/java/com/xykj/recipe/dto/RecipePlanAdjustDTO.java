package com.xykj.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 菜谱计划调整申请DTO
 */
@Data
public class RecipePlanAdjustDTO {

    /**
     * 调整类型：add/remove/replace
     */
    @NotBlank(message = "调整类型不能为空")
    private String adjustType;

    /**
     * 调整原因
     */
    @NotBlank(message = "调整原因不能为空")
    private String reason;

    /**
     * 调整餐次信息
     */
    @NotEmpty(message = "请至少添加一个餐次")
    private List<MealAdjustInfo> meals;

    /**
     * 餐次调整信息
     */
    @Data
    public static class MealAdjustInfo {
        /**
         * 餐次：breakfast/lunch/dinner
         */
        @NotBlank(message = "餐次不能为空")
        private String mealType;

        /**
         * 用餐人数
         */
        private Integer diners;

        /**
         * 菜谱ID列表
         */
        private List<Long> recipeIds;
    }
}
