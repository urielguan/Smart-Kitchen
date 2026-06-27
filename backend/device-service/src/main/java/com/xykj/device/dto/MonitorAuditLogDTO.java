package com.xykj.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 前端操作审计日志DTO
 * 用于记录前端操作（画面切换、布局变更等）
 */
@Data
public class MonitorAuditLogDTO {

    @NotBlank(message = "操作动作不能为空")
    private String action;

    private Long deviceId;

    private String deviceName;

    private Long recordingId;

    private String extra;
}
