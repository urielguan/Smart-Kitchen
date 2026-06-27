package com.xykj.health.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 晨检记录查询DTO
 */
@Data
public class HealthCheckQueryDTO {

    /** 页码（从1开始） */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 20;

    /** 晨检日期（精确查询） */
    private LocalDate checkDate;

    /** 晨检日期范围-起始 */
    private LocalDate checkDateStart;

    /** 晨检日期范围-结束 */
    private LocalDate checkDateEnd;

    /** 状态 */
    private String status;

    /** 晨检结果 */
    private String checkResult;

    /** 员工姓名（模糊） */
    private String employeeName;

    /** 组织ID */
    private Long orgId;

    /** 数据权限注入组织ID列表 */
    private List<Long> orgIds;
}
