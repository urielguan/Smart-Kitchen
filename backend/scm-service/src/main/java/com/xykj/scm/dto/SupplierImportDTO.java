package com.xykj.scm.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商导入导出 DTO
 */
@Data
@HeadRowHeight(42)
@ColumnWidth(18)
public class SupplierImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 供应商编码
     */
    @ExcelProperty(index = 0)
    @ColumnWidth(20)
    private String supplierCode;

    /**
     * 供应商名称
     */
    @ExcelProperty(index = 1)
    @ColumnWidth(24)
    private String supplierName;

    /**
     * 联系人
     */
    @ExcelProperty(index = 2)
    @ColumnWidth(14)
    private String contactName;

    /**
     * 联系电话
     */
    @ExcelProperty(index = 3)
    @ColumnWidth(16)
    private String contactPhone;

    /**
     * 联系邮箱
     */
    @ExcelProperty(index = 4)
    @ColumnWidth(24)
    private String contactEmail;

    /**
     * 地址
     */
    @ExcelProperty(index = 5)
    @ColumnWidth(32)
    private String address;

    /**
     * 供应商类型
     */
    @ExcelProperty(index = 6)
    @ColumnWidth(16)
    private String supplierType;

    /**
     * 社会信用代码
     */
    @ExcelProperty(index = 7)
    @ColumnWidth(22)
    private String unifiedCreditCode;

    /**
     * 银行账号
     */
    @ExcelProperty(index = 8)
    @ColumnWidth(22)
    private String bankAccount;

    /**
     * 开户行
     */
    @ExcelProperty(index = 9)
    @ColumnWidth(24)
    private String bankName;

    /**
     * 营业执照编号
     */
    @ExcelProperty(index = 10)
    @ColumnWidth(20)
    private String licenseNo;

    /**
     * 执照到期日
     */
    @ExcelProperty(index = 11)
    @ColumnWidth(16)
    private String licenseExpiresAt;

    /**
     * 食品许可证号
     */
    @ExcelProperty(index = 12)
    @ColumnWidth(20)
    private String foodLicenseNo;

    /**
     * 食品许可证到期日
     */
    @ExcelProperty(index = 13)
    @ColumnWidth(18)
    private String foodLicenseExpiresAt;

    /**
     * 状态
     */
    @ExcelProperty(index = 14)
    @ColumnWidth(18)
    private String status;

    /**
     * 所属组织编码
     */
    @ExcelProperty(index = 15)
    @ColumnWidth(18)
    private String orgCode;

    /**
     * 失败原因
     */
    @ExcelProperty(index = 16)
    @ColumnWidth(40)
    private String errorMessage;

    /**
     * 原始供应商编码
     */
    private String rawSupplierCode;

    /**
     * 原始统一社会信用代码
     */
    private String rawUnifiedCreditCode;

    /**
     * 原始营业执照编号
     */
    private String rawLicenseNo;

    /**
     * 原始供应商类型
     */
    private String rawSupplierType;

    /**
     * 原始食品许可证号
     */
    private String rawFoodLicenseNo;

    /**
     * 原始所属组织编码
     */
    private String rawOrgCode;

    /**
     * 异常证照类型
     */
    private String documentType;

    /**
     * 证照编号原值
     */
    private String documentNo;

    /**
     * 所属组织编码映射后的组织ID
     */
    private Long parsedOrgId;

    /**
     * 失败字段
     */
    private String failedField;

    /**
     * Excel 行号
     */
    private Integer rowNum;

    /**
     * 是否成功
     */
    private Boolean success;
}
