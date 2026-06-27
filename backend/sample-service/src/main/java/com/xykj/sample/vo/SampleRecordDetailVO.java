package com.xykj.sample.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 留样记录详情VO
 */
@Data
public class SampleRecordDetailVO {

    private Long id;

    /** 留样编号 */
    private String sampleNo;

    /** 关联烹饪任务ID */
    private Long taskId;

    /** 烹饪任务编号 */
    private String taskNo;

    /** 来源 */
    private String sourceLabel;

    /** 来源类型 */
    private String recordOriginType;

    /** 销样来源类型 */
    private String disposalSourceType;

    /** 销样来源 */
    private String disposalSourceLabel;

    /** 关联烹饪任务状态 */
    private String taskStatus;

    /** 菜谱ID */
    private Long menuId;

    /** 菜谱名称 */
    private String menuName;

    /** 留样日期 */
    private LocalDate sampleDate;

    /** 餐次 */
    private String mealType;

    /** 留样重量（克） */
    private BigDecimal sampleWeight;

    /** 留样照片列表 */
    private List<String> sampleImages;

    /** AI质量评分 */
    private BigDecimal aiQualityScore;

    /** AI分析结果 */
    private Object aiAnalysisResult;

    /** 存储位置 */
    private String storageLocation;

    /** 存储温度 */
    private BigDecimal storageTemp;

    /** 留样人ID */
    private Long sampledBy;

    /** 留样时间 */
    private LocalDateTime sampledAt;

    /** 应销样时间 */
    private LocalDateTime disposalDueAt;

    /** 销样人ID */
    private Long disposalBy;

    /** 销样时间 */
    private LocalDateTime disposalAt;

    /** 销样照片列表 */
    private List<String> disposalImages;

    /** 销样备注 */
    private String disposalRemark;

    /** 状态 */
    private String status;

    /** 组织ID */
    private Long orgId;

    /** 租户ID */
    private Long tenantId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

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

    /** 补录原因 */
    private String supplementReason;

    /** 补录备注 */
    private String supplementRemark;

    /** 销样补录场景 */
    private String disposalSupplementScene;

    /** 销样补录备注 */
    private String disposalSupplementRemark;

    /** 销样补录时间 */
    private LocalDateTime disposalSupplementedAt;

    /** 销样补录人ID */
    private Long disposalSupplementedBy;

    /** 销样补录人姓名 */
    private String disposalSupplementedByName;

    /** 是否因烹饪任务回滚被隔离 */
    private Boolean rollbackIsolated;

    /** 当前操作锁状态 */
    private SampleOperationLockVO operationLock;

    /** 烹饪任务回滚隔离时间 */
    private LocalDateTime rollbackIsolatedAt;

    /** 烹饪任务回滚隔离原因 */
    private String rollbackIsolationReason;

    /** 留样人姓名 */
    private String sampledByName;

    /** 销样人姓名 */
    private String disposalByName;

    /** 追溯链 */
    private TraceChainVO traceChain;
}
