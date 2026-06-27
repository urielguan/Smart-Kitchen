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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购需求预测主表实体
 */
@Data
@TableName("scm_purchase_demand_forecast")
public class PurchaseDemandForecast implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String forecastNo;
    private String forecastName;
    private String forecastDimension;
    private Integer forecastDays;
    private LocalDate basisDate;
    private LocalDate horizonStartDate;
    private LocalDate horizonEndDate;
    private Integer materialCount;
    private BigDecimal totalSuggestedAmount;
    private BigDecimal calendarFactor;
    private BigDecimal holidayFactor;
    private BigDecimal activityFactor;
    private String summaryBasis;
    private String evaluationStatus;
    private LocalDateTime evaluatedAt;
    private BigDecimal overallWape;
    private BigDecimal overallMape;
    private BigDecimal overallRmse;
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
