package com.xykj.recipe.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 复制菜谱计划结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopyPlanResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 新计划ID */
    private Long newPlanId;

    /** 新计划编号 */
    private String newPlanCode;

    /** 是否存在异常（如菜谱已停用/已删除） */
    private boolean hasAnomalies;

    /** 失效菜谱数量 */
    private int invalidRecipeCount;

    /** 异常明细列表 */
    private List<RecipeAnomalyItem> anomalyItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecipeAnomalyItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 菜谱ID */
        private Long recipeId;

        /** 菜谱名称 */
        private String recipeName;

        /** 异常类型: deleted / disabled */
        private String anomalyType;

        /** 异常描述 */
        private String anomalyMessage;
    }
}
