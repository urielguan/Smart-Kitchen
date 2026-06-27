package com.xykj.sys.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class IntegrationOverviewVO {

    private Long enabledIntegrationCount;
    private Long syncSuccessCount;
    private Long syncFailureCount;
    private BigDecimal callbackSuccessRate;
    private BigDecimal averageDurationMs;
    private List<MetricItem> moduleDistribution = new ArrayList<>();
    private List<MetricItem> syncTrend = new ArrayList<>();
    private List<MetricItem> providerFailureDistribution = new ArrayList<>();
    private List<IntegrationSyncLogVO> recentFailedRecords = new ArrayList<>();
    private List<IntegrationSyncLogVO> recentTimeoutRecords = new ArrayList<>();
    private List<IntegrationCallbackLogVO> recentSignFailedRecords = new ArrayList<>();

    @Data
    public static class MetricItem {
        private String code;
        private String label;
        private Long value;
    }
}
