package com.xykj.wms.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OutboundOrderStatisticsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long totalCount;
    private Long draftCount;
    private Long pendingCount;
    private Long approvedCount;
    private Long completedCount;
    private BigDecimal thisMonthAmount;
}
