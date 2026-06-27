package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 投诉VO（列表）
 */
@Data
public class ComplaintVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 投诉ID
     */
    private Long id;

    /**
     * 投诉编号
     */
    private String complaintNo;

    /**
     * 投诉类型
     */
    private String complaintType;

    /**
     * 投诉类型名称
     */
    private String complaintTypeName;

    /**
     * 来源
     */
    private String source;

    /**
     * 来源名称
     */
    private String sourceName;

    /**
     * 投诉标题
     */
    private String title;

    /**
     * 投诉人ID
     */
    private Long submitterId;

    /**
     * 投诉人姓名
     */
    private String submitterName;

    /**
     * 投诉人电话
     */
    private String submitterPhone;

    /**
     * 关联菜品名称
     */
    private String relatedMenuName;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 优先级
     */
    private String priority;

    /**
     * 优先级名称
     */
    private String priorityName;

    /**
     * 派单方式（冗余）
     */
    private String dispatchType;

    /**
     * 派单ID（冗余）
     */
    private Long dispatchId;

    /**
     * 处理人ID（冗余）
     */
    private Long handlerId;

    /**
     * 处理人姓名（冗余）
     */
    private String handlerName;

    /**
     * 截止时间（冗余）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    /**
     * 满意度
     */
    private String satisfaction;

    /**
     * 组织ID
     */
    private Long orgId;

    /**
     * 组织名称
     */
    private String orgName;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
