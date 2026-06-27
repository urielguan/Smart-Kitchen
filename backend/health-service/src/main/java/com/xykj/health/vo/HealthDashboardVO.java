package com.xykj.health.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 晨检看板VO
 */
@Data
@Builder
public class HealthDashboardVO {

    /** 今日待晨检人数 */
    private Long pendingCount;

    /** 今日已晨检人数（= normalCount + abnormalCount） */
    private Long completedCount;

    /** 今日正常人数 */
    private Long normalCount;

    /** 今日异常人数 */
    private Long abnormalCount;

    /** 今日晨检总人数（= pendingCount + completedCount） */
    private Long totalChecked;

    /** 今日晨检通过率（百分比，保留1位小数） */
    private java.math.BigDecimal passRate;

    /** 健康证即将过期人数 */
    private Long certificateExpiringCount;

    /** 健康证已过期人数 */
    private Long certificateExpiredCount;
}
