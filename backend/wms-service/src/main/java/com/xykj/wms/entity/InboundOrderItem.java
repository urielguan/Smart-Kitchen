package com.xykj.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("wms_inbound_order_item")
public class InboundOrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inboundId;
    private Long warehouseId;
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private Long locationId;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String traceBatchId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
