package com.xykj.recipe.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 菜谱计划查询DTO
 */
@Data
public class RecipePlanQueryDTO {

    /**
     * 页码（从1开始）
     */
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 20;

    /**
     * 计划ID（用于AI推荐）
     */
    private Long planId;

    /**
     * 调整申请ID
     */
    private Long adjustmentId;

    /**
     * 计划日期（开始）
     */
    private LocalDate planDateStart;

    /**
     * 计划日期（结束）
     */
    private LocalDate planDateEnd;

    /**
     * 计划日期
     */
    private LocalDate planDate;

    /**
     * 计划编码（模糊搜索）
     */
    private String planCode;

    /**
     * 门店名称（模糊搜索）
     */
    private String orgName;

    /**
     * 实施开始日期（范围查询-起）
     */
    private LocalDate startDateStart;

    /**
     * 实施开始日期（范围查询-止）
     */
    private LocalDate startDateEnd;

    /**
     * 餐次
     */
    private String mealType;

    /**
     * 状态
     */
    private String status;

    /**
     * 调整类型: add/remove/modify
     */
    private String adjustType;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 数据权限注入组织ID列表
     */
    private List<Long> orgIds;

    /**
     * 目标人群类型
     */
    private String targetGroup;

    /**
     * 健康状况(逗号分隔)
     */
    private String healthStatus;

    /**
     * 就餐人数
     */
    private Integer expectedCount;

    /**
     * 预算限制（元）
     */
    private BigDecimal budgetLimit;

    /**
     * 计划维度：single/week/month
     */
    private String planDimension;

    /**
     * 周计划起始日
     */
    private LocalDate weekStartDate;

    /**
     * 天数（用于周/月计划）
     */
    private Integer daysCount;

    /**
     * 是否考虑库存约束
     */
    private Boolean considerStock;

    /**
     * 是否优先使用临期食材
     */
    private Boolean prioritizeExpiring;

    // ============= 用餐偏好相关字段 =============

    /**
     * 口味偏好（逗号分隔）：spicy/sweet/sour/salty/light/numb
     */
    private String flavorPreferences;

    /**
     * 辣度级别（0-5）：0=不辣，1=微辣，2=小辣，3=中辣，4=特辣，5=变态辣
     */
    private Integer spicyLevel;

    /**
     * 禁忌食材ID列表（逗号分隔）
     */
    private String avoidIngredientIds;

    /**
     * 过敏食材ID列表（逗号分隔）
     */
    private String allergyIngredientIds;

    /**
     * 饮食偏好标签（逗号分隔）：vegetarian/lowFat/lowSugar/highProtein/highFiber
     */
    private String dietTags;
}
