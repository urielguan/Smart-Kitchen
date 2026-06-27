package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StocktakeVersionSummaryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long stocktakeId;
    private Integer versionNo;
    private String status;
    private Integer itemCount;
    private BigDecimal diffQtyTotal;
    private BigDecimal profitAmountTotal;
    private BigDecimal lossAmountTotal;
    private Long submittedBy;
    private String submitterName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
