package com.xykj.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "wms_inbound_order", autoResultMap = true)
public class InboundOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String inboundNo;
    private String sourceType;   // purchase/manual/return
    private Long sourceId;
    private Long supplierId;
    private String supplierName;
    private Long sourceOrderId;
    private String sourceOrderNo;
    private Long warehouseId;
    private BigDecimal totalAmount;
    private String remark;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachments;

    private String status;       // draft/pending/approved/completed/rejected/cancelled
    private Long version;
    private String postStatus;
    private String postErrorMessage;
    private LocalDateTime submittedAt;
    private Long submittedBy;
    private String submitterName;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String approveRemark;
    private Long orgId;
    private Long receivingOrgId;
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
