package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商视图对象
 */
@Data
public class SupplierVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String supplierCode;
    private String supplierName;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String address;

    private String supplierType;
    /**
     * 社会信用代码
     */
    private String unifiedCreditCode;
    private String bankAccount;
    private String bankName;

    private String licenseNo;
    private String licenseExpiresAt;
    private String licenseExpiryStatus;
    private Integer licenseRemainingDays;
    private String foodLicenseNo;
    private String foodLicenseExpiresAt;
    private String foodLicenseExpiryStatus;
    private Integer foodLicenseRemainingDays;

    private BigDecimal creditScore;
    private BigDecimal scoreQualification;
    private BigDecimal scoreQuality;
    private BigDecimal scorePrice;
    private BigDecimal scoreDelivery;
    private String scoreUpdatedAt;
    private String scoreStatisticsPeriod;
    private String aiLevel;
    private String recommendPriority;
    private String riskWarningLevel;
    private Boolean scoreQualitySampleInsufficient;
    private Boolean scorePriceSampleInsufficient;
    private Boolean scoreDeliverySampleInsufficient;
    private List<String> optimizationSuggestions;

    private String status;
    private String disableReason;
    private String cancelReason;
    private List<SupplierQualificationFileVO> qualificationFiles;
    private String auditAt;
    private String auditRemark;
    private String createdByName;
    private String updatedByName;
    private String auditByName;
    private Long tenantId;
    private String createdAt;
    private String updatedAt;
}
