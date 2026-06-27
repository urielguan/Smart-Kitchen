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
 * 采购需求预测任务主表
 */
@Data
@TableName("scm_purchase_demand_forecast_task")
public class PurchaseDemandForecastTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String forecastNo;
    private Long orgId;
    private Long tenantId;
    private String horizonType;
    private LocalDate horizonStartDate;
    private LocalDate horizonEndDate;
    private String taskStatus;
    private Integer materialCount;
    private String triggerType;
    private String evaluationStatus;
    private LocalDateTime evaluatedAt;
    private BigDecimal wape;
    private BigDecimal mape;
    private BigDecimal rmse;
    private BigDecimal biasRate;
    private BigDecimal stockoutRate;
    private BigDecimal oversupplyRate;
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
