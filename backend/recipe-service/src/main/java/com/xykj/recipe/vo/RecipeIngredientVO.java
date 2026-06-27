package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 菜谱食材VO
 */
@Data
public class RecipeIngredientVO {

    /**
     * 配比ID
     */
    private Long id;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 物料规格
     */
    private String materialSpec;

    /**
     * 用量
     */
    private BigDecimal quantity;

    /**
     * 单位
     */
    private String unit;

    /**
     * 是否主料
     */
    private Boolean isMain;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 蛋白质（g）
     */
    private BigDecimal protein;

    /**
     * 碳水化合物（g）
     */
    private BigDecimal carbohydrate;

    /**
     * 脂肪（g）
     */
    private BigDecimal fat;

    /**
     * 标准食品ID
     */
    private Long foodItemId;

    /**
     * 标准食品名称
     */
    private String foodItemName;

    /**
     * 营养来源类型
     */
    private String nutritionSourceType;

    /**
     * 营养计算口径用量（克）
     */
    private BigDecimal quantityInGram;

    /**
     * 每100g热量
     */
    private BigDecimal caloriesPer100g;

    /**
     * 每100g蛋白质
     */
    private BigDecimal proteinPer100g;

    /**
     * 每100g碳水
     */
    private BigDecimal carbohydratePer100g;

    /**
     * 每100g脂肪
     */
    private BigDecimal fatPer100g;

    /**
     * 每100g钠
     */
    private BigDecimal sodiumPer100g;

    /**
     * 每100g膳食纤维
     */
    private BigDecimal fiberPer100g;

    /**
     * 备注
     */
    private String remark;
}
