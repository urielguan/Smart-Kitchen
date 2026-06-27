package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 采购订单关联计划
 */
@Data
public class PurchaseOrderRelatedPlanVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String planNo;
    private String planName;
    private String orgName;
}
