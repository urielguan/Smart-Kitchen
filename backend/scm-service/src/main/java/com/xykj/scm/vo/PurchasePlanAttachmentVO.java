package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 采购计划附件返回
 */
@Data
public class PurchasePlanAttachmentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String size;
    private String url;
    private Integer sortOrder;
}
