package com.xykj.recipe.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 烹饪首页看板VO
 */
@Data
@Builder
public class CookDashboardVO {

    private Long totalDishes;
    private Long pendingCount;
    private Long inProgressCount;
    private Long completedCount;
    private Long abnormalTemperatureCount;
    private BigDecimal completionRate;
}
