package com.xykj.sample.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销样提醒列表项VO
 */
@Data
public class DisposalReminderVO {

    private Long id;

    /** 留样编号 */
    private String sampleNo;

    /** 关联烹饪任务ID */
    private Long taskId;

    /** 菜谱名称 */
    private String menuName;

    /** 留样日期 */
    private LocalDate sampleDate;

    /** 留样时间 */
    private LocalDateTime sampledAt;

    /** 存储位置 */
    private String storageLocation;

    /** 应销样时间 */
    private LocalDateTime disposalDueAt;

    /** 状态 */
    private String status;

    /** 是否超期 */
    private Boolean isOverdue;

    /** 剩余小时数（负数表示已超期） */
    private Long remainHours;

    /** 当前操作锁状态 */
    private SampleOperationLockVO operationLock;
}
