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
 * 供应商履约统计表
 */
@Data
@TableName("scm_supplier_delivery_stat")
public class SupplierDeliveryStat implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orgId;
    private Long tenantId;
    private Long supplierId;
    private String supplierName;
    private LocalDate statStartDate;
    private LocalDate statEndDate;
    private Integer sampleCount;
    private BigDecimal avgLeadTimeDays;
    private BigDecimal stdLeadTimeDays;
    private BigDecimal fillRate;
    private BigDecimal onTimeRate;
    private LocalDateTime lastCalculatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
