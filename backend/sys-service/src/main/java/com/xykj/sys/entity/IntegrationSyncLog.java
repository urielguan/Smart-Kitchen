package com.xykj.sys.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_integration_sync_log")
public class IntegrationSyncLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private Long bindingId;
    private Long configId;
    private String configNameSnapshot;
    private String providerCode;
    private String providerNameSnapshot;
    private Long bizId;
    private String bizNo;
    private String bizModule;
    private String bizScene;
    private String externalNo;
    private String taskType;
    private String requestPayload;
    private String requestHeaders;
    private String requestBody;
    private String responsePayload;
    private String normalizedPayload;
    private String syncStatus;
    private String errorCode;
    private String errorMessage;
    private Long durationMs;
    private Long auditLogId;
    private String resultMessage;
    private String writeBackResult;
    private String triggerType;
    private Long operatorId;
    private String operatorName;
    private Long orgId;
    private String orgNameSnapshot;
    private Long tenantId;
    private String handleStatus;
    private Long handledBy;
    private String handledByName;
    private LocalDateTime handledAt;
    private String handleRemark;

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
