package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购订单返回
 */
@Data
public class PurchaseOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long supplierId;
    private String supplierName;
    private Long orgId;
    private String orgName;
    private String buyerName;
    private Long createdById;
    private String createdBy;
    private String orderDate;
    private String expectedArrival;
    private BigDecimal totalAmount;
    private String logisticsCompany;
    private String logisticsTrackingNo;
    private String logisticsStatus;
    private String logisticsRemark;
    private String logisticsSourceType;
    private String logisticsSyncPayload;
    private String shippedAt;
    private String arrivedAt;
    private String logisticsAttachmentName;
    private String logisticsAttachmentUrl;
    private List<PurchaseOrderAttachmentVO> logisticsAttachments = new ArrayList<>();
    private String inspectionReportNo;
    private String inspectionResult;
    private String inspectionAgency;
    private String inspectionAt;
    private String inspectionRemark;
    private String inspectionSourceType;
    private String inspectionSyncPayload;
    private String inspectionAttachmentName;
    private String inspectionAttachmentUrl;
    private List<PurchaseOrderAttachmentVO> inspectionAttachments = new ArrayList<>();
    private Boolean inspectionFilled;
    private String attachmentName;
    private String attachmentUrl;
    private String status;
    private Boolean deleted;
    private String remark;
    private String createdAt;
    private String updatedAt;
    private String auditBy;
    private String auditAt;
    private String auditRemark;
    private String voidReason;
    private String voidRequestedBy;
    private String voidRequestedAt;
    private String voidAuditBy;
    private String voidAuditAt;
    private String voidAuditRemark;
    private String traceBatchId;
    private String traceOrigin;
    private String traceRemark;
    private String traceSourceType;
    private String traceSyncPayload;
    private String traceAttachmentName;
    private String traceAttachmentUrl;
    private List<PurchaseOrderAttachmentVO> traceabilityAttachments = new ArrayList<>();
    private Boolean traceabilityFilled;
    private List<Long> relatedPlanIds = new ArrayList<>();
    private List<PurchaseOrderRelatedPlanVO> relatedPlans = new ArrayList<>();
    private List<PurchaseOrderItemVO> items = new ArrayList<>();
}
