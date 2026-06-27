package com.xykj.device.vo;

import lombok.Data;

import java.util.List;

/**
 * 设备管理首页看板VO
 */
@Data
public class DeviceDashboardVO {

    /** 设备总数 */
    private Integer totalCount;

    /** 在线数量 */
    private Integer onlineCount;

    /** 离线数量 */
    private Integer offlineCount;

    /** 报警数量 */
    private Integer alertCount;

    /** 维护中数量 */
    private Integer maintenanceCount;

    /** 按设备类型统计 */
    private List<DeviceTypeStat> deviceTypeStats;

    @Data
    public static class DeviceTypeStat {
        private String deviceType;
        private String deviceTypeName;
        private Integer count;
        private Integer onlineCount;
        private Integer offlineCount;
    }
}
