package com.xykj.recipe.dto;

import lombok.Data;

/**
 * 菜谱类别查询DTO
 */
@Data
public class RecipeCategoryQueryDTO {

    /**
     * 页码（从1开始）
     */
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 20;

    /**
     * 类别名称（模糊查询）
     */
    private String categoryName;

    /**
     * 状态: active=启用, inactive=停用
     */
    private String status;
}
