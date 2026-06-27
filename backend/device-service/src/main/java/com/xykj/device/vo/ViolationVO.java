package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI违规事件VO
 */
@Data
public class ViolationVO {

    /** 事件ID */
    private Long id;

    /** 违规类型编码 */
    private String violationType;

    /** 违规类型名称 */
    private String violationTypeName;

    /** 涉及人数 */
    private Integer involvedCount;

    /** 告警级别: info/warning/urgent/danger */
    private String alertLevel;

    /** 告警级别名称 */
    private String alertLevelName;

    /** 发生位置 */
    private String location;

    /** 设备ID */
    private Long deviceId;

    /** 设备名称 */
    private String deviceName;

    /** AI识别置信度(0-100) */
    private Integer confidence;

    /** 发生时间 */
    private LocalDateTime occurredAt;

    /** 持续时长(秒) */
    private Integer duration;

    /** 处理状态: pending/assigned/processing/resolved/reviewed */
    private String status;

    /** 状态名称 */
    private String statusName;

    /** 违规视频片段地址 */
    private String videoClipUrl;

    /** 截图地址 */
    private String screenshotUrl;

    /** 处理人ID */
    private Long handlerId;

    /** 处理人姓名 */
    private String handlerName;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 处理备注 */
    private String handleRemark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 关联录像段ID (null=尚未关联) */
    private Long recordingId;

    /** 录像回放地址 (仅detail接口返回) */
    private String recordingPlaybackUrl;

    /** 录像开始时间 */
    private LocalDateTime recordingStartTime;

    /** 录像结束时间 */
    private LocalDateTime recordingEndTime;
}
