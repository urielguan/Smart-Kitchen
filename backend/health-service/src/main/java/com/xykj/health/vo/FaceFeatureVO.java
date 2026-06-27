package com.xykj.health.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人脸特征信息VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceFeatureVO {

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 员工ID
     */
    private Long employeeId;

    /**
     * 员工姓名
     */
    private String employeeName;

    /**
     * 人脸照片URL
     */
    private String faceImageUrl;

    /**
     * 照片质量评分（0-100）
     */
    private BigDecimal qualityScore;

    /**
     * 是否启用
     */
    private Integer isActive;

    /**
     * 录入时间
     */
    private LocalDateTime enrolledAt;

    /**
     * 最后使用时间
     */
    private LocalDateTime lastUsedAt;

    /**
     * 特征版本
     */
    private String featureVersion;

    /**
     * 组织ID
     */
    private Long orgId;
}
