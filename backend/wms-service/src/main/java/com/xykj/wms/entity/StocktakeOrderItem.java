package com.xykj.wms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("wms_stocktake_order_item")
public class StocktakeOrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stocktakeId;
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
