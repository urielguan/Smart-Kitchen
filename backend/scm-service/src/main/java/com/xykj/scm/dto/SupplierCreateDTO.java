package com.xykj.scm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 供应商新增参数
 */
@Data
public class SupplierCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "供应商编码不能为空")
    @Size(max = 50, message = "供应商编码长度不能超过50个字符")
    private String supplierCode;

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 100, message = "供应商名称长度不能超过100个字符")
    private String supplierName;

    @NotBlank(message = "联系人不能为空")
    @Size(max = 50, message = "联系人长度不能超过50个字符")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    @Size(max = 20, message = "联系电话长度不能超过20个字符")
    private String contactPhone;

    @Size(max = 100, message = "联系邮箱长度不能超过100个字符")
    private String contactEmail;

    @Size(max = 200, message = "地址长度不能超过200个字符")
    private String address;

    @Size(max = 50, message = "供应商类型长度不能超过50个字符")
    private String supplierType;

    /**
     * 社会信用代码
     */
    @NotBlank(message = "社会信用代码不能为空")
    @Size(max = 50, message = "社会信用代码长度不能超过50个字符")
    private String unifiedCreditCode;

    @NotBlank(message = "银行账号不能为空")
    @Size(max = 50, message = "银行账号长度不能超过50个字符")
    private String bankAccount;

    @NotBlank(message = "开户行不能为空")
    @Size(max = 100, message = "开户行长度不能超过100个字符")
    private String bankName;

    @NotBlank(message = "营业执照编号不能为空")
    @Size(max = 100, message = "营业执照编号长度不能超过100个字符")
    private String licenseNo;

    /**
     * 日期格式：yyyy-MM-dd
     */
    @NotBlank(message = "执照到期日不能为空")
    private String licenseExpiresAt;

    @Size(max = 100, message = "食品许可证号长度不能超过100个字符")
    private String foodLicenseNo;

    /**
     * 日期格式：yyyy-MM-dd
     */
    private String foodLicenseExpiresAt;

    @Size(max = 20, message = "状态长度不能超过20个字符")
    private String status;

    @Size(max = 500, message = "禁用原因长度不能超过500个字符")
    private String disableReason;

    @Valid
    private List<SupplierQualificationFileDTO> qualificationFiles;
}
