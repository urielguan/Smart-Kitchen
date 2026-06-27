package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购订单明细返回
 */
@Data
public class PurchaseOrderItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planItemId;
    private Long planId;
    private String planNo;
    private String planName;
    private String planOrgName;
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal unitCost;
    private BigDecimal subtotal;
    private BigDecimal receivedQty;
    private BigDecimal inboundQty;
    private BigDecimal remainingInboundQty;
    private String remark;
}
