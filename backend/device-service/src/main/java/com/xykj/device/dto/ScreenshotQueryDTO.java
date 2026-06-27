package com.xykj.device.dto;

import lombok.Data;

@Data
public class ScreenshotQueryDTO {

    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private Long recordingId;
    private Long deviceId;
    private String purposeTag;
}
