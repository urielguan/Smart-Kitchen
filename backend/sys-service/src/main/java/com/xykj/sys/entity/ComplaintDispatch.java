package com.xykj.sys.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投诉派单实体类
 * 对应数据库表: sys_complaint_dispatch
 */
@Data
@TableName("sys_complaint_dispatch")
public class ComplaintDispatch implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 派单ID
     */
    @TableId(type = IdType.AUTO)
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
     * 投诉编号（冗余）
     */
    private String complaintNo;

    /**
     * 投诉标题（冗余）
     */
    private String complaintTitle;

    /**
     * 派单方式：auto=自动派单，manual=人工派单
     */
    private String dispatchType;

    /**
     * 派单人ID（人工派单时）
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
    private LocalDateTime deadline;

    /**
     * 优先级：high/medium/low
     */
    private String priority;

    /**
     * 派单备注
     */
    private String remark;

    /**
     * 状态：pending=待处理，processing=处理中，completed=已完成，cancelled=已取消
     */
    private String status;

    /**
     * 处理结果
     */
    private String handleResult;

    /**
     * 处理图片（JSON数组）
     */
    private String handleImages;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0=未删除，1=已删除
     */
    @TableLogic
    private Integer deleted;
}
