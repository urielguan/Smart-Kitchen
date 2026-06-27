package com.xykj.device.vo;

import lombok.Data;

/**
 * AI违规统计VO
 */
@Data
public class ViolationStatisticsVO {

    /** 总数 */
    private Long totalCount;

    /** 待处理数 */
    private Long pendingCount;

    /** 紧急数 */
    private Long urgentCount;

    /** 已处理数 */
    private Long resolvedCount;

    /** 今日新增数 */
    private Long todayCount;

    /** 本周新增数 */
    private Long weekCount;

    /** 本月新增数 */
    private Long monthCount;
}
