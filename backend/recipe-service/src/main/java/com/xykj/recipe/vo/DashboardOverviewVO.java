package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 数据看板总览VO
 */
@Data
public class DashboardOverviewVO {

    /**
     * 数据生成时间
     */
    private String generatedAt;

    /**
     * 板块指标
     */
    private List<DashboardSectionVO> sections;

    /**
     * 趋势图数据
     */
    private List<DashboardTrendPointVO> trendData;

    /**
     * 计划对比数据
     */
    private List<DashboardCompareItemVO> compareData;

    /**
     * 风险分布数据
     */
    private List<DashboardDistributionItemVO> distributionData;

    @Data
    public static class DashboardSectionVO {
        private String id;
        private String title;
        private List<DashboardMetricVO> metrics;
    }

    @Data
    public static class DashboardMetricVO {
        private String name;
        private BigDecimal value;
        private String unit;
        private String trend;
        private String trendType;
        private Integer score;
    }

    @Data
    public static class DashboardTrendPointVO {
        private String label;
        private BigDecimal value;
    }

    @Data
    public static class DashboardCompareItemVO {
        private String label;
        private Integer planned;
        private Integer actual;
    }

    @Data
    public static class DashboardDistributionItemVO {
        private String label;
        private Integer value;
        private String color;
    }
}
