package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购订单可关联计划明细
 */
@Data
public class PurchaseOrderPlanItemOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planId;
    private String planNo;
    private String planName;
    private String planOrgName;
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal planQuantity;
    private BigDecimal orderedQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal unitPrice;
    private String remark;
}
