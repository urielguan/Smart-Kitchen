package com.xykj.wms.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class InventoryShelfLifeSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long materialId;
    private BigDecimal normalQty;
    private BigDecimal warningQty;
    private BigDecimal nearExpiryQty;
    private BigDecimal expiredQty;
    private BigDecimal totalQty;
    private String configStatus;
}
