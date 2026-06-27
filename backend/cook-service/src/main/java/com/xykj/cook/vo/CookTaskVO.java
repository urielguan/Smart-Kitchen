package com.xykj.cook.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private Long menuId;
    private LocalDate planDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String mealType;
    private String menuName;
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
    private Integer targetTemp;
    private Integer currentTemp;
    private Boolean temperatureAbnormal;
    private Integer aiViolationCount;
    private BigDecimal qualityScore;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;
    private Long initiatorId;
    private String initiatorName;
    private Long completerId;
    private String completerName;
    private String handoffStatus;
    private String handoffRemark;
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
    private List<String> ingredients;
}
