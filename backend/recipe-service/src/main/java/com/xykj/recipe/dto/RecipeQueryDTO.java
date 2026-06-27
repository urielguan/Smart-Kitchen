package com.xykj.recipe.dto;

import lombok.Data;

import java.util.List;

/**
 * 菜谱查询DTO
 */
@Data
public class RecipeQueryDTO {

    /**
     * 页码（从1开始）
     */
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 20;

    /**
     * 菜谱名称（模糊搜索）
     */
    private String recipeName;

    /**
     * 菜谱编码（精确匹配）
     */
    private String recipeCode;

    /**
     * 菜谱类别ID
     */
    private Long categoryId;

    /**
     * 状态: active=启用, inactive=停用
     */
    private String status;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 数据权限注入组织ID列表
     */
    private List<Long> orgIds;
}
