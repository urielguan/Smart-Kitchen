package com.xykj.device.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 截图上传请求DTO
 */
@Data
public class ScreenshotUploadDTO {

    @NotNull(message = "录像ID不能为空")
    private Long recordingId;

    @NotNull(message = "抓拍时间点不能为空")
    @Min(value = 0, message = "抓拍时间点不能小于0")
    private Integer captureTimeOffset;

    @NotNull(message = "用途标签不能为空")
    private String purposeTag;
}
