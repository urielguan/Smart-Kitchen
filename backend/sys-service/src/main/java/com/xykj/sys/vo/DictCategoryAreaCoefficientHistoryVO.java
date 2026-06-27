package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料类别面积系数修正历史 VO
 */
@Data
public class DictCategoryAreaCoefficientHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Integer correctionVersion;

    private BigDecimal oldAreaCoefficient;

    private BigDecimal newAreaCoefficient;

    private String oldAreaCoefficientSource;

    private String newAreaCoefficientSource;

    private String impactScope;

    private Boolean impactAcknowledged;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime impactAcknowledgedAt;

    private String recalcStatus;

    private Long recalcTaskId;

    private String recalcTaskNo;

    private Integer recalcProgressPercent;

    private String recalcResultMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recalcCompletedAt;

    private Long operatorId;

    private String operatorName;

    private Boolean currentVersion;

    private Boolean recalcAvailable;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
