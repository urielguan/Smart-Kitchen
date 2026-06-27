package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物料类别面积系数历史回溯重算任务 VO
 */
@Data
public class DictCategoryAreaCoefficientRecalcTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String taskNo;

    private Long correctionId;

    private Integer correctionVersion;

    private BigDecimal oldAreaCoefficient;

    private BigDecimal newAreaCoefficient;

    private String status;

    private Integer progressPercent;

    private Integer totalSteps;

    private Integer completedSteps;

    private Integer successCount;

    private Integer failureCount;

    private String resultMessage;

    private Long startedBy;

    private String startedByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;

    private List<DictCategoryAreaCoefficientRecalcDetailVO> details;
}
