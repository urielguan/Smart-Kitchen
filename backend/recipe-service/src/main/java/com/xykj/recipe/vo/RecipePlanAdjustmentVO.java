package com.xykj.recipe.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 菜谱计划调整申请列表VO
 */
@Data
public class RecipePlanAdjustmentVO {

    /**
     * 调整申请ID
     */
    private Long id;

    /**
     * 调整编码
     */
    private String adjustCode;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 计划编码
     */
    private String planCode;

    /**
     * 计划日期
     */
    private LocalDate planDate;

    /**
     * 调整类型: add=新增菜谱, remove=移除菜谱, modify=修改份数
     */
    private String adjustType;

    /**
     * 调整类型名称
     */
    private String adjustTypeName;

    /**
     * 调整原因
     */
    private String adjustReason;

    /**
     * 状态: pending=待审核, approved=已通过, rejected=已拒绝
     */
    private String status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 申请人姓名
     */
    private String appliedByName;

    /**
     * 申请时间
     */
    private LocalDateTime appliedAt;

    /**
     * 审核人姓名
     */
    private String auditedByName;

    /**
     * 审核时间
     */
    private LocalDateTime auditedAt;

    /**
     * 审核意见
     */
    private String auditRemark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
