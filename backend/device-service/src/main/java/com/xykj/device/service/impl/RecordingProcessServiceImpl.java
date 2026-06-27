package com.xykj.device.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.device.entity.DeviceInfo;
import com.xykj.device.mapper.DeviceInfoMapper;
import com.xykj.device.config.StreamConfig;
import com.xykj.device.service.RecordingProcessService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg 录像进程管理实现
 * 为每个启用录像的摄像头启动独立的 FFmpeg 进程，
 * 使用 segment muxer 生成按时间段分割的 MP4 文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingProcessServiceImpl implements RecordingProcessService {

    private final DeviceInfoMapper deviceInfoMapper;
    private final StreamConfig streamConfig;

    private String getRecordingOutputDir() { return streamConfig.getRecording().getOutputDir(); }
    private String getFfmpegPath() { return streamConfig.getFfmpeg().getPath(); }
    private int getSegmentDuration() { return streamConfig.getRecording().getSegmentDuration(); }
    private int getRecordingFps() { return streamConfig.getRecording().getFps(); }

    private static final String PID_FILE_NAME = ".recording.pid";

    /** 设备ID → FFmpeg 录像进程映射 */
    private final ConcurrentHashMap<Long, Process> recordingProcesses = new ConcurrentHashMap<>();

    @Override
    public boolean startRecording(Long deviceId, String rtspUrl) {
        // 停止已有录像进程
        stopRecording(deviceId);

        try {
            // 确保输出目录存在
            Path deviceDir = Paths.get(getRecordingOutputDir(), String.valueOf(deviceId));
            Files.createDirectories(deviceDir);

            String dirPath = deviceDir.toAbsolutePath().toString();
            String segmentPattern = dirPath + File.separator + "rec_%Y%m%d_%H%M%S.mp4";

            // FFmpeg 录像命令：RTSP输入 → MP4分段输出
            // -f segment + segment_time=1800: 每30分钟生成一个MP4文件
            // -segment_atclocktime 1: 按时钟对齐（整点/半点开始）
            // -strftime 1: 文件名支持时间格式化
            // -r 8: 8fps录像，节省CPU
            ProcessBuilder pb = new ProcessBuilder(
                    getFfmpegPath(),
                    "-rtsp_transport", "tcp",
                    "-fflags", "nobuffer",
                    "-flags", "low_delay",
                    "-i", rtspUrl,
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-tune", "zerolatency",
                    "-vf", "scale=1280:720",
                    "-r", String.valueOf(getRecordingFps()),
                    "-an",
                    "-f", "segment",
                    "-segment_time", String.valueOf(getSegmentDuration()),
                    "-segment_atclocktime", "1",
                    "-segment_format", "mp4",
                    "-reset_timestamps", "1",
                    "-strftime", "1",
                    segmentPattern
            );

            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(deviceDir + "/recording.log"));

            Process process = pb.start();
            recordingProcesses.put(deviceId, process);

            // 写入 PID 文件
            writePidFile(deviceId, process);

            // 异步监控进程退出
            Thread monitorThread = new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    recordingProcesses.remove(deviceId);
                    deletePidFile(deviceId);
                    if (exitCode != 0) {
                        log.warn("录像进程异常退出: deviceId={}, exitCode={}", deviceId, exitCode);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "recording-monitor-" + deviceId);
            monitorThread.setDaemon(true);
            monitorThread.start();

            log.info("启动录像进程: deviceId={}, rtspUrl={}, 分段时长={}s, 帧率={}fps",
                    deviceId, rtspUrl, getSegmentDuration(), getRecordingFps());
            return true;

        } catch (IOException e) {
            log.error("启动录像失败: deviceId={}", deviceId, e);
            return false;
        }
    }

    @Override
    public void stopRecording(Long deviceId) {
        Process process = recordingProcesses.remove(deviceId);
        if (process != null && process.isAlive()) {
            process.destroy();
            deletePidFile(deviceId);
            log.info("停止录像: deviceId={}", deviceId);
        }
    }

    @Override
    public boolean isRecording(Long deviceId) {
        Process process = recordingProcesses.get(deviceId);
        return process != null && process.isAlive();
    }

    @Override
    public void startAllRecordings() {
        // 启动前先清理上次服务实例遗留的孤儿进程
        cleanupOrphanProcesses();

        List<DeviceInfo> cameras = deviceInfoMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeviceInfo>()
                        .eq(DeviceInfo::getDeviceType, "camera")
                        .eq(DeviceInfo::getStatus, "active")
                        .eq(DeviceInfo::getOnlineStatus, "online")
        );

        ObjectMapper objectMapper = new ObjectMapper();

        for (DeviceInfo camera : cameras) {
            if (camera.getConfigParams() == null) continue;
            try {
                Map<String, Object> config = objectMapper.readValue(camera.getConfigParams(),
                        new TypeReference<>() {});

                // 只为启用录像的摄像头启动录像进程
                Object recordingEnabled = config.get("recordingEnabled");
                if (recordingEnabled == null || !Boolean.TRUE.equals(Boolean.parseBoolean(recordingEnabled.toString()))) {
                    continue;
                }

                String rtspUrl = (String) config.get("rtspUrl");
                if (rtspUrl == null) {
                    String streamUrl = (String) config.get("streamUrl");
                    if (streamUrl != null && streamUrl.startsWith("rtsp://")) {
                        rtspUrl = streamUrl;
                    }
                }
                if (rtspUrl == null) {
                    for (Map.Entry<String, Object> entry : config.entrySet()) {
                        String key = entry.getKey();
                        if (key == null) continue;
                        String normalizedKey = key.toLowerCase(java.util.Locale.ROOT);
                        if (!normalizedKey.contains("rtsp") && !normalizedKey.contains("stream")) {
                            continue;
                        }
                        Object value = entry.getValue();
                        if (value == null) continue;
                        String candidate = value.toString().trim();
                        if (candidate.startsWith("rtsp://")) {
                            rtspUrl = candidate;
                            break;
                        }
                    }
                }
                if (rtspUrl != null) {
                    boolean started = startRecording(camera.getId(), rtspUrl);
                    if (started) {
                        log.info("自动启动录像: deviceId={}, deviceName={}", camera.getId(), camera.getDeviceName());
                    }
                }
            } catch (Exception e) {
                log.warn("处理摄像头录像启动失败: deviceId={}", camera.getId(), e);
            }
        }
    }

    @Override
    @PreDestroy
    public void stopAllRecordings() {
        log.info("停止所有录像进程, 共{}个", recordingProcesses.size());
        recordingProcesses.forEach((deviceId, process) -> {
            if (process.isAlive()) {
                process.destroy();
            }
            deletePidFile(deviceId);
        });
        recordingProcesses.clear();
    }

    // ========== PID 文件管理 ==========

    private void writePidFile(Long deviceId, Process process) {
        try {
            Path pidFile = Paths.get(getRecordingOutputDir(), String.valueOf(deviceId), PID_FILE_NAME);
            Files.writeString(pidFile, String.valueOf(process.pid()));
        } catch (IOException e) {
            log.warn("写入PID文件失败: deviceId={}", deviceId, e);
        }
    }

    private void deletePidFile(Long deviceId) {
        try {
            Path pidFile = Paths.get(getRecordingOutputDir(), String.valueOf(deviceId), PID_FILE_NAME);
            Files.deleteIfExists(pidFile);
        } catch (IOException e) {
            log.warn("删除PID文件失败: deviceId={}", deviceId, e);
        }
    }

    /**
     * 清理上次服务实例遗留的孤儿 FFmpeg 录像进程
     * 扫描录像输出目录下的 .recording.pid 文件，读取 PID 并 kill 存活的进程
     */
    private void cleanupOrphanProcesses() {
        File outputDir = new File(getRecordingOutputDir());
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
                    log.info("清理孤儿录像进程: deviceId={}, pid={}", deviceDir.getName(), pid);
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
            log.info("录像孤儿进程清理完成: 共清理{}个", cleanedCount);
        }
    }
}
