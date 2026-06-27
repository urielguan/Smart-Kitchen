package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScreenshotVO {

    private Long id;
    private Long recordingId;
    private Long deviceId;
    private String deviceName;
    private Integer captureTimeOffset;
    private String captureTimeFormat;
    private Long fileSize;
    private String fileSizeFormat;
    private String resolution;
    private String purposeTag;
    private String purposeTagName;
    private String status;
    private String statusName;
    private String previewUrl;
    private String downloadUrl;
    private Integer versionNo;
    private String createdByName;
    private LocalDateTime createdAt;
    private DeviceVisionAnalysisTaskVO latestAnalysis;
}
