package com.xykj.sys.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 组织统计数据VO
 */
@Data
public class OrganizationStatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 组织总数
     */
    private Long total;

    /**
     * 集团数量
     */
    private Long groupCount;

    /**
     * 公司数量
     */
    private Long companyCount;

    /**
     * 食堂数量
     */
    private Long canteenCount;

    /**
     * 部门数量
     */
    private Long deptCount;

    /**
     * 启用数量
     */
    private Long activeCount;

    /**
     * 停用数量
     */
    private Long inactiveCount;
}
