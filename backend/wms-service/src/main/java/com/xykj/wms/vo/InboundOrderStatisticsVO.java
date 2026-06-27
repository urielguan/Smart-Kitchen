package com.xykj.wms.vo;

import lombok.Data;
import java.io.Serializable;

@Data
public class InboundOrderStatisticsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long thisMonthTotalCount;
    private Long thisMonthPendingCount;
    private Long thisMonthApprovedCount;
    private java.math.BigDecimal thisMonthInboundAmount;
}
