package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 烹饪任务详情VO
 */
@Data
public class CookTaskDetailVO {

    private Long id;
    private String taskNo;
    private Long planId;
    private String planCode;
    private LocalDate taskDate;
    private LocalDate planDate;
    private String mealType;
    private Long recipeId;
    private String menuName;
    private String recipeCode;
    private String recipeDescription;
    private String cookingSteps;
    private String status;
    private Integer plannedQty;
    private Integer actualQty;
    private String chefName;
    private Integer standardDuration;
    private Integer actualDuration;
    private Integer targetTempMin;
    private Integer targetTempMax;
    private Integer currentTemp;
    private Boolean temperatureAbnormal;
    private Integer aiViolationCount;
    private BigDecimal qualityScore;
    private String remark;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<CookIngredientVO> ingredients;
    private List<CookTemperaturePointVO> temperatureRecords;
    private List<CookAIMonitorVO> aiMonitorRecords;

    @Data
    public static class CookIngredientVO {
        private Long materialId;
        private String materialName;
        private String materialSpec;
        private BigDecimal quantity;
        private String unit;
        private Boolean main;
    }
}
