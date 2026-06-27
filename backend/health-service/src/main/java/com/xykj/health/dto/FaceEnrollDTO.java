package com.xykj.health.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 人脸录入请求DTO
 */
@Data
public class FaceEnrollDTO {

    /**
     * 员工ID
     */
    @NotNull(message = "员工ID不能为空")
    private Long employeeId;

    /**
     * 员工姓名（冗余校验）
     */
    private String employeeName;

    /**
     * 人脸照片（Base64编码）
     */
    @NotBlank(message = "人脸照片不能为空")
    private String faceImageBase64;

    /**
     * 照片来源：web/mobile/terminal
     */
    private String source;

    /**
     * 组织ID
     */
    private Long orgId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 人脸分组ID（用于分组管理）
     */
    private String groupId;
}
