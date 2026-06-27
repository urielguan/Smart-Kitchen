package com.xykj.health.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 执行晨检DTO
 */
@Data
public class HealthCheckCreateDTO {

    /** 员工ID（当不使用人脸识别时必填） */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 晨检日期 */
    private LocalDate checkDate;

    /** 体温（℃） */
    private BigDecimal temperature;

    /** 人脸照片URL（已上传后的URL） */
    private String faceImageUrl;

    /** 人脸照片Base64（用于人脸识别，与faceImageUrl二选一） */
    private String faceImageBase64;

    /** 是否启用人脸识别（默认true） */
    private Boolean enableFaceRecognize;

    /** 人脸匹配度（由系统自动计算） */
    private BigDecimal faceMatchScore;

    /** 人脸识别是否通过 */
    private Boolean faceVerified;

    /** 手部卫生：pass/fail */
    private String handHygiene;

    /** 着装检查：pass/fail */
    private String uniformCheck;

    /** 晨检员ID */
    private Long checkerId;

    /** 备注 */
    private String remark;

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
    private Long tenantId;
}
