package com.xykj.health.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 晨检记录实体
 * 对应数据库表: health_check_record
 * 注意: 该表没有 deleted/created_by/updated_by 字段
 */
@Data
@TableName("health_check_record")
public class HealthCheckRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 晨检编号 */
    private String checkNo;

    /** 员工ID */
    private Long employeeId;

    /** 员工姓名（冗余） */
    private String employeeName;

    /** 晨检日期 */
    private LocalDate checkDate;

    /** 晨检时间 */
    private LocalDateTime checkTime;

    /** 体温（℃） */
    private BigDecimal temperature;

    /** 人脸照片URL */
    private String faceImageUrl;

    /** 人脸匹配度（0-100） */
    private BigDecimal faceMatchScore;

    /** 健康证状态（冗余） */
    private String certificateStatus;

    /** 手部卫生：pass/fail */
    private String handHygiene;

    /** 着装检查：pass/fail */
    private String uniformCheck;

    /** 健康状况：normal/abnormal */
    private String healthStatus;

    /** 晨检结果: pass/fail */
    private String checkResult;

    /** 不通过原因 */
    private String failReason;

    /** 晨检员ID */
    private Long checkerId;

    /** 备注 */
    private String remark;

    /** 状态 */
    private String status;

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
