package com.xykj.wms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wms_location_area_ledger")
public class LocationAreaLedger implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String bizAction;
    private Long bizOrderId;
    private Long bizItemId;
    private Long warehouseId;
    private Long locationId;
    private Long materialId;
    private BigDecimal effectiveQuantity;
    private BigDecimal areaCoefficientSnapshot;
    private BigDecimal areaDelta;
    private String direction;
    private String validationMode;
    private String skipReason;
    private Long reversedLedgerId;
    private Long orgId;
    private Long tenantId;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
