package com.xykj.device.vo;

import lombok.Data;

/**
 * 监控统计VO
 */
@Data
public class MonitorStatisticsVO {

    /** 摄像头总数 */
    private Integer totalCameras;

    /** 在线数量 */
    private Integer onlineCameras;

    /** 离线数量 */
    private Integer offlineCameras;

    /** 告警数量 */
    private Integer alertCameras;
}
