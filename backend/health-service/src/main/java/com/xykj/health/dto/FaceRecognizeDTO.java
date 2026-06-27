package com.xykj.health.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 人脸识别请求DTO
 */
@Data
public class FaceRecognizeDTO {

    /**
     * 人脸照片（Base64编码）
     */
    @NotBlank(message = "人脸照片不能为空")
    private String faceImageBase64;

    /**
     * 期望匹配的员工ID（可选，用于晨检验证身份）
     * 如果传入，则进行1:1验证
     * 如果不传，则进行1:N搜索
     */
    private Long expectedEmployeeId;

    /**
     * 匹配阈值（0-100），默认80
     */
    private Double matchThreshold;

    /**
     * 组织ID（限制1:N搜索范围）
     */
    private Long orgId;
}
