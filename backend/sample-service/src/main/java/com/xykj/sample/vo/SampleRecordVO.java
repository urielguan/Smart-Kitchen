package com.xykj.sample.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 留样记录列表项VO
 */
@Data
public class SampleRecordVO {

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

    /** 菜谱名称 */
    private String menuName;

    /** 留样日期 */
    private LocalDate sampleDate;

    /** 餐次 */
    private String mealType;

    /** 留样重量（克） */
    private BigDecimal sampleWeight;

    /** AI质量评分 */
    private BigDecimal aiQualityScore;

    /** 存储位置 */
    private String storageLocation;

    /** 状态 */
    private String status;

    /** 是否因烹饪任务回滚被隔离 */
    private Boolean rollbackIsolated;

    /** 当前操作锁状态 */
    private SampleOperationLockVO operationLock;

    /** 留样时间 */
    private LocalDateTime sampledAt;

    /** 应销样时间 */
    private LocalDateTime disposalDueAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
