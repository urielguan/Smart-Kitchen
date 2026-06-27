package com.xykj.device.service.impl;

import com.xykj.device.config.StreamConfig;
import com.xykj.device.entity.DeviceInfo;
import com.xykj.device.mapper.DeviceInfoMapper;
import com.xykj.device.service.StreamTranscodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg RTSP→HLS转码服务实现
 * <p>
 * 为每个摄像头启动一个FFmpeg进程，将RTSP流转码为HLS（.m3u8 + .ts）文件，
 * 通过静态资源映射供前端hls.js播放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamTranscodeServiceImpl implements StreamTranscodeService {

    private final DeviceInfoMapper deviceInfoMapper;
    private final StreamConfig streamConfig;

    private String getHlsOutputDir() { return streamConfig.getHls().getOutputDir(); }
    private String getFfmpegPath() { return streamConfig.getFfmpeg().getPath(); }

    private static final String PID_FILE_NAME = ".hls.pid";

    /** 设备ID → FFmpeg进程映射 */
    private final ConcurrentHashMap<Long, Process> transcodeProcesses = new ConcurrentHashMap<>();

    @Override
    public String startTranscode(Long deviceId, String rtspUrl) {
        // 停止已有进程
        stopTranscode(deviceId);

        try {
            // 确保输出目录存在
            Path deviceDir = Paths.get(getHlsOutputDir(), String.valueOf(deviceId));
            Files.createDirectories(deviceDir);

            String outputPath = deviceDir.resolve("stream.m3u8").toString();

            // FFmpeg命令：RTSP输入 → HLS输出（低延迟优化）
            // 720p缩放 + 1秒分片 + GOP=15降低编码延迟
            boolean isRtsp = rtspUrl.startsWith("rtsp://");
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add(getFfmpegPath());
            if (isRtsp) {
                command.addAll(java.util.List.of("-rtsp_transport", "tcp"));
            } else {
                // 本地文件循环播放，模拟持续流
                command.addAll(java.util.List.of("-stream_loop", "-1", "-re"));
            }
            command.addAll(java.util.List.of(
                    "-fflags", "nobuffer",
                    "-flags", "low_delay",
                    "-i", rtspUrl,
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-tune", "zerolatency",
                    "-vf", "scale=1280:720",
                    "-r", "15",
                    "-g", "15",
                    "-keyint_min", "15",
                    "-an",
                    "-f", "hls",
                    "-hls_time", "1",
                    "-hls_list_size", "3",
                    "-hls_flags", "delete_segments",
                    "-hls_segment_filename", deviceDir.resolve("segment_%03d.ts").toString(),
                    outputPath
            ));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(deviceDir + "/ffmpeg.log"));

            Process process = pb.start();
            transcodeProcesses.put(deviceId, process);

            // 写入 PID 文件
            writePidFile(deviceId, process);

            // 异步监控进程退出
            Thread monitorThread = new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    transcodeProcesses.remove(deviceId);
                    deletePidFile(deviceId);
                    if (exitCode != 0) {
                        log.warn("FFmpeg转码进程异常退出: deviceId={}, exitCode={}", deviceId, exitCode);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "ffmpeg-monitor-" + deviceId);
            monitorThread.setDaemon(true);
            monitorThread.start();

            log.info("启动RTSP→HLS转码: deviceId={}, rtspUrl={}", deviceId, rtspUrl);
            return "/hls/" + deviceId + "/stream.m3u8";

        } catch (IOException e) {
            log.error("启动转码失败: deviceId={}", deviceId, e);
            return null;
        }
    }

    @Override
    public void stopTranscode(Long deviceId) {
        Process process = transcodeProcesses.remove(deviceId);
        if (process != null && process.isAlive()) {
            process.destroy();
            deletePidFile(deviceId);
            log.info("停止转码: deviceId={}", deviceId);
        }
    }

    @Override
    public boolean isTranscoding(Long deviceId) {
        // 第一级：Process 句柄检测（快速路径）
        Process process = transcodeProcesses.get(deviceId);
        if (process != null && process.isAlive()) {
            return true;
        }

        // 第二级：HLS m3u8 文件新鲜度兜底
        // 解决 Windows 上 Process.isAlive() 间歇性返回 false 的问题
        try {
            Path m3u8 = Paths.get(getHlsOutputDir(), String.valueOf(deviceId), "stream.m3u8");
            if (Files.exists(m3u8)) {
                long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(m3u8).toMillis();
                if (ageMs < 30_000) {
                    return true;
                }
            }
        } catch (IOException e) {
            log.warn("检查HLS文件新鲜度失败: deviceId={}", deviceId, e);
        }
        return false;
    }

    @Override
    public void startAllCameraTranscodes() {
        // 启动前先清理上次服务实例遗留的孤儿进程
        cleanupOrphanProcesses();

        List<DeviceInfo> cameras = deviceInfoMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeviceInfo>()
                        .eq(DeviceInfo::getDeviceType, "camera")
                        .eq(DeviceInfo::getStatus, "active")
                        .ne(DeviceInfo::getOnlineStatus, "fault")
                        .eq(DeviceInfo::getDeleted, 0)
        );

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (DeviceInfo camera : cameras) {
            if (camera.getConfigParams() == null) continue;
            try {
                Map<String, Object> config = objectMapper.readValue(camera.getConfigParams(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});
                String rtspUrl = resolveRtspUrl(config);
                if (rtspUrl != null) {
                    String hlsUrl = startTranscode(camera.getId(), rtspUrl);
                    if (hlsUrl != null) {
                        config.put("streamUrl", hlsUrl);
                        config.put("rtspUrl", rtspUrl);
                        camera.setConfigParams(objectMapper.writeValueAsString(config));
                        deviceInfoMapper.updateById(camera);
                        log.info("更新摄像头HLS地址: deviceId={}, hlsUrl={}", camera.getId(), hlsUrl);
                    }
                }
            } catch (Exception e) {
                log.warn("处理摄像头转码失败: deviceId={}", camera.getId(), e);
            }
        }
    }

    @Override
    public String restartDeviceTranscode(Long deviceId) {
        DeviceInfo camera = deviceInfoMapper.selectById(deviceId);
        if (camera == null || !"camera".equals(camera.getDeviceType())) {
            log.warn("设备不是摄像头类型或不存在: deviceId={}", deviceId);
            return null;
        }
        if (camera.getConfigParams() == null) {
            log.warn("摄像头缺少configParams: deviceId={}", deviceId);
            return null;
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> config = objectMapper.readValue(camera.getConfigParams(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
            String rtspUrl = resolveRtspUrl(config);
            if (rtspUrl == null) {
                log.warn("摄像头缺少rtspUrl: deviceId={}", deviceId);
                return null;
            }

            String hlsUrl = startTranscode(deviceId, rtspUrl);
            if (hlsUrl == null) {
                return null;
            }

            config.put("streamUrl", hlsUrl);
            config.put("rtspUrl", rtspUrl);
            try {
                camera.setConfigParams(objectMapper.writeValueAsString(config));
                deviceInfoMapper.updateById(camera);
            } catch (Exception persistError) {
                log.warn("重启转码后回写HLS地址失败: deviceId={}, hlsUrl={}", deviceId, hlsUrl, persistError);
            }
            log.info("重启转码成功: deviceId={}, hlsUrl={}", deviceId, hlsUrl);
            return hlsUrl;
        } catch (Exception e) {
            log.error("重启转码失败: deviceId={}", deviceId, e);
            return null;
        }
    }

    private String resolveRtspUrl(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }

        String rtspUrl = getString(config, "rtspUrl");
        if (rtspUrl != null) {
            return rtspUrl;
        }

        String streamUrl = getString(config, "streamUrl");
        if (isRtspUrl(streamUrl)) {
            return streamUrl;
        }

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            String normalizedKey = key.toLowerCase(Locale.ROOT);
            if (!normalizedKey.contains("rtsp") && !normalizedKey.contains("stream")) {
                continue;
            }
            String candidate = entry.getValue() == null ? null : entry.getValue().toString().trim();
            if (isRtspUrl(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private String getString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isRtspUrl(String value) {
        return value != null && value.startsWith("rtsp://");
    }

    @Override
    @PreDestroy
    public void stopAllTranscodes() {
        log.info("停止所有FFmpeg转码进程, 共{}个", transcodeProcesses.size());
        transcodeProcesses.forEach((deviceId, process) -> {
            if (process.isAlive()) {
                process.destroy();
            }
            deletePidFile(deviceId);
        });
        transcodeProcesses.clear();
    }

    // ========== PID 文件管理 ==========

    private void writePidFile(Long deviceId, Process process) {
        try {
            Path pidFile = Paths.get(getHlsOutputDir(), String.valueOf(deviceId), PID_FILE_NAME);
            Files.writeString(pidFile, String.valueOf(process.pid()));
        } catch (IOException e) {
            log.warn("写入PID文件失败: deviceId={}", deviceId, e);
        }
    }

    private void deletePidFile(Long deviceId) {
        try {
            Path pidFile = Paths.get(getHlsOutputDir(), String.valueOf(deviceId), PID_FILE_NAME);
            Files.deleteIfExists(pidFile);
        } catch (IOException e) {
            log.warn("删除PID文件失败: deviceId={}", deviceId, e);
        }
    }

    /**
     * 清理上次服务实例遗留的孤儿 FFmpeg 进程
     * 扫描 HLS 输出目录下的 .hls.pid 文件，读取 PID 并 kill 存活的进程
     */
    private void cleanupOrphanProcesses() {
        File outputDir = new File(getHlsOutputDir());
        if (!outputDir.exists()) return;

        File[] deviceDirs = outputDir.listFiles(File::isDirectory);
        if (deviceDirs == null) return;

        int cleanedCount = 0;
        for (File deviceDir : deviceDirs) {
            File pidFile = new File(deviceDir, PID_FILE_NAME);
            if (!pidFile.exists()) continue;

            try {
                long pid = Long.parseLong(Files.readString(pidFile.toPath()).trim());
                Optional<ProcessHandle> handle = ProcessHandle.of(pid);
                if (handle.isPresent() && handle.get().isAlive()) {
                    handle.get().destroy();
                    log.info("清理孤儿HLS转码进程: deviceId={}, pid={}", deviceDir.getName(), pid);
                    handle.get().onExit().get(2, TimeUnit.SECONDS);
                    cleanedCount++;
                }
            } catch (Exception e) {
                log.warn("清理PID文件失败: {}", pidFile.getAbsolutePath(), e);
            } finally {
                pidFile.delete();
            }
        }

        if (cleanedCount > 0) {
            log.info("HLS孤儿进程清理完成: 共清理{}个", cleanedCount);
        }
    }
}
