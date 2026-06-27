package com.xykj.cook.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 烹饪任务查询DTO
 */
@Data
public class CookTaskQueryDTO {

    /**
     * 页码
     */
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 20;

    /**
     * 实施日期（按任务自身的 task_date 过滤）
     */
    private LocalDate taskDate;

    /**
     * 计划日期（兼容旧参数，当 taskDate 为空时作为 fallback）
     */
    private LocalDate planDate;

    /**
     * 餐次
     */
    private String mealType;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 任务编号
     */
    private String taskNo;

    /**
     * 烹饪人
     */
    private String chefName;

    /**
     * 关键词（搜索菜名）
     */
    private String keyword;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 数据权限注入组织ID列表
     */
    private List<Long> orgIds;

    /**
     * 设备位置（模糊搜索）
     */
    private String deviceLocation;

    /**
     * 预警级别筛选（any=有预警，critical/warning/info=具体级别）
     */
    private String alertLevel;
}
