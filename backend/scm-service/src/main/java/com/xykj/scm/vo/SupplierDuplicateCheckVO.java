package com.xykj.scm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 供应商唯一性校验结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDuplicateCheckVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 供应商编码是否重复
     */
    private Boolean supplierCodeDuplicate;

    /**
     * 供应商编码重复提示
     */
    private String supplierCodeMessage;

    /**
     * 供应商名称是否重复
     */
    private Boolean supplierNameDuplicate;

    /**
     * 供应商名称重复提示
     */
    private String supplierNameMessage;

    /**
     * 营业执照编号是否重复
     */
    private Boolean licenseNoDuplicate;

    /**
     * 营业执照编号重复提示
     */
    private String licenseNoMessage;

    /**
     * 食品许可证号是否重复
     */
    private Boolean foodLicenseNoDuplicate;

    /**
     * 食品许可证号重复提示
     */
    private String foodLicenseNoMessage;
}
