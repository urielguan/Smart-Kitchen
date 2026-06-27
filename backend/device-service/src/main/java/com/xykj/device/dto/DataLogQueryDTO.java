package com.xykj.device.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 设备数据采集日志查询参数
 */
@Data
public class DataLogQueryDTO {

    private Long pageNum = 1L;
    private Long pageSize = 20L;

    /** 设备ID（必填） */
    private Long deviceId;

    /** 数据类型筛选：temperature/humidity/weight/heartbeat */
    private String dataType;

    /** 采集时间起始 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 采集时间结束 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
