package com.xykj.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "wms_outbound_order", autoResultMap = true)
public class OutboundOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("outbound_no")
    private String outboundNo;

    @TableField("outbound_type")
    private String outboundType;   // requisition/sales/return/transfer/loss/donation/scrap/other

    private Long warehouseId;

    private Long requesterId;      // 领用人/申请人ID
    private Long targetOrgId;      // 领用组织ID
    private String purpose;        // 用途

    private BigDecimal totalAmount;
    private String remark;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachments;

    private String status;         // draft/pending/approved/completed

    private LocalDateTime submittedAt;
    private Long submittedBy;

    private LocalDateTime approvedAt;
    private Long approvedBy;
    private LocalDateTime completedAt;
    private String approveRemark;

    private Long sourceOrderId;       // 来源单据ID（如菜谱计划ID）
    private String sourceOrderNo;     // 来源单据编号（如菜谱计划编号）
    private String submitterName;     // 提交人姓名

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
