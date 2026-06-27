package com.xykj.sys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class IntegrationSyncLogQueryDTO {

    private Long orgId;
    private String keyword;
    private String bizModule;
    private String bizScene;
    private String providerCode;
    private String syncStatus;
    private String triggerType;
    private String handleStatus;
    private String startTime;
    private String endTime;

    @Min(value = 1, message = "页码必须大于等于1")
    private Long pageNum = 1L;

    @Min(value = 1, message = "每页条数必须大于等于1")
    @Max(value = 200, message = "每页条数不能超过200")
    private Long pageSize = 20L;
}
