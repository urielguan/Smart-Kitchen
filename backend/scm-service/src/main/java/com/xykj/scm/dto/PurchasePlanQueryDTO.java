package com.xykj.scm.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 采购计划查询入参
 */
@Data
public class PurchasePlanQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private Long orgId;
    private List<Long> orgIds;
    private String keyword;
    private String planName;
    private String planNo;
    private String status;
}
