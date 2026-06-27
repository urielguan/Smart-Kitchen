package com.xykj.sys.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投诉实体类
 * 对应数据库表: sys_complaint
 */
@Data
@TableName("sys_complaint")
public class Complaint implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 投诉ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 投诉编号
     */
    private String complaintNo;

    /**
     * 投诉类型：food=食品问题，service=服务问题，hygiene=卫生问题，other=其他
     */
    private String complaintType;

    /**
     * 来源：meal=用餐评价，supervision=监管反馈，manual=人工录入
     */
    private String source;

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
     * 投诉图片（JSON数组）
     */
    private String images;

    /**
     * 状态：pending=待处理，dispatched=已派单，processing=处理中，closed=已闭环
     */
    private String status;

    /**
     * 优先级：high=高，medium=中，low=低
     */
    private String priority;

    /**
     * 满意度：satisfied=满意，neutral=一般，dissatisfied=不满意
     */
    private String satisfaction;

    /**
     * 满意度备注
     */
    private String satisfactionRemark;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

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
