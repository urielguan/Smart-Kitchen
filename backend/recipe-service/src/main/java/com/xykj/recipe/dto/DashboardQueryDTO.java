package com.xykj.recipe.dto;

import lombok.Data;

/**
 * 数据看板查询DTO
 */
@Data
public class DashboardQueryDTO {

    /**
     * 时间范围: today/week/month
     */
    private String timeRange = "today";
}
