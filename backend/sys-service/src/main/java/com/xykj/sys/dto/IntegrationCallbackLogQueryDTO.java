package com.xykj.sys.dto;

import lombok.Data;

@Data
public class IntegrationCallbackLogQueryDTO {

    private Long orgId;
    private String bizModule;
    private String bizScene;
    private String providerCode;
    private String signResult;
    private String processStatus;
    private String startTime;
    private String endTime;
    private String keyword;
    private Long pageNum = 1L;
    private Long pageSize = 20L;
}
