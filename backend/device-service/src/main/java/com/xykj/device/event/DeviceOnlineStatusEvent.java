package com.xykj.device.event;

import org.springframework.context.ApplicationEvent;

/**
 * 设备在线状态变更事件，用于SSE推送
 */
public class DeviceOnlineStatusEvent extends ApplicationEvent {

    private final Long deviceId;
    private final String deviceName;
    private final String deviceType;
    private final String oldStatus;
    private final String newStatus;

    public DeviceOnlineStatusEvent(Object source, Long deviceId, String deviceName,
                                   String deviceType, String oldStatus, String newStatus) {
        super(source);
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public Long getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getDeviceType() { return deviceType; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
}
