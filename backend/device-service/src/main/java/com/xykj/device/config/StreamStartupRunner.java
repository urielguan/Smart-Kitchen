package com.xykj.device.config;

import com.xykj.device.service.StreamTranscodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 服务启动后自动启动所有在线摄像头的RTSP→HLS转码
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamStartupRunner {

    private final StreamTranscodeService streamTranscodeService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("===== 启动摄像头RTSP→HLS转码 =====");
        try {
            streamTranscodeService.startAllCameraTranscodes();
        } catch (Exception e) {
            log.warn("启动转码失败（可能FFmpeg未安装）", e);
        }
    }
}
