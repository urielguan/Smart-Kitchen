package com.xykj.device.service;

/**
 * 录像进程管理服务
 * 管理每个摄像头的 FFmpeg 录像进程（独立于 HLS 直播转码进程）
 */
public interface RecordingProcessService {

    /**
     * 启动指定摄像头的录像
     * @param deviceId 设备ID
     * @param rtspUrl RTSP源地址
     * @return 是否启动成功
     */
    boolean startRecording(Long deviceId, String rtspUrl);

    /**
     * 停止指定摄像头的录像
     */
    void stopRecording(Long deviceId);

    /**
     * 检查指定摄像头是否正在录像
     */
    boolean isRecording(Long deviceId);

    /**
     * 启动所有启用录像的摄像头的录像进程（服务启动时调用）
     */
    void startAllRecordings();

    /**
     * 停止所有录像进程（服务关闭时调用）
     */
    void stopAllRecordings();
}
