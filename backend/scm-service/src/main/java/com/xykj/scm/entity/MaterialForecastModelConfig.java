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
 * 物料预测模型配置表
 */
@Data
@TableName("scm_material_forecast_model_config")
public class MaterialForecastModelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orgId;
    private Long tenantId;
    private Long materialId;
    private String materialCategory;
    private String materialSegment;
    private String modelType;
    private BigDecimal modelWeight;
    private Integer safetyDays;
    private Integer leadTimeDays;
    private BigDecimal serviceLevel;
    private BigDecimal alphaValue;
    private BigDecimal betaValue;
    private BigDecimal holidayFactor;
    private BigDecimal weekendFactor;
    private BigDecimal activityFactor;
    private Integer maxCoverageDays;
    private BigDecimal recipeCorrectionMin;
    private BigDecimal recipeCorrectionMax;
    private BigDecimal minOrderQty;
    private BigDecimal packSize;
    private BigDecimal leadTimeRiskFactor;
    private BigDecimal shortagePenalty;
    private BigDecimal holdingCostRate;
    private BigDecimal wasteCostRate;
    private BigDecimal orderCost;
    private BigDecimal optimizationScore;
    private Integer versionNo;
    private String sourceType;
    private LocalDate effectiveStartDate;
    private LocalDate effectiveEndDate;
    private String status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
