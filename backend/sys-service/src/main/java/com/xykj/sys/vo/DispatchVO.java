package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 派单VO（列表）
 */
@Data
public class DispatchVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 派单ID
     */
    private Long id;

    /**
     * 派单编号
     */
    private String dispatchNo;

    /**
     * 关联投诉ID
     */
    private Long complaintId;

    /**
     * 投诉编号
     */
    private String complaintNo;

    /**
     * 投诉标题
     */
    private String complaintTitle;

    /**
     * 派单方式
     */
    private String dispatchType;

    /**
     * 派单方式名称
     */
    private String dispatchTypeName;

    /**
     * 派单人ID
     */
    private Long assignerId;

    /**
     * 派单人姓名
     */
    private String assignerName;

    /**
     * 处理人ID
     */
    private Long handlerId;

    /**
     * 处理人姓名
     */
    private String handlerName;

    /**
     * 截止时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    /**
     * 派单备注
     */
    private String remark;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 优先级（来自投诉）
     */
    private String priority;

    /**
     * 优先级名称
     */
    private String priorityName;

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
