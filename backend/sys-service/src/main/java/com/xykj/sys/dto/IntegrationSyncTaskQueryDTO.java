package com.xykj.sys.dto;

import lombok.Data;

@Data
public class IntegrationSyncTaskQueryDTO {

    private Long orgId;
    private String keyword;
    private String bizModule;
    private String bizScene;
    private String providerCode;
    private String taskStatus;
    private String triggerType;
    private Integer pendingHandleOnly = 0;
    private Long pageNum = 1L;
    private Long pageSize = 20L;
}
