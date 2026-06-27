package com.xykj.recipe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 菜谱计划调整申请实体
 * 对应数据库表: recipe_plan_adjustment
 */
@Data
@TableName("recipe_plan_adjustment")
public class RecipePlanAdjustment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 调整申请ID
     */
    @TableId(type = IdType.AUTO)
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
     * 调整原因
     */
    private String adjustReason;

    /**
     * 调整类型: add=新增菜谱, remove=移除菜谱, modify=修改份数
     */
    private String adjustType;

    /**
     * 调整前数据（JSON）
     */
    private String beforeData;

    /**
     * 调整后数据（JSON）
     */
    private String afterData;

    /**
     * 状态: pending=待审核, approved=已通过, rejected=已拒绝
     */
    private String status;

    /**
     * 申请人ID
     */
    private Long appliedBy;

    /**
     * 申请时间
     */
    private LocalDateTime appliedAt;

    /**
     * 审核人ID
     */
    private Long auditedBy;

    /**
     * 审核时间
     */
    private LocalDateTime auditedAt;

    /**
     * 审核意见
     */
    private String auditRemark;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除: 0=未删除, 1=已删除
     */
    @TableLogic
    private Integer deleted;

    // ========== 关联查询字段（非数据库字段） ==========

    /**
     * 计划编码（关联查询）
     */
    @TableField(exist = false)
    private String planCode;

    /**
     * 计划日期（关联查询）
     */
    @TableField(exist = false)
    private LocalDate planDate;
}
