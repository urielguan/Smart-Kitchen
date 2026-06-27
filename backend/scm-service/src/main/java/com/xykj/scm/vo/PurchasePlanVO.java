package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购计划返回
 */
@Data
public class PurchasePlanVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String planNo;
    private String planName;
    private Long orgId;
    private String orgName;
    private String planDate;
    private String createdAt;
    private String createdBy;
    private BigDecimal budgetAmount;
    private BigDecimal totalAmount;
    private String relatedDocument;
    private String attachmentName;
    private String attachmentUrl;
    private String remark;
    private String status;
    private Boolean deleted;
    private String auditRemark;
    private String auditBy;
    private String auditAt;
    private String voidOriginalStatus;
    private String voidReason;
    private String voidRequestedBy;
    private String voidRequestedAt;
    private String voidAuditBy;
    private String voidAuditAt;
    private String voidAuditRemark;
    private Boolean mergeLocked;
    private Long mergeOrderId;
    private Integer generatedOrderCount;
    private Boolean allItemsGenerated;
    private List<PurchasePlanAttachmentVO> attachments = new ArrayList<>();
    private List<PurchasePlanItemVO> items = new ArrayList<>();
    private List<PurchaseOrderLinkVO> orderLinks = new ArrayList<>();
}
