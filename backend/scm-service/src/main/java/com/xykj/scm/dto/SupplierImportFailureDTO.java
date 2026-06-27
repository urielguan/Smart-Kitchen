package com.xykj.scm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 供应商导入失败明细
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierImportFailureDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer rowNum;

    private String supplierCode;

    private String supplierName;

    private String supplierTypeRawValue;

    private String unifiedCreditCode;

    private String documentType;

    private String documentNo;

    private String failedField;

    private String errorMessage;
}
