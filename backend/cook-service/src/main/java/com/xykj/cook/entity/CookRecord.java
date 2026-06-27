package com.xykj.cook.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 烹饪记录实体
 * 对应数据库表: cook_record
 */
@Data
@TableName("cook_record")
public class CookRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联烹饪任务ID
     */
    private Long taskId;

    /**
     * 记录类型：start=开始，complete=完成，cancel=取消，temperature=温度，ai_alert=AI告警
     */
    private String recordType;

    /**
     * 记录内容（JSON）
     */
    private String content;

    /**
     * 记录时间
     */
    private LocalDateTime recordTime;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人姓名
     */
    private String operatorName;

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
