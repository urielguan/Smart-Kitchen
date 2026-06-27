package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceVisionAnalysisTaskVO {

    private Long id;
    private Long screenshotId;
    private Long recordingId;
    private Long deviceId;
    private String deviceName;
    private String taskStatus;
    private String violationType;
    private Integer confidence;
    private String summary;
    private String modelVersion;
    private Long alertId;
    private String errorMessage;
    private LocalDateTime createdAt;
}
