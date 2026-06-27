package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购订单统计
 */
@Data
public class PurchaseOrderStatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long total;
    private Long pending;
    private Long approved;
    private BigDecimal totalAmount;
}
