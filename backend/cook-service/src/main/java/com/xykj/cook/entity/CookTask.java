package com.xykj.cook.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 烹饪任务实体
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
     * 任务日期（周期计划按天拆分后的实施日期）
     */
    private LocalDate taskDate;

    /**
     * 关联菜谱计划ID
     */
    private Long planId;

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
     * 烹饪设备ID（关联device_info表）
     */
    private Long deviceId;

    /**
     * 烹饪设备名称（冗余）
     */
    private String deviceName;

    /**
     * 设备位置描述（冗余）
     */
    private String deviceLocation;

    /**
     * 备料状态：pending_prep=待备料, prepared=已备料
     */
    private String materialPrepStatus;

    /**
     * 允许开始烹饪时间
     */
    private LocalTime allowStartTime;

    /**
     * 允许结束烹饪时间
     */
    private LocalTime allowEndTime;

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
     * 完成人ID
     */
    private Long completerId;

    /**
     * 完成人姓名
     */
    private String completerName;

    /**
     * 启动人ID
     */
    private Long initiatorId;

    /**
     * 启动人姓名
     */
    private String initiatorName;

    /**
     * 交接状态
     */
    private String handoffStatus;

    /**
     * 交接备注
     */
    private String handoffRemark;

    /**
     * 归档时间（迁移31执行后移除exist=false）
     */
    @TableField(exist = false)
    private LocalDateTime archivedAt;

    /**
     * 食安判定：true=达标, false=不达标, null=未判定
     */
    private Boolean foodSafetyPass;

    /**
     * 温度异常已确认：0=未确认，1=已确认
     */
    private Integer tempAbnormalConfirmed;

    /**
     * 温度异常确认人ID
     */
    private Long tempAbnormalConfirmedBy;

    /**
     * 温度异常确认时间
     */
    private LocalDateTime tempAbnormalConfirmedAt;

    /**
     * 采集运行状态：normal=正常, interrupted=采集中断, pending_recovery=待恢复
     */
    private String collectionStatus;

    /**
     * 最近一次采样时间
     */
    private LocalDateTime lastTemperatureRecordAt;

    /**
     * 同步状态：normal=正常, sync_failed=同步失败, conflict_pending=冲突待处理
     */
    private String syncStatus;

    /**
     * 同步重试次数
     */
    private Integer syncRetryCount;

    /**
     * 是否达到自动重试上限：0=未达到，1=已达到
     */
    private Integer syncRetryLimitReached;

    /**
     * 最近同步失败原因
     */
    private String latestSyncFailureReason;

    /**
     * 补偿状态：none=无, pending=待处理, resolved=已处理
     */
    private String compensationStatus;

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
