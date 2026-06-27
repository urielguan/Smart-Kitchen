package com.xykj.recipe.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 烹饪任务查询DTO
 */
@Data
public class CookTaskQueryDTO {

    /**
     * 页码
     */
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 20;

    /**
     * 计划日期
     */
    private LocalDate planDate;

    /**
     * 餐次
     */
    private String mealType;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 烹饪人
     */
    private String chefName;

    /**
     * 所属组织ID
     */
    private Long orgId;
}
