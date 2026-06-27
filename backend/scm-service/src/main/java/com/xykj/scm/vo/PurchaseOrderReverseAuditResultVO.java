package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购订单反审核结果
 */
@Data
public class PurchaseOrderReverseAuditResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer affectedInboundCount;
    private List<String> affectedInboundNos = new ArrayList<>();
}
