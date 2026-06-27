package com.xykj.recipe.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 营养目标VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionTargetVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标人群
     */
    private String targetGroup;

    /**
     * 人群名称
     */
    private String groupName;

    /**
     * 每日营养目标
     */
    private DailyTarget dailyTarget;

    /**
     * 每餐营养目标（每日目标的1/3）
     */
    private DailyTarget perMealTarget;

    /**
     * 健康状况调整说明
     */
    private List<HealthAdjustment> healthAdjustments;

    /**
     * 每日/每餐营养目标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTarget implements Serializable {
        private static final long serialVersionUID = 1L;

        private BigDecimal calories;
        private BigDecimal protein;
        private BigDecimal carbohydrate;
        private BigDecimal fat;
        private BigDecimal sodium;
        private BigDecimal fiber;
        private BigDecimal vitaminA;
        private BigDecimal vitaminC;
        private BigDecimal calcium;
        private BigDecimal iron;
    }

    /**
     * 健康状况调整
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthAdjustment implements Serializable {
        private static final long serialVersionUID = 1L;

        private String healthStatus;
        private String healthStatusName;
        private String adjustment;
        private List<String> restrictions;
    }
}
