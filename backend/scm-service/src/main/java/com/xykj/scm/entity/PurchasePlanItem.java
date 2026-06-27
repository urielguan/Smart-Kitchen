package com.xykj.scm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购计划明细实体
 */
@Data
@TableName("scm_purchase_plan_item")
public class PurchasePlanItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;
    private Long materialId;
    private String materialName;
    private String materialSpec;
    private String materialUnit;
    private BigDecimal planQty;
    private BigDecimal estimatePrice;
    private BigDecimal estimateAmount;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
