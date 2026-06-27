package com.xykj.sys.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 数据监管看板查询DTO
 */
@Data
public class SupervisionDashboardQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 组织ID
     */
    private Long orgId;

    /**
     * 数据权限注入组织ID列表
     */
    private List<Long> orgIds;

    /**
     * 日期范围 today/week/month/quarter/year
     */
    private String dateRange = "month";
}
