package com.xykj.scm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购计划明细入参
 */
@Data
public class PurchasePlanItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "物料不能为空")
    private Long materialId;

    private String materialSpec;

    @NotNull(message = "计划数量不能为空")
    @DecimalMin(value = "0.001", message = "计划数量必须大于0")
    private BigDecimal quantity;

    @NotNull(message = "预估单价不能为空")
    @DecimalMin(value = "0", inclusive = true, message = "预估单价不能小于0")
    private BigDecimal unitPrice;

    private String remark;
}
