package com.xykj.sample.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 留样记录查询DTO
 */
@Data
public class SampleRecordQueryDTO {

    /** 页码（从1开始） */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 20;

    /** 状态 */
    private String status;

    /** 留样日期（起） */
    private LocalDate sampleDate;

    /** 留样日期（止） */
    private LocalDate sampleDateEnd;

    /** 餐次 */
    private String mealType;

    /** 菜谱名称（模糊） */
    private String menuName;

    /** 是否查看烹饪回滚隔离记录（仅管理员） */
    private Boolean showRollbackIsolated;

    /** 组织ID */
    private Long orgId;

    /** 数据权限注入组织ID列表 */
    private List<Long> orgIds;
}
