package com.xykj.sample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 留样记录实体
 * 对应数据库表: sample_record
 */
@Data
@TableName("sample_record")
public class SampleRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 留样编号 */
    private String sampleNo;

    /** 关联烹饪任务ID */
    private Long taskId;

    /** 菜谱ID */
    private Long menuId;

    /** 菜谱名称（冗余） */
    private String menuName;

    /** 留样日期 */
    private LocalDate sampleDate;

    /** 餐次：breakfast/lunch/dinner/supper */
    private String mealType;

    /** 留样重量（克） */
    private BigDecimal sampleWeight;

    /** 留样照片（JSON数组） */
    private String sampleImages;

    /** AI质量评分（0-100） */
    private BigDecimal aiQualityScore;

    /** AI分析结果（JSON） */
    private String aiAnalysisResult;

    /** 存储位置 */
    private String storageLocation;

    /** 存储温度（℃） */
    private BigDecimal storageTemp;

    /** 留样人ID */
    private Long sampledBy;

    /** 留样时间 */
    private LocalDateTime sampledAt;

    /** 应销样时间（留样后48小时） */
    private LocalDateTime disposalDueAt;

    /** 销样人ID */
    private Long disposalBy;

    /** 实际销样时间 */
    private LocalDateTime disposalAt;

    /** 销样照片（JSON数组） */
    private String disposalImages;

    /** 销样备注 */
    private String disposalRemark;

    /** 作废原因 */
    private String voidReason;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** AI评估时间 */
    private LocalDateTime evaluatedAt;

    /** 锁定状态：none/investigation/accident */
    private String lockStatus;

    /** 追溯批次ID */
    private String traceBatchId;

    /** 食品台账编号 */
    private String foodSafetyLedgerNo;

    /** 留样来源类型：auto/manual_daily/manual_history/offline_delayed/system_backfill */
    private String recordOriginType;

    /** 补录原因 */
    private String supplementReason;

    /** 补录备注 */
    private String supplementRemark;

    /** 是否因烹饪任务回滚隔离：0否 1是 */
    private Integer rollbackIsolated;

    /** 烹饪任务回滚隔离时间 */
    private LocalDateTime rollbackIsolatedAt;

    /** 烹饪任务回滚隔离原因 */
    private String rollbackIsolationReason;

    /** 状态：sampled/pending_disposal/disposed/overdue/voided/archived/evaluated */
    private String status;

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
