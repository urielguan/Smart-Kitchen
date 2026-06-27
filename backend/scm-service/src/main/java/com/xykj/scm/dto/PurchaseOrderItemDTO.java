package com.xykj.scm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购订单明细入参
 */
@Data
public class PurchaseOrderItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long planItemId;

    @NotNull(message = "物料不能为空")
    private Long materialId;

    private String spec;

    @NotNull(message = "订购数量不能为空")
    @DecimalMin(value = "0.001", message = "订购数量必须大于0")
    private BigDecimal quantity;

    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0", inclusive = true, message = "单价不能小于0")
    private BigDecimal unitPrice;

    private String remark;
}
