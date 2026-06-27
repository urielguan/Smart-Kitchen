package com.xykj.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜谱导入失败记录DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeImportFailureDTO {

    /**
     * 行号（Excel中的行号）
     */
    private int rowNum;

    /**
     * 菜谱编码
     */
    private String recipeCode;

    /**
     * 菜谱名称
     */
    private String recipeName;

    /**
     * 失败原因
     */
    private String errorMessage;
}