package com.xykj.cook.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private LocalDate planDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String mealType;
    private Long recipeId;
    private String menuName;
    private String recipeCode;
    private String recipeDescription;
    private String cookingSteps;
    private String status;
    private Integer plannedQty;
    private Integer actualQty;
    private Long assignedChefId;
    private String assignedChefName;
    private String chefName;
    private Long deviceId;
    private String deviceName;
    private String deviceLocation;
    private String materialPrepStatus;
    private LocalTime allowStartTime;
    private LocalTime allowEndTime;
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
    private Long initiatorId;
    private String initiatorName;
    private Long completerId;
    private String completerName;
    private String handoffStatus;
    private String handoffRemark;
    private List<CookIngredientVO> ingredients;
    private List<CookTemperaturePointVO> temperatureRecords;
    private List<CookAIMonitorVO> aiMonitorRecords;
    private String outboundOrderNo;
    private Boolean foodSafetyPass;
    private Boolean tempAbnormalConfirmed;
    private Long tempAbnormalConfirmedBy;
    private LocalDateTime tempAbnormalConfirmedAt;
    private String collectionStatus;
    private LocalDateTime lastTemperatureRecordAt;
    private Boolean hasSyncException;
    private String syncStatus;
    private Integer syncRetryCount;
    private Boolean syncRetryLimitReached;
    private String latestSyncFailureReason;
    private Boolean hasCompensationPending;
    private String compensationStatus;

    @Data
    public static class CookIngredientVO {
        private Long materialId;
        private String materialName;
        private String materialSpec;
        private BigDecimal quantity;
        private String unit;
        private Boolean main;
        private String batchNo;
        private String traceBatchId;
    }
}
