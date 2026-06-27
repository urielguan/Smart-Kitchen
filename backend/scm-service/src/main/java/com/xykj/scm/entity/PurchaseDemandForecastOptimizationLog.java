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
 * 采购需求预测自动优化日志
 */
@Data
@TableName("scm_purchase_demand_forecast_optimization_log")
public class PurchaseDemandForecastOptimizationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orgId;
    private Long tenantId;
    private Long materialId;
    private String materialCategory;
    private String materialSegment;
    private String modelType;
    private String candidateConfigJson;
    private String selectedConfigJson;
    private BigDecimal score;
    private BigDecimal wape;
    private BigDecimal stockoutRate;
    private BigDecimal oversupplyRate;
    private Integer sampleForecastCount;
    private Integer versionNo;
    private String triggerType;
    private LocalDate effectiveDate;
    private LocalDateTime optimizedAt;
    private Integer rollbackApplied;
    private Integer previousVersionNo;
    private BigDecimal previousScore;
    private String rollbackReason;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
