package com.xykj.sys.vo;

import lombok.Data;

@Data
public class IntegrationExecutionResultVO {

    private Long taskId;
    private String taskNo;
    private Long bindingId;
    private String syncStatus;
    private String message;
    private String normalizedPayload;
    private Integer downloadedFileCount;
    private Integer failedFileCount;
    private Integer skippedFileCount;
    private Integer reusedFileCount;
    private String fileTransferSummary;
    private Long syncLogId;
    private Long auditLogId;
    private String writeBackResult;
}
