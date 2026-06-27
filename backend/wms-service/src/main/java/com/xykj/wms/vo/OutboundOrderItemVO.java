package com.xykj.wms.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class OutboundOrderItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long outboundId;
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private String locationName;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private String batchNo;
    private LocalDate expiryDate;
    private String purpose;
    private String remark;
    private List<OutboundOrderAllocationVO> allocations;
}
