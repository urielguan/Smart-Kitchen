package com.xykj.recipe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 营养目标配置
 * 根据不同人群和健康状况配置营养目标值
 */
@Component
@ConfigurationProperties(prefix = "nutrition")
@Data
public class NutritionTargetConfig {

    /**
     * 人群营养目标配置（每人每日）
     */
    private Map<String, GroupNutritionTarget> groupTargets = new HashMap<>();

    /**
     * 健康状况饮食限制
     */
    private Map<String, HealthRestriction> healthRestrictions = new HashMap<>();

    /**
     * 获取每日营养目标
     */
    public GroupNutritionTarget getDailyTarget(String targetGroup) {
        return getGroupTargets().getOrDefault(targetGroup, getDefaultTarget());
    }

    /**
     * 人群营养目标
     */
    @Data
    public static class GroupNutritionTarget {
        /** 人群名称 */
        private String name;
        /** 每日热量目标(kcal) */
        private BigDecimal calories;
        /** 每日蛋白质目标(g) */
        private BigDecimal protein;
        /** 每日碳水化合物目标(g) */
        private BigDecimal carbohydrate;
        /** 每日脂肪目标(g) */
        private BigDecimal fat;
        /** 每日钠目标(mg) */
        private BigDecimal sodium;
        /** 每日膳食纤维目标(g) */
        private BigDecimal fiber;
        /** 蛋白质占比范围(%) */
        private String proteinRange;
        /** 碳水占比范围(%) */
        private String carbRange;
        /** 脂肪占比范围(%) */
        private String fatRange;
        /** 描述说明 */
        private String description;
    }

    /**
     * 健康状况饮食限制
     */
    @Data
    public static class HealthRestriction {
        /** 状况名称 */
        private String name;
        /** 需限制的营养素 */
        private Set<String> limitNutrients;
        /** 需避免的食材类别 */
        private Set<String> avoidIngredients;
        /** 需增加的营养素 */
        private Set<String> increaseNutrients;
        /** 特殊建议 */
        private String advice;
        /** 调整系数 */
        private BigDecimal adjustmentFactor;
    }

    /**
     * 获取人群营养目标（每人每餐）
     */
    public GroupNutritionTarget getPerMealTarget(String targetGroup) {
        GroupNutritionTarget dailyTarget = getGroupTargets().getOrDefault(
                targetGroup, getDefaultTarget());

        // 将每日目标转换为每餐目标（按3餐计算）
        GroupNutritionTarget mealTarget = new GroupNutritionTarget();
        mealTarget.setName(dailyTarget.getName());
        mealTarget.setCalories(dailyTarget.getCalories().divide(BigDecimal.valueOf(3), 0, BigDecimal.ROUND_HALF_UP));
        mealTarget.setProtein(dailyTarget.getProtein().divide(BigDecimal.valueOf(3), 1, BigDecimal.ROUND_HALF_UP));
        mealTarget.setCarbohydrate(dailyTarget.getCarbohydrate().divide(BigDecimal.valueOf(3), 0, BigDecimal.ROUND_HALF_UP));
        mealTarget.setFat(dailyTarget.getFat().divide(BigDecimal.valueOf(3), 1, BigDecimal.ROUND_HALF_UP));
        mealTarget.setSodium(dailyTarget.getSodium().divide(BigDecimal.valueOf(3), 0, BigDecimal.ROUND_HALF_UP));
        mealTarget.setFiber(dailyTarget.getFiber().divide(BigDecimal.valueOf(3), 1, BigDecimal.ROUND_HALF_UP));
        mealTarget.setProteinRange(dailyTarget.getProteinRange());
        mealTarget.setCarbRange(dailyTarget.getCarbRange());
        mealTarget.setFatRange(dailyTarget.getFatRange());
        mealTarget.setDescription(dailyTarget.getDescription());

        return mealTarget;
    }

    /**
     * 获取默认营养目标（普通成人）
     */
    public GroupNutritionTarget getDefaultTarget() {
        GroupNutritionTarget target = new GroupNutritionTarget();
        target.setName("普通成人");
        target.setCalories(new BigDecimal("2000"));
        target.setProtein(new BigDecimal("65"));
        target.setCarbohydrate(new BigDecimal("300"));
        target.setFat(new BigDecimal("60"));
        target.setSodium(new BigDecimal("2000"));
        target.setFiber(new BigDecimal("25"));
        target.setProteinRange("10-15");
        target.setCarbRange("50-65");
        target.setFatRange("20-30");
        target.setDescription("健康成年人标准营养目标");
        return target;
    }

    /**
     * 根据健康状况调整营养目标
     */
    public GroupNutritionTarget adjustForHealthStatus(GroupNutritionTarget baseTarget, String healthStatus) {
        if (healthStatus == null || healthStatus.isEmpty()) {
            return baseTarget;
        }

        GroupNutritionTarget adjustedTarget = new GroupNutritionTarget();
        adjustedTarget.setName(baseTarget.getName());
        adjustedTarget.setProteinRange(baseTarget.getProteinRange());
        adjustedTarget.setCarbRange(baseTarget.getCarbRange());
        adjustedTarget.setFatRange(baseTarget.getFatRange());
        adjustedTarget.setDescription(baseTarget.getDescription());

        // 复制基础值
        adjustedTarget.setCalories(baseTarget.getCalories());
        adjustedTarget.setProtein(baseTarget.getProtein());
        adjustedTarget.setCarbohydrate(baseTarget.getCarbohydrate());
        adjustedTarget.setFat(baseTarget.getFat());
        adjustedTarget.setSodium(baseTarget.getSodium());
        adjustedTarget.setFiber(baseTarget.getFiber());

        // 根据健康状况调整
        String[] statuses = healthStatus.split(",");
        for (String status : statuses) {
            HealthRestriction restriction = getHealthRestrictions().get(status.trim());
            if (restriction != null && restriction.getAdjustmentFactor() != null) {
                // 根据限制调整营养目标
                if (status.trim().equals("diabetes")) {
                    // 糖尿病：降低碳水，增加纤维
                    adjustedTarget.setCarbohydrate(adjustedTarget.getCarbohydrate()
                            .multiply(new BigDecimal("0.8")));
                    adjustedTarget.setFiber(adjustedTarget.getFiber()
                            .multiply(new BigDecimal("1.2")));
                    adjustedTarget.setCarbRange("45-55");
                } else if (status.trim().equals("hypertension")) {
                    // 高血压：降低钠
                    adjustedTarget.setSodium(new BigDecimal("1500"));
                } else if (status.trim().equals("hyperlipidemia")) {
                    // 高血脂：降低脂肪
                    adjustedTarget.setFat(adjustedTarget.getFat()
                            .multiply(new BigDecimal("0.7")));
                    adjustedTarget.setFatRange("15-25");
                } else if (status.trim().equals("obesity")) {
                    // 肥胖：降低总热量
                    adjustedTarget.setCalories(adjustedTarget.getCalories()
                            .multiply(new BigDecimal("0.8")));
                } else if (status.trim().equals("kidney_disease")) {
                    // 肾病：限制蛋白质
                    adjustedTarget.setProtein(adjustedTarget.getProtein()
                            .multiply(new BigDecimal("0.7")));
                }
            }
        }

        return adjustedTarget;
    }
}
