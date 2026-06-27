package com.xykj.device.vo;

import lombok.Data;

/**
 * 录像回放统计VO
 * 统计所有符合筛选条件的录像汇总数据，而非仅当前页
 */
@Data
public class RecordingStatisticsVO {

    /** 录像总数 */
    private Long totalCount;

    /** 总存储大小（字节） */
    private Long totalFileSize;

    /** 告警录像数量 */
    private Long alarmCount;

    /** AI标记录像数量 */
    private Long aiMarkCount;
}