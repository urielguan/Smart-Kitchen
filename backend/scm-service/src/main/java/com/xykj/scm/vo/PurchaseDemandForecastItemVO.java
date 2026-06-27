package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购需求预测明细返回
 */
@Data
public class PurchaseDemandForecastItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long materialId;
    private String materialName;
    private String materialSpec;
    private String unit;
    private BigDecimal currentInventoryQty;
    private BigDecimal historicalPlanAvgQty;
    private BigDecimal historicalOrderAvgQty;
    private BigDecimal recipeDemandQty;
    private BigDecimal forecastDemandQty;
    private BigDecimal safetyStockQty;
    private BigDecimal avgDailyDemandQty;
    private Integer reviewPeriodDays;
    private BigDecimal reorderPointQty;
    private BigDecimal targetStockQty;
    private BigDecimal inventoryPositionQty;
    private BigDecimal theoreticalSuggestedQty;
    private BigDecimal suggestedQty;
    private BigDecimal confidenceLowerQty;
    private BigDecimal confidenceUpperQty;
    private BigDecimal confidenceRate;
    private BigDecimal estimatedUnitPrice;
    private BigDecimal estimatedAmount;
    private String priority;
    private String modelType;
    private String materialSegment;
    private String forecastBasis;
    private String explanationSummary;
    private String explanationDetail;
    private String explanationTemplateCode;
    private String explanationTitle;
    private String warningLevel;
    private String warningMessage;
    private String approvalNote;
    private Boolean manualReviewRequired;
    private String anomalyCodes;
    private BigDecimal explanationSortScore;
    private List<ExplanationFactorVO> explanationFactors = new ArrayList<>();
    private String anomalyFlags;
    private BigDecimal actualConsumptionQty;
    private BigDecimal absError;
    private BigDecimal ape;
    private BigDecimal biasQty;
    private BigDecimal recipeDriveRatio;
    private BigDecimal demandActiveRatio;
    private BigDecimal demandCv;
    private BigDecimal activitySensitivity;
    private BigDecimal serviceLevel;
    private Integer leadTimeDays;
    private BigDecimal effectiveLeadTimeDays;
    private BigDecimal minOrderQty;
    private BigDecimal packSize;
    private BigDecimal maxAllowedStockQty;
    private Integer maxCoverageDays;
    private Long recommendedSupplierId;
    private String recommendedSupplierName;
    private BigDecimal supplierScore;
    private BigDecimal supplierFillRate;
    private BigDecimal supplierOnTimeRate;
    private Boolean orderNow;
    private String orderAction;
    private BigDecimal shortageCost;
    private BigDecimal holdingCost;
    private BigDecimal expiryRiskCost;
    private BigDecimal orderProcessingCost;
    private BigDecimal purchasePriceCost;
    private BigDecimal totalCost;
    private String phaseThreeRiskFlags;
    private String evaluationStatus;
    private BigDecimal occupiedLinkQty;
    private BigDecimal availableLinkQty;
    private String materialPlanStatus;

    @Data
    public static class ExplanationFactorVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String label;
        private String value;
        private String description;
    }
}
