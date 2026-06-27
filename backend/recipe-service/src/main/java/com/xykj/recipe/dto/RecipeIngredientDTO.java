package com.xykj.recipe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 菜谱食材DTO
 */
@Data
public class RecipeIngredientDTO {

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
     * 备注
     */
    private String remark;
}