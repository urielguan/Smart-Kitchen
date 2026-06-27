package com.xykj.device.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 视频片段截取请求DTO
 */
@Data
public class ClipExtractDTO {

    /** 来源录像ID */
    @NotNull(message = "录像ID不能为空")
    private Long recordingId;

    /** 片段开始时间点 (秒，相对于源录像起始) */
    @NotNull(message = "开始时间不能为空")
    @Min(value = 0, message = "开始时间不能小于0")
    private Integer startTimeOffset;

    /** 片段结束时间点 (秒，相对于源录像起始) */
    @NotNull(message = "结束时间不能为空")
    @Min(value = 1, message = "结束时间不能小于1秒")
    private Integer endTimeOffset;

    /** 用途标签: violation_trace/accident_review/process_review */
    @NotNull(message = "用途标签不能为空")
    private String purposeTag;
}
