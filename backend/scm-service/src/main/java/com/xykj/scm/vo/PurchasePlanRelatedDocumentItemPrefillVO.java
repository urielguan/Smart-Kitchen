package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购计划关联单据物料回填项
 */
@Data
public class PurchasePlanRelatedDocumentItemPrefillVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long sourceForecastDetailId;
    private Long materialId;
    private String materialName;
    private String materialSpec;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
}
