package com.xykj.sample.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 留样看板VO
 */
@Data
@Builder
public class SampleDashboardVO {

    /** 总留样数 */
    private Long totalSamples;

    /** 待销样数 */
    private Long pendingDisposal;

    /** 已销样数 */
    private Long disposed;

    /** 超期未销数 */
    private Long overdue;

    /** 今日新增留样 */
    private Long todaySampled;
}
