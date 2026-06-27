package com.xykj.device.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 视频录像查询DTO
 */
@Data
public class RecordingQueryDTO {

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 20;

    /** 设备ID */
    private Long deviceId;

    /** 组织ID */
    private Long orgId;

    /** 开始时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 录像类型 (continuous/alarm/manual) */
    private String recordingType;
}
