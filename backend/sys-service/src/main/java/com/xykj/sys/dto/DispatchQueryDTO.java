package com.xykj.sys.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 派单查询DTO
 */
@Data
public class DispatchQueryDTO implements Serializable {

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
     * 派单方式：auto/manual
     */
    private String dispatchType;

    /**
     * 状态：pending/processing/completed/cancelled
     */
    private String status;

    /**
     * 组织ID
     */
    private Long orgId;

    /**
     * 数据权限注入组织ID列表
     */
    private List<Long> orgIds;

    /**
     * 处理人姓名（模糊查询）
     */
    private String handlerName;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;
}
