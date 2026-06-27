package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购订单关联记录
 */
@Data
public class PurchaseOrderLinkVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private String createdAt;
    private String createdBy;
    private List<PurchaseOrderLinkItemVO> items = new ArrayList<>();
}
