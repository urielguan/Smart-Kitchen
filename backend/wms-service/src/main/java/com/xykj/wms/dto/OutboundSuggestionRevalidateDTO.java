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
public class OutboundSuggestionRevalidateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long orderId;

    @Valid
    @NotEmpty(message = "校验明细不能为空")
    private List<DetailDTO> details;

    @Data
    public static class DetailDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long detailId;
        private Integer lineNo;
        private Long materialId;

        @Size(max = 100)
        private String specName;

        @NotNull(message = "需求数量不能为空")
        @DecimalMin(value = "0.001", message = "需求数量必须大于0")
        private BigDecimal requestQty;

        @Valid
        @NotEmpty(message = "分配明细不能为空")
        private List<AllocationDTO> allocations;
    }

    @Data
    public static class AllocationDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotNull(message = "来源库存明细不能为空")
        private Long sourceStockDetailId;

        private Long warehouseId;
        private Long locationId;

        @Size(max = 100)
        private String batchNo;

        @NotNull(message = "分配数量不能为空")
        @DecimalMin(value = "0.001", message = "分配数量必须大于0")
        private BigDecimal suggestQty;
    }
}
