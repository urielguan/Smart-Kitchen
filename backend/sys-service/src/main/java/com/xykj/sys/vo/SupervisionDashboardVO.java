package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据监管看板VO
 */
@Data
public class SupervisionDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal violationRate;
    private BigDecimal violationRateYoy;
    private BigDecimal violationRateMom;
    private BigDecimal violationRateTarget;

    private BigDecimal traceResponseTime;
    private BigDecimal traceResponseYoy;
    private BigDecimal traceResponseMom;
    private BigDecimal traceResponseTarget;

    private BigDecimal wasteRate;
    private BigDecimal wasteRateYoy;
    private BigDecimal wasteRateMom;
    private BigDecimal wasteRateTarget;

    private BigDecimal satisfactionRate;
    private BigDecimal satisfactionYoy;
    private BigDecimal satisfactionMom;
    private BigDecimal satisfactionTarget;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime snapshotAt;

    private List<SupervisionViolationVO> recentViolations;

    private List<SupervisionTraceVO> recentTraces;

    private List<TrendPoint> trendData;

    @Data
    public static class TrendPoint implements Serializable {
        private static final long serialVersionUID = 1L;

        private String weekLabel;
        private BigDecimal violationRate;
        private BigDecimal wasteRate;
        private BigDecimal satisfactionRate;
    }
}
