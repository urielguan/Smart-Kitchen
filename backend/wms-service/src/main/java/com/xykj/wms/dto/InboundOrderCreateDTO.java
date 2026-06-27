package com.xykj.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class InboundOrderCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "入库类型不能为空")
    private String sourceType;  // purchase/transfer/return/material_return/surplus/donation/other

    private Long sourceId;
    private Long supplierId;
    private String supplierName;
    private Long sourceOrderId;
    private String sourceOrderNo;
    private Long orgId;
    private Long receivingOrgId;

    @Size(max = 500)
    private String remark;

    private List<String> attachments;

    @NotEmpty(message = "入库明细不能为空")
    @Valid
    private List<InboundOrderItemDTO> items;

    @Data
    public static class InboundOrderItemDTO implements Serializable {
        @Size(max = 64)
        private String lineKey;

        @NotNull(message = "入库仓库不能为空")
        private Long warehouseId;

        @NotNull(message = "仓位不能为空")
        private Long locationId;

        @NotNull(message = "物料ID不能为空")
        private Long materialId;

        @NotBlank(message = "物料名称不能为空")
        @Size(max = 100)
        private String materialName;

        @Size(max = 100)
        private String spec;

        @NotBlank(message = "单位不能为空")
        @Size(max = 20)
        private String unit;

        @NotNull(message = "入库数量不能为空")
        @DecimalMin(value = "0.001", message = "入库数量必须大于0")
        private BigDecimal quantity;

        @DecimalMin(value = "0", message = "单价不能为负")
        private BigDecimal unitCost;

        @Size(max = 100)
        private String batchNo;

        private LocalDate productionDate;
        private LocalDate expiryDate;
    }
}
