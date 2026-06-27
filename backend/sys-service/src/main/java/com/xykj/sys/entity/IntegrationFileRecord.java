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
@TableName("sys_integration_file_record")
public class IntegrationFileRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bindingId;
    private Long configId;
    private String configNameSnapshot;
    private String bizModule;
    private String bizScene;
    private Long bizId;
    private String bizNo;
    private String providerCode;
    private String providerNameSnapshot;
    private String sourceFileName;
    private String sourceFileUrl;
    private String sourceUrlSignature;
    private String minioFileUrl;
    private String fileHash;
    private String fileSize;
    private String mimeType;
    private String downloadStatus;
    private String storageStatus;
    private String errorCode;
    private String errorMessage;
    private Long taskId;
    private Long syncLogId;
    private Long auditLogId;
    private Long orgId;
    private String orgNameSnapshot;
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
