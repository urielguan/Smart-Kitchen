package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntegrationFileRecordVO {

    private Long id;
    private Long bindingId;
    private Long configId;
    private String configName;
    private String configNameSnapshot;
    private String bizModule;
    private String bizScene;
    private Long bizId;
    private String bizNo;
    private String providerCode;
    private String providerName;
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
    private String taskNo;
    private Long syncLogId;
    private LocalDateTime syncLogCreatedAt;
    private String syncLogStatus;
    private String syncLogResultMessage;
    private String syncLogErrorMessage;
    private Long auditLogId;
    private Long orgId;
    private String orgName;
    private String orgNameSnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
