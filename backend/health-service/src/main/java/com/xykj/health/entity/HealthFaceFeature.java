package com.xykj.health.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人脸特征实体
 * 对应数据库表: health_face_feature
 */
@Data
@TableName("health_face_feature")
public class HealthFaceFeature implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long employeeId;

    /** 人脸照片URL */
    private String faceImageUrl;

    /** 人脸特征向量（加密存储） */
    private String faceFeatureVector;

    /** 特征提取算法版本 */
    private String featureVersion;

    /** 照片质量评分（0-100） */
    private BigDecimal qualityScore;

    /** 是否启用：0=否，1=是 */
    private Integer isActive;

    /** 录入时间 */
    private LocalDateTime enrolledAt;

    /** 最后使用时间 */
    private LocalDateTime lastUsedAt;

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
