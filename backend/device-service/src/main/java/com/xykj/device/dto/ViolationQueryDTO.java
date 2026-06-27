package com.xykj.device.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * AI违规事件查询DTO
 */
@Data
public class ViolationQueryDTO {

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 设备ID */
    private Long deviceId;

    /** 组织ID */
    private Long orgId;

    /** 违规类型: no_mask/no_hat/smoking/phone/outsider */
    private String violationType;

    /** 告警级别: info/warning/urgent/danger */
    private String alertLevel;

    /** 处理状态: pending/assigned/processing/resolved/reviewed */
    private String status;

    /** 开始时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
