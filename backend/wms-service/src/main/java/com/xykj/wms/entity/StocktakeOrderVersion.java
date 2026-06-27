package com.xykj.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "wms_stocktake_order_version", autoResultMap = true)
public class StocktakeOrderVersion implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stocktakeId;
    private Integer versionNo;
    private String stocktakeNo;
    private Long warehouseId;
    private Long locationId;
    private String stocktakeType;
    private LocalDate stocktakeDate;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long checkerId;
    private String checkerName;
    private Integer itemCount;
    private BigDecimal diffQtyTotal;
    private BigDecimal profitAmountTotal;
    private BigDecimal lossAmountTotal;
    private BigDecimal diffRate;
    private BigDecimal surplusQty;
    private BigDecimal deficitQty;
    private BigDecimal surplusAmount;
    private BigDecimal deficitAmount;
    private String remark;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachments;

    private String status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String approveRemark;
    private String rejectRemark;
    private String voidReason;
    private Long orgId;
    private Long tenantId;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
