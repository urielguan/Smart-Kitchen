package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购计划明细返回
 */
@Data
public class PurchasePlanItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long materialId;
    private String materialName;
    private String materialSpec;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private BigDecimal orderedQuantity;
    private String remark;
}
