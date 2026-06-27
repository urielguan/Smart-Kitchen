package com.xykj.scm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购需求预测明细实体
 */
@Data
@TableName("scm_purchase_demand_forecast_item")
public class PurchaseDemandForecastItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long forecastId;
    private Long materialId;
    private String materialName;
    private String materialSpec;
    private String materialUnit;
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
    private BigDecimal estimatedUnitPrice;
    private BigDecimal estimatedAmount;
    private String replenishmentPriority;
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
    private Integer manualReviewRequired;
    private String explanationFactorsJson;
    private String anomalyCodes;
    private BigDecimal explanationSortScore;
    private String anomalyFlags;
    private BigDecimal actualConsumptionQty;
    private BigDecimal absError;
    private BigDecimal ape;
    private BigDecimal rmse;
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
    private Integer orderNow;
    private String orderAction;
    private BigDecimal shortageCost;
    private BigDecimal holdingCost;
    private BigDecimal expiryRiskCost;
    private BigDecimal orderProcessingCost;
    private BigDecimal purchasePriceCost;
    private BigDecimal totalCost;
    private String phaseThreeRiskFlags;
    private String evaluationStatus;
    private Integer sortOrder;
    private Long orgId;
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
