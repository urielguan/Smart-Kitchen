package com.xykj.recipe.vo;

import lombok.Data;

/**
 * 菜谱计划统计VO
 */
@Data
public class RecipePlanStatisticsVO {

    /** 计划总数 */
    private Long total;

    /** 已审核数 */
    private Long approvedCount;

    /** 待审核数 */
    private Long pendingCount;

    /** 总份数 */
    private Long totalServings;
}
