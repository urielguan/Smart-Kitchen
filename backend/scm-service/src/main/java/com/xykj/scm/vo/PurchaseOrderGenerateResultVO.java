package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 关联生成采购订单结果
 */
@Data
public class PurchaseOrderGenerateResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
}
