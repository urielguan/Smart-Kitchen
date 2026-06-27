package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 采购计划关联单据下拉项
 */
@Data
public class PurchasePlanRelatedDocumentOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String documentType;
    private String documentTypeLabel;
    private String documentNo;
    private String title;
    private String optionLabel;
    private Long orgId;
    private String orgName;
}
