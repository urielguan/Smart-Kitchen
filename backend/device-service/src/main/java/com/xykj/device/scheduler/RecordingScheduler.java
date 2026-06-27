package com.xykj.device.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.device.config.StreamConfig;
import com.xykj.device.entity.*;
import com.xykj.device.mapper.*;
import com.xykj.device.service.RecordingProcessService;
import com.xykj.device.service.StreamTranscodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 录像定时任务调度器
 * - 启动时自动开始所有启用录像的摄像头
 * - 每2分钟检查 FFmpeg 进程健康状态，自动重启死掉的进程
 * - 每3分钟扫描录像目录，创建/更新数据库记录
 * - 每6小时清理30天前的录像文件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingScheduler {

    private final DeviceMonitorRecordMapper recordMapper;
    private final DeviceInfoMapper deviceInfoMapper;
    private final RecordingProcessService recordingProcessService;
    private final StreamTranscodeService streamTranscodeService;
    private final DeviceVideoClipMapper clipMapper;
    private final DeviceScreenshotMapper screenshotMapper;
    private final DeviceAlertMapper alertMapper;
    private final DeviceCleanupAuditLogMapper auditLogMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final StreamConfig streamConfig;

    private String getRecordingOutputDir() { return streamConfig.getRecording().getOutputDir(); }
    private String getFfmpegPath() { return streamConfig.getFfmpeg().getPath(); }

    /** 标记启动流程是否已完成，防止定时任务与启动监听器并发 */
    private volatile boolean startupComplete = false;

    private int getRetentionDays() { return streamConfig.getRecording().getRetentionDays(); }
    private int getEvidenceRetentionDays() { return streamConfig.getRecording().getEvidenceRetentionDays(); }
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 服务启动时自动开始录像
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            recordingProcessService.startAllRecordings();
            log.info("录像自动启动任务完成");
        } catch (Exception e) {
            log.warn("启动录像进程失败（可能FFmpeg未安装）", e);
        } finally {
            startupComplete = true;
        }
    }

    /**
     * 每2分钟检查 FFmpeg 进程健康状态
     * 检测死掉的 HLS 转码进程和录像进程，自动重启
     */
    @Scheduled(cron = "0 */2 * * * ?")
    public void healthCheck() {
        if (!startupComplete) return;

        List<DeviceInfo> cameras = deviceInfoMapper.selectList(
                new LambdaQueryWrapper<DeviceInfo>()
                        .eq(DeviceInfo::getDeviceType, "camera")
                        .eq(DeviceInfo::getStatus, "active")
                        .eq(DeviceInfo::getOnlineStatus, "online")
                        .eq(DeviceInfo::getDeleted, 0));

        for (DeviceInfo camera : cameras) {
            Long deviceId = camera.getId();
            String configParams = camera.getConfigParams();
            if (configParams == null) continue;

            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<String, Object> config = om.readValue(configParams,
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});

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
                if (rtspUrl == null) continue;

                // 检查 HLS 转码进程是否存活
                if (!streamTranscodeService.isTranscoding(deviceId)) {
                    log.info("健康检查: HLS转码进程已死, 重启 deviceId={}", deviceId);
                    String hlsUrl = streamTranscodeService.startTranscode(deviceId, rtspUrl);
                    if (hlsUrl != null) {
                        config.put("streamUrl", hlsUrl);
                        String newConfigParams = om.writeValueAsString(config);
                        // 只更新 configParams，避免全字段 updateById 覆盖 onlineStatus
                        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DeviceInfo> updateWrapper =
                                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                        updateWrapper.eq(DeviceInfo::getId, deviceId)
                                .set(DeviceInfo::getConfigParams, newConfigParams);
                        deviceInfoMapper.update(null, updateWrapper);
                    }
                }

                // 检查录像进程是否存活（仅对启用了录像的摄像头）
                Object recordingEnabled = config.get("recordingEnabled");
                if (recordingEnabled != null && Boolean.TRUE.equals(Boolean.parseBoolean(recordingEnabled.toString()))) {
                    if (!recordingProcessService.isRecording(deviceId)) {
                        log.info("健康检查: 录像进程已死, 重启 deviceId={}", deviceId);
                        recordingProcessService.startRecording(deviceId, rtspUrl);
                    }
                }
            } catch (Exception e) {
                log.warn("健康检查失败: deviceId={}", deviceId, e);
            }
        }
    }

    /**
     * 每3分钟扫描录像目录，同步元数据到数据库
     */
    @Scheduled(cron = "0 */3 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void scanRecordingMetadata() {
        if (!startupComplete) return;

        File outputDir = new File(getRecordingOutputDir());
        if (!outputDir.exists()) return;

        File[] deviceDirs = outputDir.listFiles(File::isDirectory);
        if (deviceDirs == null) return;

        for (File deviceDir : deviceDirs) {
            String dirName = deviceDir.getName();
            Long deviceId;
            try {
                deviceId = Long.parseLong(dirName);
            } catch (NumberFormatException e) {
                continue; // 跳过非设备ID目录
            }

            // 获取设备信息（冗余字段）
            DeviceInfo device = deviceInfoMapper.selectById(deviceId);
            String deviceName = device != null ? device.getDeviceName() : null;
            String location = device != null ? device.getLocationDesc() : null;
            Long orgId = device != null ? device.getOrgId() : null;

            // 列出目录下所有 MP4 文件
            File[] mp4Files = deviceDir.listFiles((dir, name) ->
                    name.startsWith("rec_") && name.endsWith(".mp4"));
            if (mp4Files == null) continue;

            for (File mp4File : mp4Files) {
                try {
                    processFile(mp4File, deviceId, deviceName, location, orgId);
                } catch (Exception e) {
                    log.warn("处理录像文件失败: {}", mp4File.getName(), e);
                }
            }
        }
    }

    /**
     * 处理单个录像文件：检查是否在DB中，补充元数据
     */
    private void processFile(File mp4File, Long deviceId, String deviceName,
                             String location, Long orgId) throws IOException {
        String filePath = mp4File.getAbsolutePath();
        String fileName = mp4File.getName();

        // 检查文件是否正在被写入（Windows 文件锁检测）
        boolean isBeingWritten = isFileLocked(mp4File.toPath());

        // 查询DB中是否已有记录
        DeviceMonitorRecord existing = recordMapper.selectOne(
                new LambdaQueryWrapper<DeviceMonitorRecord>()
                        .eq(DeviceMonitorRecord::getFilePath, filePath)
                        .last("LIMIT 1"));

        if (existing != null) {
            if (!isBeingWritten && "recording".equals(existing.getStatus())) {
                // 文件已完成写入但状态还是recording，更新为completed
                updateCompletedRecord(existing, mp4File);
            } else if (!isBeingWritten && "completed".equals(existing.getStatus())
                    && (existing.getDuration() == null || existing.getDuration() == 0)) {
                // 已完成但缺少元数据（duration=0），补充 ffprobe 信息
                enrichWithFfprobe(existing, mp4File);
                existing.setFileSize(mp4File.length());
                recordMapper.updateById(existing);
                log.info("补充录像元数据: id={}, duration={}s", existing.getId(), existing.getDuration());
            }
            return;
        }

        // 新文件：创建DB记录
        DeviceMonitorRecord record = new DeviceMonitorRecord();
        record.setDeviceId(deviceId);
        record.setDeviceName(deviceName);
        record.setLocation(location);
        record.setFilePath(filePath);
        record.setFileName(fileName);
        record.setFileSize(mp4File.length());
        record.setOrgId(orgId != null ? orgId : 0L);
        record.setRecordingType("continuous");
        record.setHasAiMarks(0);
        record.setRetentionDays(getRetentionDays());
        record.setIsEvidence(0);

        // 从文件名解析开始时间: rec_YYYYMMDD_HHMMSS.mp4
        parseStartTimeFromFileName(fileName, record);

        // 计算过期时间
        if (record.getStartTime() != null) {
            record.setExpiresAt(record.getStartTime().plusDays(getRetentionDays()));
        }

        if (isBeingWritten) {
            // 正在写入
            record.setStatus("recording");
        } else {
            // 文件已完成，用 ffprobe 获取精确信息
            record.setStatus("completed");
            enrichWithFfprobe(record, mp4File);
        }

        recordMapper.insert(record);
        log.info("创建录像记录: deviceId={}, fileName={}, status={}", deviceId, fileName, record.getStatus());
    }

    /**
     * 更新已完成的录像记录：补充 ffprobe 元数据
     */
    private void updateCompletedRecord(DeviceMonitorRecord record, File mp4File) throws IOException {
        enrichWithFfprobe(record, mp4File);
        record.setStatus("completed");
        // 确保过期时间已设置
        if (record.getExpiresAt() == null && record.getStartTime() != null && record.getRetentionDays() != null) {
            record.setExpiresAt(record.getStartTime().plusDays(record.getRetentionDays()));
        }
        recordMapper.updateById(record);
        log.info("更新录像记录为completed: id={}, fileName={}", record.getId(), record.getFileName());
    }

    /**
     * 从文件名解析录像开始时间
     * 文件名格式: rec_YYYYMMDD_HHMMSS.mp4
     */
    private void parseStartTimeFromFileName(String fileName, DeviceMonitorRecord record) {
        try {
            // rec_20260508_103000.mp4 -> 20260508_103000
            String timePart = fileName.substring(4, fileName.length() - 4); // 去掉 "rec_" 和 ".mp4"
            LocalDateTime startTime = LocalDateTime.parse(timePart, FILE_TIME_FORMATTER);
            record.setStartTime(startTime);
        } catch (Exception e) {
            // 解析失败使用文件最后修改时间
            log.warn("无法从文件名解析时间: {}", fileName);
        }
    }

    /**
     * 使用 ffprobe 获取录像文件的精确元数据
     */
    private void enrichWithFfprobe(DeviceMonitorRecord record, File mp4File) throws IOException {
        // 从 ffmpeg 路径推导 ffprobe 路径（只替换文件名，不替换目录名中的 "ffmpeg"）
        Path ffmpegFilePath = Paths.get(getFfmpegPath());
        String ffprobeExe = ffmpegFilePath.getFileName().toString().replace("ffmpeg", "ffprobe");
        String ffprobePath = ffmpegFilePath.getParent() != null
                ? ffmpegFilePath.getParent().resolve(ffprobeExe).toString()
                : ffprobeExe;
        ProcessBuilder pb = new ProcessBuilder(
                ffprobePath,
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                mp4File.getAbsolutePath()
        );

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(output.toString());

            // 获取时长
            JsonNode formatNode = root.get("format");
            if (formatNode != null && formatNode.has("duration")) {
                double durationSeconds = formatNode.get("duration").asDouble();
                record.setDuration((int) Math.round(durationSeconds));

                // 计算结束时间
                if (record.getStartTime() != null && record.getDuration() > 0) {
                    record.setEndTime(record.getStartTime().plusSeconds(record.getDuration()));
                }
            }

            // 获取文件大小
            if (formatNode != null && formatNode.has("size")) {
                record.setFileSize(formatNode.get("size").asLong());
            }

            // 获取分辨率
            JsonNode streams = root.get("streams");
            if (streams != null && streams.isArray() && !streams.isEmpty()) {
                JsonNode videoStream = streams.get(0);
                int width = videoStream.has("width") ? videoStream.get("width").asInt() : 1280;
                int height = videoStream.has("height") ? videoStream.get("height").asInt() : 720;
                record.setResolution(width + "x" + height);
            }
        } catch (Exception e) {
            log.warn("解析ffprobe输出失败: {}", mp4File.getName(), e);
        }
    }

    /**
     * 检测文件是否仍在被写入
     * 通过最后修改时间判断：如果超过2分钟没被修改，认为写入已完成
     * （2分钟远大于FFmpeg 1秒分片的写入间隔，足够安全）
     */
    private boolean isFileLocked(Path filePath) {
        try {
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();
            long ageMillis = System.currentTimeMillis() - lastModified;
            return ageMillis < 120_000; // 2分钟内修改过，认为仍在写入
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * 每6小时清理超过保留期限的录像文件
     * - 跳过证据录像（is_evidence=1）
     * - 级联删除关联的片段、截图
     * - 置空关联告警的 recording_id
     * - 记录审计日志
     * - 清理孤儿片段/截图
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    public void cleanupOldRecordings() {
        if (!startupComplete) return;

        String batchId = "CLN-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        // 查询已过期且非证据的录像
        List<DeviceMonitorRecord> expiredRecords = recordMapper.selectList(
                new LambdaQueryWrapper<DeviceMonitorRecord>()
                        .lt(DeviceMonitorRecord::getExpiresAt, LocalDateTime.now())
                        .eq(DeviceMonitorRecord::getIsEvidence, 0)
                        .eq(DeviceMonitorRecord::getDeleted, 0));

        if (expiredRecords.isEmpty()) {
            log.debug("无过期录像需要清理");
        } else {
            log.info("[{}] 开始清理过期录像: 共{}条（普通保留{}天）", batchId, expiredRecords.size(), getRetentionDays());

            int deletedCount = 0;
            long totalFreedBytes = 0;
            int totalClips = 0;
            int totalScreenshots = 0;
            int totalAlerts = 0;

            for (DeviceMonitorRecord record : expiredRecords) {
                try {
                    // 再次检查证据状态（防止在查询后、删除前被升级）
                    DeviceMonitorRecord fresh = recordMapper.selectById(record.getId());
                    if (fresh != null && fresh.getIsEvidence() != null && fresh.getIsEvidence() == 1) {
                        log.info("[{}] 跳过证据录像: id={}", batchId, record.getId());
                        continue;
                    }

                    int clips = cascadeDeleteClips(record.getId());
                    int screenshots = cascadeDeleteScreenshots(record.getId());
                    int alerts = nullifyAlertRecordingId(record.getId());

                    // 物理删除录像文件
                    long fileSize = record.getFileSize() != null ? record.getFileSize() : 0;
                    if (record.getFilePath() != null) {
                        Files.deleteIfExists(Paths.get(record.getFilePath()));
                    }
                    if (record.getThumbnailPath() != null) {
                        Files.deleteIfExists(Paths.get(record.getThumbnailPath()));
                    }

                    // 软删除 DB 记录
                    recordMapper.deleteById(record.getId());

                    // 写审计日志
                    insertAuditLog(batchId, record, fileSize, "retention_expired", clips, screenshots, alerts);

                    deletedCount++;
                    totalFreedBytes += fileSize;
                    totalClips += clips;
                    totalScreenshots += screenshots;
                    totalAlerts += alerts;
                } catch (Exception e) {
                    log.warn("[{}] 清理录像失败: id={}, filePath={}", batchId, record.getId(), record.getFilePath(), e);
                }
            }

            log.info("[{}] 录像清理完成: 删除{}条，释放{}MB，级联删除片段{}条/截图{}条/告警关联{}条",
                    batchId, deletedCount, totalFreedBytes / 1024 / 1024, totalClips, totalScreenshots, totalAlerts);
        }

        // 清理孤儿片段和截图
        cleanupOrphanedMedia(batchId);
    }

    /**
     * 级联删除关联的视频片段（DB记录 + 物理文件）
     */
    private int cascadeDeleteClips(Long recordingId) {
        List<DeviceVideoClip> clips = clipMapper.selectList(
                new LambdaQueryWrapper<DeviceVideoClip>()
                        .eq(DeviceVideoClip::getRecordingId, recordingId)
                        .eq(DeviceVideoClip::getDeleted, 0));

        for (DeviceVideoClip clip : clips) {
            try {
                if (clip.getFilePath() != null) {
                    Files.deleteIfExists(Paths.get(clip.getFilePath()));
                }
            } catch (Exception e) {
                log.warn("删除片段文件失败: {}", clip.getFilePath(), e);
            }
            clipMapper.deleteById(clip.getId());
        }
        return clips.size();
    }

    /**
     * 级联删除关联的截图（DB记录 + 物理文件）
     */
    private int cascadeDeleteScreenshots(Long recordingId) {
        List<DeviceScreenshot> screenshots = screenshotMapper.selectList(
                new LambdaQueryWrapper<DeviceScreenshot>()
                        .eq(DeviceScreenshot::getRecordingId, recordingId)
                        .eq(DeviceScreenshot::getDeleted, 0));

        for (DeviceScreenshot screenshot : screenshots) {
            try {
                if (screenshot.getFilePath() != null) {
                    Files.deleteIfExists(Paths.get(screenshot.getFilePath()));
                }
            } catch (Exception e) {
                log.warn("删除截图文件失败: {}", screenshot.getFilePath(), e);
            }
            screenshotMapper.deleteById(screenshot.getId());
        }
        return screenshots.size();
    }

    /**
     * 置空关联告警的 recording_id
     */
    private int nullifyAlertRecordingId(Long recordingId) {
        List<DeviceAlert> alerts = alertMapper.selectList(
                new LambdaQueryWrapper<DeviceAlert>()
                        .eq(DeviceAlert::getRecordingId, recordingId));
        if (!alerts.isEmpty()) {
            alertMapper.update(null, new LambdaUpdateWrapper<DeviceAlert>()
                    .eq(DeviceAlert::getRecordingId, recordingId)
                    .set(DeviceAlert::getRecordingId, null));
        }
        return alerts.size();
    }

    /**
     * 插入清理审计日志
     */
    private void insertAuditLog(String batchId, DeviceMonitorRecord record, long fileSize,
                                String reason, int clips, int screenshots, int alerts) {
        try {
            DeviceCleanupAuditLog auditLog = new DeviceCleanupAuditLog();
            auditLog.setBatchId(batchId);
            auditLog.setRecordingId(record.getId());
            auditLog.setDeviceId(record.getDeviceId());
            auditLog.setFilePath(record.getFilePath());
            auditLog.setFileSize(fileSize);
            auditLog.setReason(reason);
            auditLog.setCascadedClips(clips);
            auditLog.setCascadedScreenshots(screenshots);
            auditLog.setCascadedAlertsNullified(alerts);
            auditLog.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("[{}] 写入审计日志失败: recordingId={}", batchId, record.getId(), e);
        }
    }

    /**
     * 清理孤儿片段和截图（父录像已被删除的）
     */
    private void cleanupOrphanedMedia(String batchId) {
        try {
            // 清理孤儿片段
            List<Long> orphanClipIds = jdbcTemplate.queryForList(
                    "SELECT c.id FROM device_video_clip c " +
                    "LEFT JOIN device_monitor_record r ON c.recording_id = r.id AND r.deleted = 0 " +
                    "WHERE r.id IS NULL AND c.deleted = 0", Long.class);

            if (!orphanClipIds.isEmpty()) {
                for (Long clipId : orphanClipIds) {
                    try {
                        DeviceVideoClip clip = clipMapper.selectById(clipId);
                        if (clip != null) {
                            if (clip.getFilePath() != null) {
                                Files.deleteIfExists(Paths.get(clip.getFilePath()));
                            }
                            clipMapper.deleteById(clipId);

                            DeviceCleanupAuditLog auditLog = new DeviceCleanupAuditLog();
                            auditLog.setBatchId(batchId);
                            auditLog.setRecordingId(clip.getRecordingId());
                            auditLog.setDeviceId(clip.getDeviceId());
                            auditLog.setFilePath(clip.getFilePath());
                            auditLog.setFileSize(clip.getFileSize() != null ? clip.getFileSize() : 0);
                            auditLog.setReason("orphan_clip");
                            auditLog.setCascadedClips(0);
                            auditLog.setCascadedScreenshots(0);
                            auditLog.setCascadedAlertsNullified(0);
                            auditLog.setCreatedAt(LocalDateTime.now());
                            auditLogMapper.insert(auditLog);
                        }
                    } catch (Exception e) {
                        log.warn("[{}] 清理孤儿片段失败: clipId={}", batchId, clipId, e);
                    }
                }
                log.info("[{}] 清理孤儿片段: {}条", batchId, orphanClipIds.size());
            }

            // 清理孤儿截图
            List<Long> orphanScreenshotIds = jdbcTemplate.queryForList(
                    "SELECT s.id FROM device_screenshot s " +
                    "LEFT JOIN device_monitor_record r ON s.recording_id = r.id AND r.deleted = 0 " +
                    "WHERE r.id IS NULL AND s.deleted = 0", Long.class);

            if (!orphanScreenshotIds.isEmpty()) {
                for (Long screenshotId : orphanScreenshotIds) {
                    try {
                        DeviceScreenshot screenshot = screenshotMapper.selectById(screenshotId);
                        if (screenshot != null) {
                            if (screenshot.getFilePath() != null) {
                                Files.deleteIfExists(Paths.get(screenshot.getFilePath()));
                            }
                            screenshotMapper.deleteById(screenshotId);

                            DeviceCleanupAuditLog auditLog = new DeviceCleanupAuditLog();
                            auditLog.setBatchId(batchId);
                            auditLog.setRecordingId(screenshot.getRecordingId());
                            auditLog.setDeviceId(screenshot.getDeviceId());
                            auditLog.setFilePath(screenshot.getFilePath());
                            auditLog.setFileSize(screenshot.getFileSize() != null ? screenshot.getFileSize() : 0);
                            auditLog.setReason("orphan_screenshot");
                            auditLog.setCascadedClips(0);
                            auditLog.setCascadedScreenshots(0);
                            auditLog.setCascadedAlertsNullified(0);
                            auditLog.setCreatedAt(LocalDateTime.now());
                            auditLogMapper.insert(auditLog);
                        }
                    } catch (Exception e) {
                        log.warn("[{}] 清理孤儿截图失败: screenshotId={}", batchId, screenshotId, e);
                    }
                }
                log.info("[{}] 清理孤儿截图: {}条", batchId, orphanScreenshotIds.size());
            }
        } catch (Exception e) {
            log.warn("[{}] 孤儿媒体清理失败", batchId, e);
        }
    }
}
