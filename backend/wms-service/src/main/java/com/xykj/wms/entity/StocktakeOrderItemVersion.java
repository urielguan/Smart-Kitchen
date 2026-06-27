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
@TableName("wms_stocktake_order_item_version")
public class StocktakeOrderItemVersion implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stocktakeVersionId;
    private Long stocktakeId;
    private Long stocktakeItemId;
    private Integer versionNo;
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private Long warehouseId;
    private Long locationId;
    private String batchNo;
    private Long inventoryId;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
