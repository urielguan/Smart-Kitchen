package com.xykj.recipe.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 菜谱计划实体
 * 对应数据库表: recipe_plan
 */
@Data
@TableName("recipe_plan")
public class RecipePlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 计划ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 计划编码
     */
    private String planCode;

    /**
     * 计划日期
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate planDate;

    /**
     * 实施开始日期
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate startDate;

    /**
     * 实施结束日期
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate endDate;

    /**
     * 餐次: breakfast=早餐, lunch=午餐, dinner=晚餐
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String mealType;

    /**
     * 就餐人数
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer expectedCount;

    /**
     * 目标人群类型: adult=普通成人, elderly=老人, child=儿童, patient=病人
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String targetGroup;

    /**
     * 健康状况标签(逗号分隔): diabetes=糖尿病, hypertension=高血压, hyperlipidemia=高血脂, obesity=肥胖
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String healthStatus;

    /**
     * 饮食限制描述
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String dietRestrictions;

    /**
     * 总份数
     */
    private Integer totalServings;

    /**
     * 预估总成本（元）
     */
    private BigDecimal estimatedCost;

    /**
     * 营养达标率（%）
     */
    private BigDecimal nutritionPassRate;

    /**
     * AI营养评估结果（JSON）
     */
    private String aiNutritionAssessment;

    /**
     * 是否使用AI推荐
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Boolean useAiRecommend;

    /**
     * 状态: draft=草稿, pending=待审核, approved=已审核, rejected=已拒绝, completed=已完成
     */
    private String status;

    /**
     * 库存风险状态: normal=正常, warning=临期预警, expired=已过期, shortage=库存不足, unknown=待确认
     */
    private String stockRiskStatus;

    /**
     * 库存风险说明
     */
    private String stockRiskMessage;

    /**
     * 最近一次库存校验时间
     */
    private LocalDateTime stockValidatedAt;

    /**
     * 下一次自动复检时间
     */
    private LocalDateTime stockNextRecheckAt;

    /**
     * 提交人ID
     */
    private Long submittedBy;

    /**
     * 提交时间
     */
    private LocalDateTime submittedAt;

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
     * 备注
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

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
}
