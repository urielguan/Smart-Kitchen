package com.xykj.recipe.dto;

import lombok.Data;

/**
 * 完成烹饪DTO
 */
@Data
public class CookTaskCompleteDTO {

    /**
     * 实际完成份数
     */
    private Integer actualQty;

    /**
     * 完成备注
     */
    private String remark;
}
