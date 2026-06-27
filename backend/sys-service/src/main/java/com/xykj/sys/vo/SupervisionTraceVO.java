package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 溯源响应记录VO
 */
@Data
public class SupervisionTraceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String traceNo;
    private String materialName;
    private String batchNo;
    private String supplierName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime requestTime;

    private BigDecimal responseTime;
    private String responseTimeRange;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    private String status;
}
