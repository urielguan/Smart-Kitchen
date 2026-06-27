package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 采购订单供应商选项
 */
@Data
public class PurchaseOrderSupplierOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String contactName;
    private String contactPhone;
}
