package com.xykj.wms.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OutboundOrderVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String outboundNo;
    private String outboundType;
    private Long warehouseId;
    private String warehouseName;
    private Long requesterId;
    private String requesterName;
    private Long targetOrgId;
    private String targetOrgName;
    private String purpose;
    private Long sourceOrderId;     // 来源单据ID（如菜谱计划ID）
    private String sourceOrderNo;   // 来源单据编号（如菜谱计划编号）
    private BigDecimal totalAmount;
    private String remark;
    private List<String> attachments;
    private String status;
    private LocalDateTime submittedAt;
    private Long submittedBy;
    private String submitterName;      // 提交人姓名
    private LocalDateTime approvedAt;
    private Long approvedBy;
    private LocalDateTime completedAt;
    private String approverName;      // 审核人姓名
    private String approveRemark;
    private Long orgId;
    private String orgName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer itemCount;
    private BigDecimal totalQuantity;

    private String warehouseNames;  // 多个仓库名称(逗号分隔)

    private List<OutboundOrderItemVO> items;
}
