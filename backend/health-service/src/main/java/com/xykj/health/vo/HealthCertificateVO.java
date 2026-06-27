package com.xykj.health.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康证VO
 */
@Data
@Builder
public class HealthCertificateVO {

    private Long id;

    private Long employeeId;

    private String employeeName;

    /** 健康证编号 */
    private String certificateNo;

    /** 发证日期 */
    private LocalDate issueDate;

    /** 到期日期 */
    private LocalDate expiryDate;

    /** 健康证照片 */
    private String certificateImages;

    /** 发证机构 */
    private String issuingAuthority;

    /** 状态：pending/valid/expiring/expired */
    private String status;

    /** 到期前预警天数 */
    private Integer warningDays;

    /** 剩余有效天数 */
    private Long remainingDays;

    /** 备注 */
    private String remark;

    private Long orgId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
