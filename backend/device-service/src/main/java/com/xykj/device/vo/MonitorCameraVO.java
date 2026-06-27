package com.xykj.device.vo;

import lombok.Data;

/**
 * 监控摄像头VO
 */
@Data
public class MonitorCameraVO {

    /** 设备ID */
    private Long id;

    /** 设备编码 */
    private String deviceCode;

    /** 设备名称 */
    private String deviceName;

    /** 安装位置 */
    private String location;

    /** 分辨率 */
    private String resolution;

    /** 帧率 */
    private Integer frameRate;

    /** 在线状态：online/offline */
    private String onlineStatus;

    /** 设备状态：active/inactive/maintenance */
    private String status;

    /** 告警数量 */
    private Integer alertCount;

    /** 最后更新时间 */
    private String lastUpdatedAt;

    /** 流媒体地址 */
    private String streamUrl;

    /** AI识别结果流地址 */
    private String analysisStreamUrl;

    /** AI识别结果流类型 */
    private String analysisStreamType;

    /** 是否优先展示AI识别流 */
    private Boolean preferAnalysisStream;

    /** 缩略图URL */
    private String thumbnailUrl;

    /** 是否支持云台控制 */
    private Boolean ptzSupport;
}
