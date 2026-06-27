package com.xykj.scm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购需求预测评估表
 */
@Data
@TableName("scm_purchase_demand_forecast_evaluation")
public class PurchaseDemandForecastEvaluation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orgId;
    private Long tenantId;
    private Long forecastId;
    private Long taskId;
    private Long taskItemId;
    private Long forecastItemId;
    private Long materialId;
    private String materialName;
    private String materialSegment;
    private LocalDate forecastDate;
    private LocalDate horizonStartDate;
    private LocalDate horizonEndDate;
    private BigDecimal predictedQty;
    private BigDecimal actualQty;
    private BigDecimal absError;
    private BigDecimal ape;
    private BigDecimal rmse;
    private BigDecimal biasQty;
    private Integer stockoutDays;
    private BigDecimal oversupplyQty;
    private BigDecimal wapeContribution;
    private String modelType;
    private String actualSource;
    private String evaluationStatus;
    private LocalDateTime evaluatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
