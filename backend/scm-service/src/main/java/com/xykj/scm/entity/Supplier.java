package com.xykj.scm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商实体
 * 对应数据库表: scm_supplier
 */
@Data
@TableName("scm_supplier")
public class Supplier implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
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
    private LocalDateTime licenseExpiresAt;
    private String foodLicenseNo;
    private LocalDateTime foodLicenseExpiresAt;

    /**
     * JSON 字符串（数组）
     */
    private String qualificationFiles;

    private BigDecimal creditScore;
    private BigDecimal scoreQualification;
    private BigDecimal scoreQuality;
    private BigDecimal scorePrice;
    private BigDecimal scoreDelivery;

    private String status;
    private String disableReason;
    private String cancelReason;
    private Long auditBy;
    private LocalDateTime auditAt;
    private String auditRemark;

    private Long orgId;
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
