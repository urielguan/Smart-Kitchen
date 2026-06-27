package com.xykj.recipe.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 菜谱计划创建DTO
 */
@Data
public class RecipePlanCreateDTO {

    /**
     * 计划日期
     */
    private LocalDate planDate;

    /**
     * 实施开始日期
     */
    private LocalDate startDate;

    /**
     * 实施结束日期
     */
    private LocalDate endDate;

    /**
     * 餐次: breakfast=早餐, lunch=午餐, dinner=晚餐
     */
    private String mealType;

    /**
     * 就餐人数
     */
    private Integer expectedCount;

    private List<MealScheduleDTO> mealSchedules;

    /**
     * 菜谱列表
     */
    private List<RecipePlanItemDTO> recipes;

    /**
     * 是否使用AI推荐菜谱
     */
    private Boolean useAiRecommend;

    /**
     * 是否使用AI营养分析
     */
    private Boolean useAiNutrition;

    /**
     * AI营养评估结果（JSON）
     */
    private String aiNutritionAssessment;

    /**
     * 备注
     */
    private String remark;

    /**
     * 目标人群类型: adult=普通成人, elderly=老人, child=儿童, patient=病人
     */
    private String targetGroup;

    /**
     * 健康状况标签(逗号分隔): diabetes=糖尿病, hypertension=高血压, hyperlipidemia=高血脂, obesity=肥胖
     */
    private String healthStatus;

    /**
     * 饮食限制描述
     */
    private String dietRestrictions;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 菜谱计划明细DTO
     */
    @Data
    public static class RecipePlanItemDTO {
        /**
         * 菜谱ID
         */
        private Long recipeId;

        /**
         * 计划份数
         */
        private Integer plannedServings;

        /**
         * 排序
         */
        private Integer sortOrder;

        /**
         * 备注
         */
        private String remark;
    }

    @Data
    public static class MealScheduleDTO {
        private String mealKey;
        private String mealType;
        private String mealName;
        private Integer expectedCount;
        private Integer sortOrder;
        private List<RecipePlanItemDTO> recipes;
    }
}
