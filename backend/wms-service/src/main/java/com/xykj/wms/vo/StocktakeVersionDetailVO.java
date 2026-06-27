package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StocktakeVersionDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long stocktakeId;
    private Integer versionNo;
    private String stocktakeNo;
    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private String locationName;
    private String stocktakeType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate stocktakeDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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
    private List<String> attachments;
    private String status;
    private Long approvedBy;
    private String approverName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    private String approveRemark;
    private String rejectRemark;
    private String voidReason;
    private Long orgId;
    private Long tenantId;
    private Long submittedBy;
    private String submitterName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;

    private Long createdBy;
    private String creatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private List<StocktakeOrderItemVO> items;
}
