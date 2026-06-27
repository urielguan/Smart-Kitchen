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
 * 采购订单实体
 */
@Data
@TableName("scm_purchase_order")
public class PurchaseOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long planId;
    private Long supplierId;
    private String supplierName;
    private LocalDate orderDate;
    private BigDecimal totalAmount;
    private LocalDateTime expectedDeliveryAt;
    private LocalDateTime actualDeliveryAt;
    private String deliveryAddress;
    private String logisticsNo;
    private String logisticsCompany;
    private String logisticsStatus;
    private String logisticsRemark;
    private String logisticsSourceType;
    private String logisticsSyncPayload;
    private LocalDateTime shippedAt;
    private LocalDateTime arrivedAt;
    private String logisticsAttachmentName;
    private String logisticsAttachmentUrl;
    private String inspectionReportNo;
    private String inspectionResult;
    private String inspectionAgency;
    private LocalDateTime inspectionAt;
    private String inspectionRemark;
    private String inspectionSourceType;
    private String inspectionSyncPayload;
    private String inspectionAttachmentName;
    private String inspectionAttachmentUrl;
    private String attachmentName;
    private String attachmentUrl;
    private String remark;
    private String status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String approveRemark;
    private String voidReason;
    private Long voidRequestedBy;
    private LocalDateTime voidRequestedAt;
    private Long voidAuditBy;
    private LocalDateTime voidAuditAt;
    private String voidAuditRemark;
    private String traceBatchId;
    private String traceOrigin;
    private String traceRemark;
    private String traceSourceType;
    private String traceSyncPayload;
    private String traceAttachmentName;
    private String traceAttachmentUrl;
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
