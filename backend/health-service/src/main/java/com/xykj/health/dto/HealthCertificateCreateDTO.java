package com.xykj.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 健康证创建/更新DTO
 */
@Data
public class HealthCertificateCreateDTO {

    /** 员工ID */
    @NotNull(message = "员工ID不能为空")
    private Long employeeId;

    /** 健康证编号 */
    @NotBlank(message = "健康证编号不能为空")
    private String certificateNo;

    /** 发证日期 */
    @NotNull(message = "发证日期不能为空")
    private LocalDate issueDate;

    /** 到期日期 */
    @NotNull(message = "到期日期不能为空")
    private LocalDate expiryDate;

    /** 健康证照片（JSON数组） */
    private String certificateImages;

    /** 发证机构 */
    private String issuingAuthority;

    /** 到期前预警天数（默认30） */
    private Integer warningDays;

    /** 备注 */
    private String remark;

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
    private Long tenantId;
}
