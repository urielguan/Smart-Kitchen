package com.xykj.sys.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 投诉查询DTO
 */
@Data
public class ComplaintQueryDTO implements Serializable {

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
     * 投诉类型：food/service/hygiene/other
     */
    private String complaintType;

    /**
     * 来源：meal/supervision/manual
     */
    private String source;

    /**
     * 状态：pending/dispatched/processing/closed
     */
    private String status;

    /**
     * 优先级：high/medium/low
     */
    private String priority;

    /**
     * 组织ID
     */
    private Long orgId;

    /**
     * 数据权限注入组织ID列表
     */
    private List<Long> orgIds;

    /**
     * 投诉人姓名（模糊查询）
     */
    private String submitterName;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;
}
