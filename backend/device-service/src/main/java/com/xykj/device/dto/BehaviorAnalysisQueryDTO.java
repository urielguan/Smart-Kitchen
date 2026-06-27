package com.xykj.device.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * AI人员行为分析查询DTO
 */
@Data
public class BehaviorAnalysisQueryDTO {

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 组织ID */
    private Long orgId;

    /** 员工ID */
    private Long employeeId;

    /** 员工姓名（模糊搜索） */
    private String employeeName;

    /** 是否有问题 */
    private Boolean hasIssues;

    /** 开始时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
