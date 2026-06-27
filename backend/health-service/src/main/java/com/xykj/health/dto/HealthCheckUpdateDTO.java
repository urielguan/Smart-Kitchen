package com.xykj.health.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 晨检记录更新DTO
 */
@Data
public class HealthCheckUpdateDTO {

    /** 体温（℃） */
    private BigDecimal temperature;

    /** 人脸照片URL */
    private String faceImageUrl;

    /** 人脸匹配度 */
    private BigDecimal faceMatchScore;

    /** 手部卫生: pass/fail */
    private String handHygiene;

    /** 着装检查: pass/fail */
    private String uniformCheck;

    /** 健康状况: normal/abnormal */
    private String healthStatus;

    /** 不通过原因 */
    private String failReason;

    /** 备注 */
    private String remark;
}
