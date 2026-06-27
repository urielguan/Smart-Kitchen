package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购需求预测返回
 */
@Data
public class PurchaseDemandForecastVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String forecastNo;
    private String forecastName;
    private Long orgId;
    private String orgName;
    private String dimension;
    private Integer forecastDays;
    private String basisDate;
    private String horizonStartDate;
    private String horizonEndDate;
    private Integer materialCount;
    private BigDecimal totalSuggestedAmount;
    private BigDecimal calendarFactor;
    private BigDecimal holidayFactor;
    private BigDecimal activityFactor;
    private String summaryBasis;
    private String generatedBy;
    private String generatedAt;
    private String materialPlanStatus;
    private String evaluationStatus;
    private String evaluatedAt;
    private BigDecimal wape;
    private BigDecimal mape;
    private BigDecimal biasRate;
    private BigDecimal stockoutRate;
    private BigDecimal oversupplyRate;
    private Integer optimizationVersion;
    private BigDecimal optimizationScore;
    private String explanationSummary;
    private String approvalSummary;
    private Integer reorderTriggeredCount;
    private Integer riskItemCount;
    private Integer supplierRecommendedCount;
    private Integer manualReviewCount;
    private Integer warningItemCount;
    private BigDecimal totalOptimizationCost;
    private List<PurchaseDemandForecastItemVO> items = new ArrayList<>();
}
