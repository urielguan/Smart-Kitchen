package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InventoryOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long materialId;
    private String materialCode;
    private String materialName;
    private String categoryName;
    private String materialSpec;
    private String unit;
    private String imageUrl;
    private String warehouseName;
    private String locationName;
    private BigDecimal currentStock;
    private BigDecimal minStock;
    private BigDecimal maxStock;
    private String stockRange;
    private String latestBatchNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate latestProductionDate;

    private Integer shelfLifeDays;
    private Integer minRemainingDays;
    private String stockStatus;
    private String shelfLifeLevel;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
