package com.xykj.device.vo;

import lombok.Data;

import java.util.List;

/**
 * 告警管理首页看板VO
 */
@Data
public class AlertDashboardVO {

    /** 总告警数 */
    private Integer totalCount;

    /** 待处理数量 */
    private Integer pendingCount;

    /** 紧急告警数量 */
    private Integer criticalCount;

    /** 严重告警数量 */
    private Integer errorCount;

    /** 警告数量 */
    private Integer warningCount;

    /** 提示数量 */
    private Integer infoCount;

    /** 已指派数量 */
    private Integer assignedCount;

    /** 已关闭数量 */
    private Integer closedCount;

    /** 已处置数量 */
    private Integer handledCount;

    /** 已复核数量 */
    private Integer reviewedCount;

    /** 告警类型统计 */
    private List<AlertTypeStats> alertTypeStats;

    /** 告警趋势（最近7天） */
    private List<AlertTrend> alertTrends;

    @Data
    public static class AlertTypeStats {
        /** 告警类型 */
        private String alertType;
        /** 告警类型名称 */
        private String alertTypeName;
        /** 数量 */
        private Integer count;
    }

    @Data
    public static class AlertTrend {
        /** 日期 */
        private String date;
        /** 数量 */
        private Integer count;
    }
}
