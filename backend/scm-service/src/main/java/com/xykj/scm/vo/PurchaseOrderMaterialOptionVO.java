package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购订单物料选项
 */
@Data
public class PurchaseOrderMaterialOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String unit;
    private String spec;
    private BigDecimal referencePrice;
}
