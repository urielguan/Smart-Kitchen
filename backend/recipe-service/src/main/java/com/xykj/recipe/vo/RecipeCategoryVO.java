package com.xykj.recipe.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜谱类别VO
 */
@Data
public class RecipeCategoryVO {

    /**
     * 类别ID
     */
    private Long id;

    /**
     * 类别编码
     */
    private String categoryCode;

    /**
     * 类别名称
     */
    private String categoryName;

    /**
     * 类别图标
     */
    private String icon;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 状态: active=启用, inactive=停用
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 菜谱数量
     */
    private Integer recipeCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
