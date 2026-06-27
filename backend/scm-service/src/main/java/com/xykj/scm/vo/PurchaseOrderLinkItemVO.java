package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购订单关联明细
 */
@Data
public class PurchaseOrderLinkItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planItemId;
    private String materialName;
    private String materialSpec;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String remark;
}
