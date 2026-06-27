package com.xykj.scm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 采购计划关联生成采购订单入参
 */
@Data
public class PurchasePlanGenerateOrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    @Valid
    @NotEmpty(message = "请选择至少一条生成明细")
    private List<Item> items;

    @Data
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotNull(message = "计划明细ID不能为空")
        private Long planItemId;

        @NotNull(message = "生成数量不能为空")
        @DecimalMin(value = "0.001", message = "生成数量必须大于0")
        private BigDecimal quantity;

        @DecimalMin(value = "0", message = "采购单价不能小于0")
        private BigDecimal unitPrice;

        @DecimalMin(value = "0", message = "小计不能小于0")
        private BigDecimal subtotal;
    }
}
