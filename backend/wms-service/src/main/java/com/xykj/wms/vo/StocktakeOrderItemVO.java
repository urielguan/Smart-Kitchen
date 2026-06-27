package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StocktakeOrderItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long stocktakeId;
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private String locationName;
    private String batchNo;
    private Long inventoryId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    private BigDecimal systemQty;
    private BigDecimal actualQty;
    private BigDecimal diffQty;
    private BigDecimal unitCost;
    private BigDecimal diffAmount;
    private String diffType;
    private String diffDirection;
    private String diffReason;
    private String recognitionSource;
    private BigDecimal aiConfidence;
    private String remark;
    private String lineRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
