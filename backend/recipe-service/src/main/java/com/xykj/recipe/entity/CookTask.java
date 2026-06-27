package com.xykj.recipe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 烹饪任务实体（跨服务操作cook_task表）
 * 对应数据库表: cook_task
 */
@Data
@TableName("cook_task")
public class CookTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务编号
     */
    private String taskNo;

    /**
     * 关联菜谱计划ID
     */
    private Long planId;

    /**
     * 实施日期（周期计划按天拆分后每个任务对应具体日期）
     */
    @TableField("task_date")
    private LocalDate taskDate;

    /**
     * 菜谱ID
     */
    private Long menuId;

    /**
     * 菜谱名称（冗余）
     */
    private String menuName;

    /**
     * 计划份数
     */
    private Integer plannedQty;

    /**
     * 实际完成份数
     */
    private Integer actualQty;

    /**
     * 指派厨师ID
     */
    private Long assignedChefId;

    /**
     * 厨师姓名（冗余）
     */
    private String assignedChefName;

    /**
     * 开始烹饪时间
     */
    private LocalDateTime startTime;

    /**
     * 完成时间
     */
    private LocalDateTime endTime;

    /**
     * 实际烹饪时长（分钟）
     */
    private Integer cookingDuration;

    /**
     * 温度采集记录（JSON数组）
     */
    private String temperatureRecords;

    /**
     * AI识别违规次数
     */
    private Integer aiViolationCount;

    /**
     * 违规详情（JSON数组）
     */
    private String violationDetails;

    /**
     * 质量评分（0-100）
     */
    private BigDecimal qualityScore;

    /**
     * 状态：pending=待开始，in_progress=进行中，completed=已完成，cancelled=已取消
     */
    private String status;

    /**
     * 备注
     */
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
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}
