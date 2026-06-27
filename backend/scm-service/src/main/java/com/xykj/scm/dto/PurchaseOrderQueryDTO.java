package com.xykj.scm.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 采购订单查询入参
 */
@Data
public class PurchaseOrderQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private Long orgId;
    private List<Long> orgIds;
    private String keyword;
    private String orderNo;
    private String supplierName;
    private String status;
    private String dateStart;
    private String dateEnd;
}
