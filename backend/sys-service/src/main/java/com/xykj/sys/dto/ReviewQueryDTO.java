package com.xykj.sys.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 评价查询DTO
 */
@Data
public class ReviewQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 页码
     */
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

    /**
     * 来源：meal/supervision/manual
     */
    private String source;

    /**
     * 组织ID
     */
    private Long orgId;

    /**
     * 数据权限注入组织ID列表
     */
    private List<Long> orgIds;

    /**
     * 关键词（评价人姓名/菜品名称模糊查询）
     */
    private String keyword;

    /**
     * 评分（1-5）
     */
    private Integer overallScore;

    /**
     * 开始时间（按创建时间筛选）
     */
    private String startTime;

    /**
     * 结束时间（按创建时间筛选）
     */
    private String endTime;
}
