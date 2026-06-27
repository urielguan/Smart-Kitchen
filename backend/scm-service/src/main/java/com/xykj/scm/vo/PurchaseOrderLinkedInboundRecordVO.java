package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购订单关联入库单明细记录
 */
@Data
public class PurchaseOrderLinkedInboundRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long inboundOrderId;
    private String inboundNo;
    private String inboundDate;
    private String status;
    private String postStatus;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal inboundQuantity;
    private String operatorName;
    private String createdAt;
}
