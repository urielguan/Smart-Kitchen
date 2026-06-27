package com.xykj.scm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购订单明细实体
 */
@Data
@TableName("scm_purchase_order_item")
public class PurchaseOrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long planItemId;
    private Long materialId;
    private String materialName;
    private String materialSpec;
    private String materialUnit;
    private BigDecimal orderQty;
    private BigDecimal receivedQty;
    private BigDecimal inboundQty;
    private BigDecimal remainingInboundQty;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
