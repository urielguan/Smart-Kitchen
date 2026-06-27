package com.xykj.device.dto;

import lombok.Data;

/**
 * 监控审计日志查询DTO
 */
@Data
public class MonitorAuditLogQueryDTO {

    private Integer pageNum = 1;
    private Integer pageSize = 10;

    private String operationType;
    private String moduleCode;
    private String sourceTerminal;
    private Long deviceId;
    private Long recordingId;
    private Long orgId;

    private String startTime;
    private String endTime;
}
