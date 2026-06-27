package com.xykj.recipe.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 菜谱计划食材汇总VO
 */
@Data
public class RecipePlanMaterialSummaryVO {
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal totalQuantity;
    private BigDecimal unitCost;
}
