package com.xykj.sys.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 监管看板首页查询DTO
 */
@Data
public class RegulatoryDashboardQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * today / 7d / 30d
     */
    private String quickRange = "today";

    private String organization;

    private String canteen;

    private String area;

    private String startDate;

    private String endDate;

    private Long orgId;

    private List<Long> orgIds;
}
