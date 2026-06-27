package com.xykj.health.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI一键晨检结果VO
 * 包含完整的人脸识别、体温、健康证、手部卫生、着装检查等所有检查项结果
 */
@Data
@Builder
public class AiCheckResultVO {

    /** 晨检记录ID */
    private Long checkId;

    /** 晨检编号 */
    private String checkNo;

    // ==================== 员工信息 ====================

    /** 员工ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 职位 */
    private String position;

    /** 工号 */
    private String employeeNo;

    /** 员工头像URL */
    private String avatarUrl;

    // ==================== 人脸识别结果 ====================

    /** 人脸匹配度（0-100） */
    private BigDecimal faceMatchScore;

    /** 人脸匹配结果：pass/fail */
    private String faceMatchResult;

    /** 人脸照片URL */
    private String faceImageUrl;

    // ==================== 体温检查结果 ====================

    /** 检测时间 */
    private LocalDateTime checkTime;

    /** 体温（℃） */
    private BigDecimal temperature;

    /** 体温状态：normal/low/high */
    private String tempStatus;

    /** 体温检查结果：pass/fail */
    private String tempCheckResult;

    // ==================== 健康证检查结果 ====================

    /** 健康证编号 */
    private String certNo;

    /** 健康证到期日期 */
    private LocalDate certExpiryDate;

    /** 健康证状态：valid/expiring/expired/pending */
    private String certStatus;

    /** 健康证检查结果：pass/fail */
    private String certCheckResult;

    /** 健康证检查信息（如异常说明） */
    private String certCheckMessage;

    // ==================== 手部卫生结果 ====================

    /** 手部卫生：pass/fail */
    private String handHygiene;

    /** 手部卫生异常说明 */
    private String handHygieneMessage;

    // ==================== 着装检查结果 ====================

    /** 着装检查：pass/fail */
    private String uniformCheck;

    /** 着装检查异常说明 */
    private String uniformCheckMessage;

    // ==================== 综合结果 ====================

    /** 晨检结果：pass/fail */
    private String checkResult;

    /** 不通过原因列表 */
    private List<String> failReasons;

    /** 是否有预警 */
    private Boolean hasWarning;

    /** 预警信息列表 */
    private List<String> warningMessages;
}
