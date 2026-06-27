package com.xykj.sys.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 监管看板首页聚合数据VO
 */
@Data
public class RegulatoryDashboardDataVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String snapshotAt;
    private String lastUpdatedAt;
    private List<OverviewMetric> overviewMetrics;
    private List<DomainSection> domainSections;
    private List<RiskEvent> riskEvents;
    private List<TrendPoint> trendSeries;
    private List<DistributionItem> alarmDistribution;
    private List<BarItem> executionSeries;
    private List<QualityMetric> serviceQuality;
    private List<HeatCard> heatCards;
    private List<ReportTemplate> reportTemplates;
    private List<ExternalShare> externalShares;
    private List<ApiSubscription> apiSubscriptions;

    @Data
    public static class OverviewMetric implements Serializable {
        private String id;
        private String title;
        private String value;
        private String unit;
        private String compare;
        private String status;
        private String source;
    }

    @Data
    public static class DomainSection implements Serializable {
        private String title;
        private String subtitle;
        private List<DomainMetric> metrics;
    }

    @Data
    public static class DomainMetric implements Serializable {
        private String name;
        private String value;
        private String hint;
        private String status;
    }

    @Data
    public static class RiskEvent implements Serializable {
        private String id;
        private String type;
        private String title;
        private String traceBatchId;
        private String level;
        private String location;
        private String time;
        private String status;
        private String owner;
        private String sourceModule;
        private List<String> sourceTerminals;
        private String consistency;
        private boolean overtime;
        private DrillDown drillDown;
    }

    @Data
    public static class DrillDown implements Serializable {
        private String type;
        private Long recordId;
        private String traceBatchId;
        private Long alertId;
        private Long dispatchId;
        private String alertNo;
        private String dispatchNo;
        private String metric;
        private String tab;
        private String status;
        private String alertLevel;
        private Boolean overdue;
    }

    @Data
    public static class TrendPoint implements Serializable {
        private String label;
        private Integer alarm;
        private Integer review;
    }

    @Data
    public static class DistributionItem implements Serializable {
        private String name;
        private Integer value;
    }

    @Data
    public static class BarItem implements Serializable {
        private String label;
        private Integer morningCheck;
        private Integer cooking;
    }

    @Data
    public static class QualityMetric implements Serializable {
        private String label;
        private String value;
        private String compare;
        private String target;
        private String status;
    }

    @Data
    public static class HeatCard implements Serializable {
        private String name;
        private String level;
        private String value;
        private String status;
    }

    @Data
    public static class ReportTemplate implements Serializable {
        private String name;
        private String scope;
        private String updatedAt;
    }

    @Data
    public static class ExternalShare implements Serializable {
        private String target;
        private String mode;
        private String expireAt;
        private String status;
    }

    @Data
    public static class ApiSubscription implements Serializable {
        private String app;
        private String path;
        private String limit;
        private String status;
    }
}
