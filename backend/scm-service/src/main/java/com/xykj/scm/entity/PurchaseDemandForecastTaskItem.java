package com.xykj.scm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购需求预测任务明细表
 */
@Data
@TableName("scm_purchase_demand_forecast_task_item")
public class PurchaseDemandForecastTaskItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private Long materialId;
    private String materialName;
    private String materialUnit;
    private String materialCategory;
    private String materialSegment;
    private BigDecimal forecastDemandQty;
    private BigDecimal safetyStockQty;
    private BigDecimal avgDailyDemandQty;
    private Integer reviewPeriodDays;
    private BigDecimal reorderPointQty;
    private BigDecimal targetStockQty;
    private BigDecimal currentStockQty;
    private BigDecimal availableStockQty;
    private BigDecimal inventoryPositionQty;
    private BigDecimal inTransitQty;
    private BigDecimal pendingExecQty;
    private BigDecimal theoreticalSuggestedQty;
    private BigDecimal suggestedPurchaseQty;
    private BigDecimal lowerBoundQty;
    private BigDecimal upperBoundQty;
    private BigDecimal confidenceLevel;
    private String forecastBasis;
    private String priorityLevel;
    private String modelType;
    private String featureSnapshotJson;
    private String modelParamJson;
    private String explanationSummary;
    private String explanationDetail;
    private String explanationTemplateCode;
    private String explanationTitle;
    private String warningLevel;
    private String warningMessage;
    private String approvalNote;
    private Integer manualReviewRequired;
    private String explanationFactorsJson;
    private String anomalyCodes;
    private BigDecimal explanationSortScore;
    private String anomalyFlags;
    private BigDecimal estimatedUnitPrice;
    private BigDecimal actualConsumptionQty;
    private BigDecimal absError;
    private BigDecimal ape;
    private BigDecimal rmse;
    private BigDecimal biasQty;
    private Integer stockoutDays;
    private BigDecimal oversupplyQty;
    private String evaluationStatus;
    private LocalDateTime evaluatedAt;
    private BigDecimal recipeDriveRatio;
    private BigDecimal demandActiveRatio;
    private BigDecimal demandCv;
    private BigDecimal activitySensitivity;
    private Integer leadTimeDays;
    private BigDecimal serviceLevel;
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
    private Integer orderNow;
    private String orderAction;
    private BigDecimal shortageCost;
    private BigDecimal holdingCost;
    private BigDecimal expiryRiskCost;
    private BigDecimal orderProcessingCost;
    private BigDecimal purchasePriceCost;
    private BigDecimal totalCost;
    private String phaseThreeRiskFlags;
    private Integer optimizationVersion;
    private BigDecimal optimizationScore;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
