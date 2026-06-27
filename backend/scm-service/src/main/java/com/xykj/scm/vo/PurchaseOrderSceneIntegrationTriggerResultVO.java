package com.xykj.scm.vo;

import lombok.Data;

@Data
public class PurchaseOrderSceneIntegrationTriggerResultVO {

    private Long taskId;
    private String taskNo;
    private Long bindingId;
    private String syncStatus;
    private String message;
    private String normalizedPayload;
    private Integer downloadedFileCount;
    private PurchaseOrderSceneIntegrationBindingVO binding;
}
