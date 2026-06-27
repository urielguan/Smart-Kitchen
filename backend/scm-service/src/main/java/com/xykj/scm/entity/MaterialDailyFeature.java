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
 * 物料日特征宽表
 */
@Data
@TableName("scm_material_daily_feature")
public class MaterialDailyFeature implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orgId;
    private Long tenantId;
    private LocalDate statDate;
    private Long materialId;
    private BigDecimal currentStockQty;
    private BigDecimal availableStockQty;

    @TableField("avg_consumption_7d")
    private BigDecimal avgConsumption7d;

    @TableField("avg_consumption_14d")
    private BigDecimal avgConsumption14d;

    @TableField("avg_consumption_30d")
    private BigDecimal avgConsumption30d;

    @TableField("std_consumption_30d")
    private BigDecimal stdConsumption30d;

    @TableField("recipe_demand_7d")
    private BigDecimal recipeDemand7d;

    @TableField("recipe_history_30d")
    private BigDecimal recipeHistory30d;

    @TableField("actual_consumption_30d")
    private BigDecimal actualConsumption30d;

    @TableField("consumption_days_30d")
    private Integer consumptionDays30d;
    private BigDecimal inventoryTurnoverDays;
    private BigDecimal pendingPlanQty;
    private BigDecimal inTransitQty;
    private BigDecimal holidayFactor;
    private BigDecimal activityFactor;
    private String materialCategory;
    private String forecastType;
    private BigDecimal recipeDriveRatio;
    private BigDecimal demandActiveRatio;
    private BigDecimal demandCv;
    private BigDecimal activitySensitivity;
    private String materialSegment;
    private Integer leadTimeDays;
    private BigDecimal serviceLevel;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
