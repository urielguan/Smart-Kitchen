package com.xykj.wms.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class InboundSourceOrderOptionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long supplierId;
    private String supplierName;
    private Long orgId;
    private String orgName;
    private BigDecimal availableQuantity;
    private BigDecimal linkedQuantity;
}
