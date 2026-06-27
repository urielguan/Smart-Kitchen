package com.xykj.device.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 厨房视觉识别视频流配置
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "vision-stream")
public class VisionStreamConfig {

    /** 是否启用 YOLO 识别流接入 */
    private boolean enabled = true;

    /** 识别服务基础地址 */
    private String baseUrl = "http://127.0.0.1:8081";

    /** 标注后的视频流路径 */
    private String annotatedPath = "/annotated.mjpg";

    /** 原始视频流路径 */
    private String rawPath = "/raw.mjpg";

    /** 默认播放器类型 */
    private String streamType = "mjpeg";

    /** 是否优先展示识别流 */
    private boolean preferAnalysisStream = true;

    @PostConstruct
    public void logConfig() {
        log.info("厨房视觉识别流配置: enabled={}, baseUrl={}, annotatedPath={}, rawPath={}, streamType={}, preferAnalysisStream={}",
            enabled, baseUrl, annotatedPath, rawPath, streamType, preferAnalysisStream);
    }
}
