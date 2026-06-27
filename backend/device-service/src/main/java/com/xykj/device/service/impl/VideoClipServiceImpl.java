package com.xykj.device.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.exception.BizException;
import com.xykj.device.dto.ClipExtractDTO;
import com.xykj.device.dto.ClipQueryDTO;
import com.xykj.device.entity.DeviceMonitorRecord;
import com.xykj.device.entity.DeviceVideoClip;
import com.xykj.device.mapper.DeviceMonitorRecordMapper;
import com.xykj.device.mapper.DeviceVideoClipMapper;
import com.xykj.device.config.StreamConfig;
import com.xykj.device.service.VideoClipService;
import com.xykj.device.vo.VideoClipVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 视频片段截取服务实现
 * 使用 FFmpeg 流复制 (-c copy) 从源录像中截取子片段，异步处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoClipServiceImpl implements VideoClipService {

    private final DeviceVideoClipMapper clipMapper;
    private final DeviceMonitorRecordMapper recordMapper;
    private final JdbcTemplate jdbcTemplate;
    private final StreamConfig streamConfig;

    private String getFfmpegPath() { return streamConfig.getFfmpeg().getPath(); }
    private String getRecordingOutputDir() { return streamConfig.getRecording().getOutputDir(); }

    private static final Map<String, String> PURPOSE_TAG_NAMES = Map.of(
            "violation_trace", "违规追溯",
            "accident_review", "事故核查",
            "process_review", "流程复检"
    );

    private static final Map<String, String> STATUS_NAMES = Map.of(
            "processing", "处理中",
            "completed", "已完成",
            "failed", "失败"
    );

    @Override
    public VideoClipVO extractClip(ClipExtractDTO dto) {
        // 1. 校验来源录像
        DeviceMonitorRecord record = recordMapper.selectById(dto.getRecordingId());
        if (record == null) {
            throw new BizException("录像记录不存在");
        }
        if (!"completed".equals(record.getStatus())) {
            throw new BizException("只能从已完成的录像中截取片段");
        }

        // 2. 校验时间偏移
        int duration = record.getDuration() != null ? record.getDuration() : 0;
        if (dto.getStartTimeOffset() < 0) {
            throw new BizException("开始时间不能小于0");
        }
        if (dto.getEndTimeOffset() <= dto.getStartTimeOffset()) {
            throw new BizException("结束时间必须大于开始时间");
        }
        if (duration > 0 && dto.getEndTimeOffset() > duration) {
            throw new BizException("结束时间不能超过录像时长(" + duration + "秒)");
        }
        if (dto.getEndTimeOffset() - dto.getStartTimeOffset() < 1) {
            throw new BizException("片段时长不能小于1秒");
        }

        // 3. 校验用途标签
        if (!PURPOSE_TAG_NAMES.containsKey(dto.getPurposeTag())) {
            throw new BizException("无效的用途标签");
        }

        // 4. 创建片段记录
        DeviceVideoClip clip = new DeviceVideoClip();
        clip.setRecordingId(dto.getRecordingId());
        clip.setDeviceId(record.getDeviceId());
        clip.setDeviceName(record.getDeviceName());
        clip.setOrgId(record.getOrgId());
        clip.setStartTimeOffset(dto.getStartTimeOffset());
        clip.setEndTimeOffset(dto.getEndTimeOffset());
        clip.setClipDuration(dto.getEndTimeOffset() - dto.getStartTimeOffset());
        clip.setPurposeTag(dto.getPurposeTag());
        clip.setStatus("processing");
        clip.setVersionNo(1);
        clip.setFileSize(0L);

        // 计算存储路径: {getRecordingOutputDir()}/clips/{orgId}/{deviceId}/{yyyyMMdd}/{clipId}.mp4
        String dateDir = record.getStartTime() != null
                ? record.getStartTime().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 先用临时文件名，插入后改为 clipId.mp4
        String tempFileName = "clip_temp_" + System.currentTimeMillis() + ".mp4";
        String clipDir = String.format("%s/clips/%d/%d/%s",
                getRecordingOutputDir(), record.getOrgId(), record.getDeviceId(), dateDir);
        String tempFilePath = clipDir + "/" + tempFileName;

        clip.setFilePath(tempFilePath);
        clip.setFileName(tempFileName);

        clipMapper.insert(clip);

        // 5. 用真实文件名更新
        String realFileName = "clip_" + clip.getId() + ".mp4";
        String realFilePath = clipDir + "/" + realFileName;
        clip.setFileName(realFileName);
        clip.setFilePath(realFilePath);
        clipMapper.updateById(clip);

        // 6. 异步 FFmpeg 截取
        final String sourceFilePath = record.getFilePath();
        final String outputPath = realFilePath;
        final Long clipId = clip.getId();

        CompletableFuture.runAsync(() -> {
            try {
                doExtract(sourceFilePath, outputPath, dto.getStartTimeOffset(), dto.getEndTimeOffset());
                // 成功：更新状态和文件大小
                DeviceVideoClip update = new DeviceVideoClip();
                update.setId(clipId);
                update.setStatus("completed");
                update.setFilePath(outputPath);
                update.setFileName(realFileName);

                Path file = Paths.get(outputPath);
                if (Files.exists(file)) {
                    update.setFileSize(Files.size(file));
                }

                clipMapper.updateById(update);
                log.info("片段截取成功: clipId={}, duration={}s", clipId, clip.getClipDuration());
            } catch (Exception e) {
                log.error("片段截取失败: clipId={}", clipId, e);
                DeviceVideoClip failUpdate = new DeviceVideoClip();
                failUpdate.setId(clipId);
                failUpdate.setStatus("failed");
                failUpdate.setFailReason(e.getMessage() != null ? e.getMessage().substring(0, Math.min(500, e.getMessage().length())) : "未知错误");
                clipMapper.updateById(failUpdate);

                // 清理临时文件
                try { Files.deleteIfExists(Paths.get(outputPath)); } catch (Exception ignored) {}
            }
        });

        return convertToVO(clip);
    }

    @Override
    public Page<VideoClipVO> getClipList(ClipQueryDTO query) {
        LambdaQueryWrapper<DeviceVideoClip> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getRecordingId() != null, DeviceVideoClip::getRecordingId, query.getRecordingId());
        wrapper.eq(query.getDeviceId() != null, DeviceVideoClip::getDeviceId, query.getDeviceId());
        wrapper.eq(StrUtil.isNotBlank(query.getPurposeTag()), DeviceVideoClip::getPurposeTag, query.getPurposeTag());
        wrapper.eq(StrUtil.isNotBlank(query.getStatus()), DeviceVideoClip::getStatus, query.getStatus());
        wrapper.orderByDesc(DeviceVideoClip::getCreatedAt);

        Page<DeviceVideoClip> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<DeviceVideoClip> resultPage = clipMapper.selectPage(page, wrapper);

        Page<VideoClipVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public VideoClipVO getClipDetail(Long id) {
        DeviceVideoClip clip = clipMapper.selectById(id);
        if (clip == null) {
            throw new BizException("片段记录不存在");
        }
        return convertToVO(clip);
    }

    @Override
    public void deleteClip(Long id) {
        DeviceVideoClip clip = clipMapper.selectById(id);
        if (clip == null) {
            throw new BizException("片段记录不存在");
        }

        // 物理删除文件
        if (StrUtil.isNotBlank(clip.getFilePath())) {
            try {
                Files.deleteIfExists(Paths.get(clip.getFilePath()));
            } catch (Exception e) {
                log.warn("删除片段文件失败: {}", clip.getFilePath(), e);
            }
        }

        // 软删除 DB 记录
        clipMapper.deleteById(id);
        log.info("删除片段: id={}, recordingId={}", id, clip.getRecordingId());
    }

    // ========== FFmpeg 截取 ==========

    /**
     * 使用 FFmpeg 流复制截取片段
     * 优先使用 -c copy（无重编码，毫秒级完成），失败时回退到重编码
     */
    private void doExtract(String sourcePath, String outputPath, int startOffset, int endOffset) throws Exception {
        // 确保输出目录存在
        Path outputDir = Paths.get(outputPath).getParent();
        Files.createDirectories(outputDir);

        // 先尝试流复制
        boolean success = runFFmpegExtract(sourcePath, outputPath, startOffset, endOffset, true);

        if (!success) {
            // 流复制失败，回退到重编码
            log.warn("流复制失败，尝试重编码: source={}", sourcePath);
            success = runFFmpegExtract(sourcePath, outputPath, startOffset, endOffset, false);
        }

        if (!success) {
            throw new RuntimeException("FFmpeg 截取失败");
        }

        // 校验输出文件
        if (!Files.exists(Paths.get(outputPath)) || Files.size(Paths.get(outputPath)) == 0) {
            throw new RuntimeException("截取输出文件为空或不存在");
        }
    }

    /**
     * 执行 FFmpeg 命令
     * @return true 如果成功，false 如果需要回退
     */
    private boolean runFFmpegExtract(String sourcePath, String outputPath, int startOffset, int endOffset, boolean streamCopy) throws Exception {
        ProcessBuilder pb;
        if (streamCopy) {
            pb = new ProcessBuilder(
                    getFfmpegPath(),
                    "-ss", String.valueOf(startOffset),
                    "-to", String.valueOf(endOffset),
                    "-i", sourcePath,
                    "-c", "copy",
                    "-avoid_negative_ts", "1",
                    "-y", outputPath
            );
        } else {
            pb = new ProcessBuilder(
                    getFfmpegPath(),
                    "-ss", String.valueOf(startOffset),
                    "-to", String.valueOf(endOffset),
                    "-i", sourcePath,
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-an",
                    "-y", outputPath
            );
        }

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 读取输出（防止进程阻塞）
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("FFmpeg 截取失败(exitCode={}): {}", exitCode, output);
            // 删除可能存在的不完整输出文件
            Files.deleteIfExists(Paths.get(outputPath));
            return false;
        }

        return true;
    }

    // ========== 实体 → VO 转换 ==========

    private VideoClipVO convertToVO(DeviceVideoClip clip) {
        VideoClipVO vo = new VideoClipVO();
        vo.setId(clip.getId());
        vo.setRecordingId(clip.getRecordingId());
        vo.setDeviceId(clip.getDeviceId());
        vo.setDeviceName(clip.getDeviceName());
        vo.setStartTimeOffset(clip.getStartTimeOffset());
        vo.setEndTimeOffset(clip.getEndTimeOffset());
        vo.setClipDuration(clip.getClipDuration());
        vo.setDurationFormat(clip.getClipDuration() != null ? formatDuration(clip.getClipDuration()) : "00:00");
        vo.setFileSize(clip.getFileSize());
        vo.setFileSizeFormat(clip.getFileSize() != null ? formatFileSize(clip.getFileSize()) : "0B");
        vo.setPurposeTag(clip.getPurposeTag());
        vo.setPurposeTagName(PURPOSE_TAG_NAMES.getOrDefault(clip.getPurposeTag(), "未知"));
        vo.setStatus(clip.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(clip.getStatus(), "未知"));
        vo.setFailReason(clip.getFailReason());
        vo.setVersionNo(clip.getVersionNo());
        vo.setCreatedAt(clip.getCreatedAt());

        // 下载地址: /clips/{orgId}/{deviceId}/{yyyyMMdd}/{fileName}
        if ("completed".equals(clip.getStatus()) && StrUtil.isNotBlank(clip.getFileName())) {
            vo.setDownloadUrl("/clips/" + clip.getOrgId() + "/" + clip.getDeviceId()
                    + "/" + getClipDateDir(clip) + "/" + clip.getFileName());
        }

        // 创建人名称
        if (clip.getCreatedBy() != null) {
            vo.setCreatedByName(resolveEmployeeName(clip.getCreatedBy()));
        }

        return vo;
    }

    /**
     * 从文件路径中提取日期目录
     * 路径格式: .../clips/{orgId}/{deviceId}/{yyyyMMdd}/{fileName}
     */
    private String getClipDateDir(DeviceVideoClip clip) {
        if (StrUtil.isNotBlank(clip.getFilePath())) {
            Path path = Paths.get(clip.getFilePath());
            Path parent = path.getParent();
            if (parent != null) {
                return parent.getFileName().toString();
            }
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    // ========== 工具方法 ==========

    private String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%02d:%02d", minutes, secs);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }

    private String resolveEmployeeName(Long employeeId) {
        if (employeeId == null) return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM sys_employee WHERE id = ? AND deleted = 0",
                    String.class, employeeId);
        } catch (Exception e) {
            return "用户" + employeeId;
        }
    }

    private Long getCurrentUserId() {
        try {
            return com.xykj.common.context.UserContext.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
