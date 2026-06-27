package com.xykj.device.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 流媒体配置（RTSP→HLS转码 + 录像）
 * 通过 Nacos 配置中心管理，支持动态刷新
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "stream")
public class StreamConfig {

    private static final String DEFAULT_TEMP_HLS_DIR = System.getProperty("java.io.tmpdir") + "/hls";
    private static final String DEFAULT_TEMP_RECORDING_DIR = System.getProperty("java.io.tmpdir") + "/recordings";
    private static final List<String> WINDOWS_HLS_DIR_CANDIDATES = List.of("C:/hls");
    private static final List<String> WINDOWS_RECORDING_DIR_CANDIDATES = List.of("C:/recordings");

    private Ffmpeg ffmpeg = new Ffmpeg();
    private Hls hls = new Hls();
    private Recording recording = new Recording();

    @Data
    public static class Ffmpeg {
        private static final List<String> WINDOWS_FFMPEG_CANDIDATES = List.of(
                "C:/ffmpeg/bin/ffmpeg.exe",
                "C:/Program Files/ffmpeg/bin/ffmpeg.exe"
        );

        /** FFmpeg 可执行文件路径 */
        private String path = "ffmpeg";

        public String getPath() {
            if (path != null && !path.isBlank() && !"ffmpeg".equalsIgnoreCase(path.trim())) {
                return path;
            }

            for (String candidate : WINDOWS_FFMPEG_CANDIDATES) {
                if (Files.exists(Path.of(candidate))) {
                    return candidate;
                }
            }
            return path;
        }
    }

    @Data
    public static class Hls {
        /** HLS 输出目录 */
        private String outputDir = DEFAULT_TEMP_HLS_DIR;

        public String getOutputDir() {
            return resolveOutputDir(outputDir, DEFAULT_TEMP_HLS_DIR, WINDOWS_HLS_DIR_CANDIDATES);
        }
    }

    @Data
    public static class Recording {
        /** 录像输出目录 */
        private String outputDir = DEFAULT_TEMP_RECORDING_DIR;
        /** 分段时长（秒），默认30分钟 */
        private int segmentDuration = 1800;
        /** 录像帧率 */
        private int fps = 8;
        /** 普通录像保留天数 */
        private int retentionDays = 30;
        /** 证据录像保留天数 */
        private int evidenceRetentionDays = 365;
        /** 证据包下载链接有效期（小时） */
        private int evidencePackageExpireHours = 72;
        /** 单个证据包最大文件数 */
        private int evidencePackageMaxItems = 50;

        public String getOutputDir() {
            return resolveOutputDir(outputDir, DEFAULT_TEMP_RECORDING_DIR, WINDOWS_RECORDING_DIR_CANDIDATES);
        }
    }

    private static String resolveOutputDir(String configuredPath, String defaultPath, List<String> windowsCandidates) {
        if (configuredPath != null && !configuredPath.isBlank() && !defaultPath.equals(configuredPath.trim())) {
            return configuredPath;
        }

        for (String candidate : windowsCandidates) {
            if (Files.exists(Path.of(candidate))) {
                return candidate;
            }
        }
        return configuredPath;
    }

    @PostConstruct
    public void logConfig() {
        log.info("流媒体配置: ffmpeg={}, hlsOutput={}, recordingOutput={}, segmentDuration={}s, fps={}, retentionDays={}",
                ffmpeg.path, hls.outputDir, recording.outputDir,
                recording.segmentDuration, recording.fps, recording.retentionDays);
    }
}
