package com.xykj.health.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 晨检记录列表项VO
 */
@Data
public class HealthCheckRecordVO {

    private Long id;

    /** 晨检编号 */
    private String checkNo;

    /** 员工ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 员工头像URL */
    private String avatarUrl;

    /** 职位 */
    private String position;

    /** 工号 */
    private String employeeNo;

    /** 是否已录入人脸 */
    private Boolean hasFaceData;

    /** 健康证到期日期 */
    private LocalDate certExpiryDate;

    /** 晨检日期 */
    private LocalDate checkDate;

    /** 晨检时间 */
    private LocalDateTime checkTime;

    /** 体温（℃） */
    private BigDecimal temperature;

    /** 人脸匹配度 */
    private BigDecimal faceMatchScore;

    /** 健康证状态 */
    private String certificateStatus;

    /** 手部卫生 */
    private String handHygiene;

    /** 着装检查 */
    private String uniformCheck;

    /** 健康状况 */
    private String healthStatus;

    /** 晨检结果 */
    private String checkResult;

    /** 不通过原因 */
    private String failReason;

    /** 晨检员ID */
    private Long checkerId;

    /** 状态 */
    private String status;

    /** 是否仍纳入当日应检 */
    private Boolean shouldCheck;

    /** 标签：formal=正式在岗，substitute=临时替班 */
    private String dutyType;

    /** 标签名称 */
    private String dutyTypeName;

    /** 当前归属组织ID */
    private Long currentOrgId;

    /** 当前归属组织名称 */
    private String currentOrgName;

    /** 当前联动说明 */
    private String linkageReason;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
