package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InboundOrderVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String inboundNo;
    private String sourceType;
    private Long sourceId;
    private Long supplierId;
    private String supplierName;
    private Long sourceOrderId;
    private String sourceOrderNo;
    private Long orgId;
    private Long receivingOrgId;
    private String receivingOrgName;
    private Long warehouseId;
    private String warehouseName;
    private String warehouseNames;
    private BigDecimal totalAmount;
    private String remark;
    private List<String> attachments;
    private String status;  // draft/pending/approved/rejected/cancelled
    private Long version;
    private String postStatus;
    private String postErrorMessage;
    private Integer itemCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;
    private Long submittedBy;
    private String submitterName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;
    private String approveRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // 详情时携带
    private List<InboundOrderItemVO> items;
}
