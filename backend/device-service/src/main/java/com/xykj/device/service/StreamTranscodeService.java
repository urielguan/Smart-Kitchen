package com.xykj.device.service;

/**
 * RTSP转HLS流媒体转码服务
 */
public interface StreamTranscodeService {

    /**
     * 启动指定摄像头的RTSP→HLS转码
     * @param deviceId 设备ID
     * @param rtspUrl RTSP源地址
     * @return HLS播放地址
     */
    String startTranscode(Long deviceId, String rtspUrl);

    /**
     * 从数据库读取configParams并重启指定摄像头的转码
     * @param deviceId 设备ID
     * @return HLS播放地址，失败返回null
     */
    String restartDeviceTranscode(Long deviceId);

    /**
     * 停止指定摄像头的转码
     */
    void stopTranscode(Long deviceId);

    /**
     * 检查转码进程是否运行中
     */
    boolean isTranscoding(Long deviceId);

    /**
     * 启动所有camera类型设备的转码（服务启动时调用）
     */
    void startAllCameraTranscodes();

    /**
     * 停止所有转码进程（服务关闭时调用）
     */
    void stopAllTranscodes();
}
