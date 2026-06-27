package com.xykj.health.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI一键晨检DTO
 * 传入人脸照片即可自动完成全部检查流程
 */
@Data
public class AiCheckDTO {

    /** 人脸照片（Base64编码） */
    @NotBlank(message = "人脸照片不能为空")
    private String faceImage;

    /** 体温（℃），可由设备自动传入，不传则跳过体温判定 */
    private BigDecimal temperature;

    /** 晨检设备ID（可选，用于关联设备采集数据） */
    private Long deviceId;

    /** 组织ID */
    private Long orgId;

    /** 期望的员工ID（可选，传入时进行1:1验证；不传则进行1:N搜索） */
    private Long expectedEmployeeId;

    /** 人脸匹配阈值（默认80） */
    private BigDecimal matchThreshold;

    /** 手部卫生判定：pass/fail（默认pass，人工补充） */
    private String handHygiene;

    /** 着装检查判定：pass/fail（默认pass，人工补充） */
    private String uniformCheck;
}
