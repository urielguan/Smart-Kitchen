package com.xykj.wms.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class StocktakeStatisticsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long thisMonthCount;
    private Long pendingCount;
    private BigDecimal profitAmountTotal;
    private BigDecimal lossAmountTotal;
}
