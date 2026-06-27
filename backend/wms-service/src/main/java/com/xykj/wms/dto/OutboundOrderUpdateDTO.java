package com.xykj.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class OutboundOrderUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String outboundType;
    private Long targetOrgId;
    private LocalDate outboundDate;
    @Size(max = 200)
    private String purpose;

    private Long supplierId;
    private String supplierName;
    private Long recipientId;
    private String recipientName;

    private Long sourceOrderId;
    private String sourceOrderNo;

    @Size(max = 500)
    private String remark;

    private List<String> attachments;

    @Valid
    private List<OutboundOrderItemDTO> items;

    @Data
    public static class OutboundOrderItemDTO implements Serializable {
        private Long id;  // 更新时带上ID
        private Long materialId;
        private String materialName;
        private String spec;
        private String unit;
        private Long warehouseId;
        private Long locationId;
        private String batchNo;

        @DecimalMin(value = "0.001", message = "出库数量必须大于0")
        private BigDecimal quantity;

        @DecimalMin(value = "0", message = "单价不能为负")
        private BigDecimal unitCost;

        private LocalDate expiryDate;

        @Size(max = 200)
        private String purpose;

        @Size(max = 200)
        private String lineRemark;

        @Size(max = 200)
        private String remark;

        @Valid
        private List<OutboundOrderAllocationDTO> allocations;
    }

    @Data
    public static class OutboundOrderAllocationDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;

        private Long sourceStockDetailId;
        private Long warehouseId;
        private Long locationId;
        private String batchNo;
        private LocalDate productionDate;
        private LocalDate expiryDate;

        @DecimalMin(value = "0.001", message = "分配数量必须大于0")
        private BigDecimal quantity;

        @Size(max = 32)
        private String sourceType;
    }
}
