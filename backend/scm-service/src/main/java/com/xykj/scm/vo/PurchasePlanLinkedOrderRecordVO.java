package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购计划关联采购订单明细记录
 */
@Data
public class PurchasePlanLinkedOrderRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;
    private String orderNo;
    private String status;
    private String materialName;
    private String materialSpec;
    private String unit;
    private BigDecimal quantity;
    private String operatorName;
    private String createdAt;
}
