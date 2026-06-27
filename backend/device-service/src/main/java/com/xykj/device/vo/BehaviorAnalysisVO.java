package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI人员行为分析VO
 */
@Data
public class BehaviorAnalysisVO {

    /** 记录ID */
    private Long id;

    /** 员工ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 员工工号 */
    private String employeeCode;

    /** 员工角色/岗位 */
    private String employeeRole;

    /** 所属部门 */
    private String department;

    /** 头像 */
    private String avatar;

    /** 效率评分(0-100) */
    private Integer efficiencyScore;

    /** 合规评分(0-100) */
    private Integer complianceScore;

    /** 卫生评分(0-100) */
    private Integer hygieneScore;

    /** 守时评分(0-100) */
    private Integer punctualityScore;

    /** 团队协作评分(0-100) */
    private Integer teamworkScore;

    /** 综合评分(0-100) */
    private Integer overallScore;

    /** 工作时长(分钟) */
    private Integer workDuration;

    /** 操作次数 */
    private Integer operationCount;

    /** 违规次数 */
    private Integer violationCount;

    /** 是否存在问题 */
    private Boolean hasIssues;

    /** 标签列表 */
    private List<String> tags;

    /** 问题列表 */
    private List<BehaviorIssueVO> issues;

    /** AI培训建议 */
    private List<String> aiTrainingSuggestions;

    /** 分析时间段开始 */
    private LocalDateTime periodStart;

    /** 分析时间段结束 */
    private LocalDateTime periodEnd;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
