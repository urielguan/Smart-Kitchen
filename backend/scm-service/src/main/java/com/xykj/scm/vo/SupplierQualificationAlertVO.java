package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 供应商资质临期提醒视图对象
 */
@Data
public class SupplierQualificationAlertVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private Long orgId;
    private Long tenantId;
    private String title;
    private String content;
    private Integer daysRemaining;
    private List<String> qualificationNames;
    private String licenseExpiresAt;
    private String licenseExpiryStatus;
    private Integer licenseRemainingDays;
    private String foodLicenseExpiresAt;
    private String foodLicenseExpiryStatus;
    private Integer foodLicenseRemainingDays;
    private String generatedAt;
}
