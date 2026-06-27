package com.xykj.health.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 人脸识别结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceRecognizeVO {

    /**
     * 是否成功（技术层面）
     */
    private Boolean success;

    /**
     * 是否通过身份验证（业务层面）
     */
    private Boolean verified;

    /**
     * 匹配的员工ID
     */
    private Long employeeId;

    /**
     * 员工姓名
     */
    private String employeeName;

    /**
     * 匹配度（0-100）
     */
    private BigDecimal matchScore;

    /**
     * 失败原因
     */
    private String failReason;

    /**
     * 人脸质量评分
     */
    private BigDecimal qualityScore;

    /**
     * 是否活体
     */
    private Boolean isLive;
}
