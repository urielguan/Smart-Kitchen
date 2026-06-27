package com.xykj.recipe.dto;

import lombok.Data;

/**
 * 菜谱计划导入明细DTO（Sheet2 行数据）
 */
@Data
public class RecipePlanItemImportDTO {
    /** 计划序号（关联 Sheet1 的序号） */
    private Integer planSeqNo;
    /** 菜谱编码（必填，对应 recipe.recipe_code） */
    private String recipeCode;
    /** 菜谱名称（展示用） */
    private String recipeName;
    /** 计划份数 */
    private String plannedServings;
    /** 排序（可选） */
    private String sortOrder;
    /** 备注 */
    private String remark;
}
