package com.xykj.device.dto;

import lombok.Data;

import java.util.List;

/**
 * 告警查询DTO
 */
@Data
public class AlertQueryDTO {

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 20;

    /** 组织ID */
    private Long orgId;

    /** 数据权限注入组织ID列表 */
    private List<Long> orgIds;

    /** 告警类型 */
    private String alertType;

    /** 告警级别 */
    private String alertLevel;

    /** 状态 */
    private String status;

    /** 设备ID */
    private Long deviceId;

    /** 处理人ID */
    private Long assignedTo;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;

    /** 是否显示被风暴抑制的告警（默认 false，不显示） */
    private Boolean showSuppressed = false;
}
