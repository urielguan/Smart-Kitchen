package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购计划反审核结果
 */
@Data
public class PurchasePlanReverseAuditResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer affectedOrderCount;
    private List<String> affectedOrderNos = new ArrayList<>();
}
