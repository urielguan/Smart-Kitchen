package com.xykj.wms.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OutboundSuggestionStockCandidateVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long inventoryId;
    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private String locationName;
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private String inventoryStatus;
    private Long sourceId;
    private Long orgId;
    private Long tenantId;
    private LocalDateTime inboundTime;
}
