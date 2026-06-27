package com.xykj.cook.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 烹饪首页看板VO
 */
@Data
@Builder
public class CookDashboardVO {

    /**
     * 总菜品数
     */
    private Long totalDishes;

    /**
     * 待开始数
     */
    private Long pendingCount;

    /**
     * 进行中数
     */
    private Long inProgressCount;

    /**
     * 已完成数
     */
    private Long completedCount;

    /**
     * 温度异常数
     */
    private Long abnormalTemperatureCount;

    /**
     * 完成率
     */
    private BigDecimal completionRate;

    /**
     * 超时任务数（实际时长 > 标准时长）
     */
    private Long durationAbnormalCount;
}
