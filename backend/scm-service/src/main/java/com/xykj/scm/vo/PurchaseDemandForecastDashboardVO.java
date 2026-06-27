package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购需求预测二期看板返回
 */
@Data
public class PurchaseDemandForecastDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orgId;
    private String orgName;
    private Integer pendingEvaluationCount;
    private Integer evaluatedForecastCount;
    private BigDecimal wape;
    private BigDecimal mape;
    private BigDecimal biasRate;
    private BigDecimal stockoutRate;
    private BigDecimal oversupplyRate;
    private Integer optimizedConfigCount;
    private String lastOptimizationAt;
    private String lastEvaluationAt;
    private Integer reorderTriggeredCount;
    private Integer riskItemCount;
    private Integer supplierRecommendedCount;
    private BigDecimal avgOptimizationCost;
    private Integer rollbackCount;
    private String lastRollbackAt;
    private Integer manualReviewCount;
    private Integer warningItemCount;
    private List<SegmentSummary> segmentSummaries = new ArrayList<>();
    private List<OptimizationSummary> optimizationSummaries = new ArrayList<>();
    private List<SupplierSummary> supplierSummaries = new ArrayList<>();

    @Data
    public static class SegmentSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private String materialSegment;
        private String modelType;
        private Integer materialCount;
        private BigDecimal totalSuggestedQty;
        private BigDecimal totalActualQty;
        private BigDecimal totalOptimizationCost;
        private BigDecimal wape;
        private BigDecimal biasRate;
    }

    @Data
    public static class OptimizationSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private String materialSegment;
        private String modelType;
        private Integer versionNo;
        private BigDecimal score;
        private BigDecimal wape;
        private BigDecimal stockoutRate;
        private BigDecimal oversupplyRate;
        private String optimizedAt;
        private Boolean rollbackApplied;
        private String rollbackReason;
    }

    @Data
    public static class SupplierSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long supplierId;
        private String supplierName;
        private Integer recommendCount;
        private BigDecimal avgSupplierScore;
        private BigDecimal avgEffectiveLeadTimeDays;
    }
}
