package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 投诉详情VO
 */
@Data
public class ComplaintDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 基础信息 ====================

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
     * 投诉描述
     */
    private String description;

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
     * 关联菜品ID
     */
    private Long relatedMenuId;

    /**
     * 关联菜品名称
     */
    private String relatedMenuName;

    /**
     * 投诉图片
     */
    private List<String> images;

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
     * 满意度
     */
    private String satisfaction;

    /**
     * 满意度名称
     */
    private String satisfactionName;

    /**
     * 满意度备注
     */
    private String satisfactionRemark;

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

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // ==================== 派单信息 ====================

    /**
     * 派单ID（冗余，方便前端判断）
     */
    private Long dispatchId;

    /**
     * 派单信息
     */
    private DispatchInfo dispatch;

    /**
     * 处理记录
     */
    private List<WorkOrderRecordVO> records;

    /**
     * 派单信息内部类
     */
    @Data
    public static class DispatchInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 派单ID
         */
        private Long dispatchId;

        /**
         * 派单编号
         */
        private String dispatchNo;

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
         * 优先级
         */
        private String priority;

        /**
         * 优先级名称
         */
        private String priorityName;

        /**
         * 派单备注
         */
        private String remark;

        /**
         * 派单状态
         */
        private String status;

        /**
         * 派单状态名称
         */
        private String statusName;

        /**
         * 处理结果
         */
        private String handleResult;

        /**
         * 处理图片
         */
        private List<String> handleImages;

        /**
         * 完成时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime completedAt;

        /**
         * 派单时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
