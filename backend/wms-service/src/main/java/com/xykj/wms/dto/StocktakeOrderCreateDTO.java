package com.xykj.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StocktakeOrderCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long warehouseId;
    private Long locationId;
    private List<Long> warehouseIds;
    private List<Long> locationIds;

    @Size(max = 20, message = "盘点类型长度不能超过20")
    private String stocktakeType;

    @NotNull(message = "盘点日期不能为空")
    private LocalDate stocktakeDate;

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long checkerId;

    @Size(max = 100, message = "盘点人姓名长度不能超过100")
    private String checkerName;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;

    private List<String> attachments;

    @Valid
    private List<StocktakeOrderItemDTO> items;

    @AssertTrue(message = "盘点仓库不能为空")
    public boolean hasWarehouseSelection() {
        return warehouseId != null || (warehouseIds != null && !warehouseIds.isEmpty());
    }

    @Data
    public static class StocktakeOrderItemDTO implements Serializable {
        private Long id;
        private Long inventoryId;

        @NotNull(message = "物料ID不能为空")
        private Long materialId;

        @Size(max = 100, message = "物料名称长度不能超过100")
        private String materialName;

        @Size(max = 100, message = "规格长度不能超过100")
        private String spec;

        @Size(max = 20, message = "单位长度不能超过20")
        private String unit;

        private Long warehouseId;
        private Long locationId;

        @Size(max = 100, message = "批次号长度不能超过100")
        private String batchNo;

        private LocalDate expiryDate;

        @DecimalMin(value = "0", inclusive = true, message = "系统库存不能小于0")
        private BigDecimal systemQty;

        @DecimalMin(value = "0", inclusive = true, message = "实际库存不能小于0")
        private BigDecimal actualQty;

        @DecimalMin(value = "0", inclusive = true, message = "单价不能小于0")
        private BigDecimal unitCost;

        @Size(max = 100, message = "差异原因长度不能超过100")
        private String diffReason;

        @Size(max = 50, message = "识别来源长度不能超过50")
        private String recognitionSource;

        @DecimalMin(value = "0", inclusive = true, message = "AI置信度不能小于0")
        @DecimalMax(value = "1", inclusive = true, message = "AI置信度不能大于1")
        private BigDecimal aiConfidence;

        @Size(max = 200, message = "备注长度不能超过200")
        private String remark;

        @Size(max = 200, message = "行备注长度不能超过200")
        private String lineRemark;
    }
}
