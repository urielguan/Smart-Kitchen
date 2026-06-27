package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InboundOrderItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long inboundId;
    private Long warehouseId;
    private String warehouseName;
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private Long locationId;
    private String locationName;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private String batchNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate productionDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;
}
