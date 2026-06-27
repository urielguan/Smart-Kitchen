package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 可供采购订单关联的采购计划
 */
@Data
public class SelectablePurchasePlanVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String planNo;
    private String planName;
    private Long orgId;
    private String orgName;
    private BigDecimal remainingQuantity;
    private BigDecimal remainingAmount;
}
