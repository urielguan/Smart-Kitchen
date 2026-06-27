package com.xykj.recipe.dto;

import lombok.Data;

/**
 * 开始烹饪DTO
 */
@Data
public class CookTaskStartDTO {

    /**
     * 烹饪人ID
     */
    private Long chefId;

    /**
     * 烹饪人姓名
     */
    private String chefName;

    /**
     * 开始备注
     */
    private String remark;
}
