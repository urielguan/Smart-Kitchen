package com.xykj.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 菜谱类别创建/更新DTO
 */
@Data
public class RecipeCategoryCreateDTO {

    /**
     * 类别编码
     */
    @NotBlank(message = "类别编码不能为空")
    private String categoryCode;

    /**
     * 类别名称
     */
    @NotBlank(message = "类别名称不能为空")
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
     * 备注
     */
    private String remark;
}
