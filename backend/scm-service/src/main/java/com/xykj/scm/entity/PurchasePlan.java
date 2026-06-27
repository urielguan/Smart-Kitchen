package com.xykj.scm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购计划实体
 */
@Data
@TableName("scm_purchase_plan")
public class PurchasePlan implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String planNo;
    private String planName;
    private LocalDate planDate;
    private String sourceType;
    private Long sourceRefId;
    private BigDecimal budgetAmount;
    private BigDecimal totalAmount;
    private String relatedDocument;
    private String attachmentName;
    private String attachmentUrl;
    private String remark;
    private String status;
    private LocalDateTime submittedAt;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String approveRemark;
    private String voidOriginStatus;
    private String voidReason;
    private Long voidRequestedBy;
    private LocalDateTime voidRequestedAt;
    private Long voidAuditBy;
    private LocalDateTime voidAuditAt;
    private String voidAuditRemark;
    private Integer mergeLocked;
    private Long mergeOrderId;
    private Long orgId;
    private Long tenantId;

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
