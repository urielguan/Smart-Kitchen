package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 烹饪任务VO
 */
@Data
public class CookTaskVO {

    private Long id;
    private String taskNo;
    private Long planId;
    private LocalDate taskDate;
    private LocalDate planDate;
    private String mealType;
    private String menuName;
    private String status;
    private Integer plannedQty;
    private Integer actualQty;
    private String chefName;
    private Integer standardDuration;
    private Integer actualDuration;
    private Integer targetTemp;
    private Integer currentTemp;
    private Boolean temperatureAbnormal;
    private Integer aiViolationCount;
    private BigDecimal qualityScore;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;
    private List<String> ingredients;
}
