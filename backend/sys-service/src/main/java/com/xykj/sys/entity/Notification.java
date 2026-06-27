package com.xykj.sys.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一消息通知实体
 * 对应数据库表: sys_notification
 */
@Data
@TableName("sys_notification")
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageId;

    private Long tenantId;

    /** 接收人 auth_user.id */
    private Long userId;

    /** 消息大类 */
    private String category;

    /** 消息子类 */
    private String subCategory;

    private String title;

    private String summary;

    private String body;

    /** normal/attention/high/severe */
    private String riskLevel;

    /** unread/read */
    private String readStatus;

    /** pending/processing/processed/closed/invalidated/deleted */
    private String processStatus;

    private String sourceModule;

    private Long relatedBusinessId;

    private String relatedBusinessType;

    private Long relatedOrgId;

    private Long relatedWarehouseId;

    private Long relatedMaterialId;

    private LocalDateTime sendTime;

    private LocalDateTime expiryTime;

    private String pushChannels;

    private String sourceSnapshot;

    private String executableActions;

    private Integer allowDelete;

    private Long orgId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
