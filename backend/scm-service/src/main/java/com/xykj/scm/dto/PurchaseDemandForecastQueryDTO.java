package com.xykj.scm.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 采购需求预测历史查询入参
 */
@Data
public class PurchaseDemandForecastQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private Long orgId;
    private List<Long> orgIds;
    private String keyword;
    private String dimension;
}
