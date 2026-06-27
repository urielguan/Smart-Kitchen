package com.xykj.sys.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单处理记录实体类
 * 对应数据库表: sys_work_order_record
 */
@Data
@TableName("sys_work_order_record")
public class WorkOrderRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联派单ID
     */
    private Long dispatchId;

    /**
     * 关联投诉ID
     */
    private Long complaintId;

    /**
     * 操作类型：dispatch=派单，reassign=改派，process=处理，complete=完成，cancel=取消
     */
    private String action;

    /**
     * 操作名称
     */
    private String actionName;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人姓名
     */
    private String operatorName;

    /**
     * 操作内容
     */
    private String content;

    /**
     * 处理图片（JSON数组）
     */
    private String images;

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
}
