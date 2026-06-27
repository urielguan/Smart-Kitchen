package com.xykj.wms.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购订单物料明细（供入库单自动带出使用）
 */
@Data
public class PurchaseOrderItemForInboundVO {

    private Long materialId;

    private String materialName;

    private String spec;

    private String unit;

    private BigDecimal unitPrice;

    /** 订单数量 */
    private BigDecimal orderQty;

    /** 已占用数量（已完成 + 草稿/待审核/已审核占用） */
    private BigDecimal inboundQty;

    /** 当前可入库数量 */
    private BigDecimal remainingInboundQty;
}
