package com.xykj.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class OutboundOrderCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "出库类型不能为空")
    private String outboundType;  // requisition/sales/return/transfer/loss/donation/scrap/other

    @NotNull(message = "出库仓库不能为空")
    private Long warehouseId;

    private Long requesterId;      // 领用人/申请人ID
    private Long targetOrgId;

    @NotBlank(message = "用途不能为空")
    @Size(max = 200)
    private String purpose;        // 用途

    private Long sourceOrderId;      // 来源单据ID（如菜谱计划ID）
    private String sourceOrderNo;    // 来源单据编号（如菜谱计划编号）

    @Size(max = 500)
    private String remark;

    private List<String> attachments;

    @NotEmpty(message = "出库明细不能为空")
    @Valid
    private List<OutboundOrderItemDTO> items;

    @Data
    public static class OutboundOrderItemDTO implements Serializable {
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

        private Long warehouseId;
        private Long locationId;

        @Size(max = 100)
        private String batchNo;

        @NotNull(message = "出库数量不能为空")
        @DecimalMin(value = "0.001", message = "出库数量必须大于0")
        private BigDecimal quantity;

        @DecimalMin(value = "0", message = "单价不能为负")
        private BigDecimal unitCost;

        private LocalDate expiryDate;

        @Size(max = 200)
        private String purpose;

        @Size(max = 200)
        private String remark;

        @Valid
        private List<OutboundOrderAllocationDTO> allocations;
    }

    @Data
    public static class OutboundOrderAllocationDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotNull(message = "来源库存明细不能为空")
        private Long sourceStockDetailId;

        private Long warehouseId;
        private Long locationId;

        @Size(max = 100)
        private String batchNo;

        private LocalDate productionDate;
        private LocalDate expiryDate;

        @NotNull(message = "分配数量不能为空")
        @DecimalMin(value = "0.001", message = "分配数量必须大于0")
        private BigDecimal quantity;

        @Size(max = 32)
        private String sourceType;
    }
}
