package com.xykj.health.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康证实体
 * 对应数据库表: health_certificate
 */
@Data
@TableName("health_certificate")
public class HealthCertificate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long employeeId;

    /** 员工姓名（冗余） */
    private String employeeName;

    /** 健康证编号 */
    private String certificateNo;

    /** 发证日期 */
    private LocalDate issueDate;

    /** 到期日期 */
    private LocalDate expiryDate;

    /** 健康证照片（JSON数组） */
    private String certificateImages;

    /** 发证机构 */
    private String issuingAuthority;

    /** 状态：pending/valid/expiring/expired */
    private String status;

    /** 到期前预警天数 */
    private Integer warningDays;

    /** 备注 */
    private String remark;

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
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
