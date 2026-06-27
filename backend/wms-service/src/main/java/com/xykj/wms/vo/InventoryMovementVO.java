package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryMovementVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String bizType;
    private String documentNo;
    private String operationType;
    private Long materialId;
    private String materialName;
    private String spec;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal postOperationStockQty;
    private String warehouseName;
    private String locationName;
    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operationTime;
}
