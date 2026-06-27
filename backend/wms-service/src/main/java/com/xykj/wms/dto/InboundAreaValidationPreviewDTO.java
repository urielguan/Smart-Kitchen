package com.xykj.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class InboundAreaValidationPreviewDTO implements Serializable {

    private Long warehouseId;

    @Valid
    @NotEmpty(message = "入库明细不能为空")
    private List<Item> items;

    @Data
    public static class Item implements Serializable {
        private String lineKey;
        private Long warehouseId;

        @NotNull(message = "仓位不能为空")
        private Long locationId;

        @NotNull(message = "物料不能为空")
        private Long materialId;

        @NotNull(message = "数量不能为空")
        @DecimalMin(value = "0.001", message = "数量必须大于0")
        private BigDecimal quantity;
    }
}
