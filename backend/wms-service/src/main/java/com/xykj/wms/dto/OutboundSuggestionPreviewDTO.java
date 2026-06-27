package com.xykj.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class OutboundSuggestionPreviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long orderId;

    @Size(max = 32)
    private String warehouseScopeType;

    @Valid
    @NotEmpty(message = "建议明细不能为空")
    private List<DetailDTO> details;

    @Data
    public static class DetailDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long detailId;

        @NotNull(message = "行号不能为空")
        private Integer lineNo;

        @NotNull(message = "物料ID不能为空")
        private Long materialId;

        @Size(max = 100)
        private String materialName;

        @Size(max = 100)
        private String specName;

        @NotNull(message = "需求数量不能为空")
        @DecimalMin(value = "0.001", message = "需求数量必须大于0")
        private BigDecimal requestQty;

        private Long fixedWarehouseId;
        private Long fixedLocationId;
    }
}
