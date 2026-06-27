package com.xykj.wms.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OutboundOrderAllocationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long outboundId;
    private Long outboundItemId;
    private Long sourceStockDetailId;
    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private String locationName;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private BigDecimal quantity;
    private String sourceType;
}
