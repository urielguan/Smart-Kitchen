package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InventoryDistributionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private String locationName;
    private String batchNo;
    private BigDecimal quantity;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate productionDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    private Integer remainingDays;
}
