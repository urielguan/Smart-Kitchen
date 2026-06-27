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
@TableName("sys_integration_sync_task")
public class IntegrationSyncTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;
    private Long bindingId;
    private Long configId;
    private String taskType;
    private String triggerType;
    private String taskStatus;
    private LocalDateTime planExecuteAt;
    private LocalDateTime startAt;
    private LocalDateTime finishAt;
    private Integer retryCount;
    private Long operatorId;
    private String operatorName;
    private Long bizId;
    private String bizNo;
    private String bizModule;
    private String bizScene;
    private String externalNo;
    private String resultMessage;
    private Long orgId;
    private Long tenantId;

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
