package com.xykj.device.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.exception.BizException;
import com.xykj.device.dto.RecordingQueryDTO;
import com.xykj.device.entity.DeviceAlert;
import com.xykj.device.entity.DeviceMonitorRecord;
import com.xykj.device.entity.DeviceScreenshot;
import com.xykj.device.entity.DeviceVideoClip;
import com.xykj.device.mapper.DeviceAlertMapper;
import com.xykj.device.mapper.DeviceMonitorRecordMapper;
import com.xykj.device.mapper.DeviceScreenshotMapper;
import com.xykj.device.mapper.DeviceVideoClipMapper;
import com.xykj.device.service.RecordingService;
import com.xykj.device.vo.RecordingStatisticsVO;
import com.xykj.device.vo.RecordingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 视频录像服务实现
 * 基于数据库查询真实录像记录（由 RecordingScheduler 定时扫描写入）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingServiceImpl implements RecordingService {

    private final DeviceMonitorRecordMapper recordMapper;
    private final DeviceVideoClipMapper clipMapper;
    private final DeviceScreenshotMapper screenshotMapper;
    private final DeviceAlertMapper alertMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final Map<String, String> RECORDING_TYPE_NAMES = Map.of(
            "continuous", "连续录像",
            "alarm", "告警录像",
            "manual", "手动录像"
    );

    @Override
    public Page<RecordingVO> getRecordingList(RecordingQueryDTO query) {
        LambdaQueryWrapper<DeviceMonitorRecord> wrapper = buildQueryWrapper(query);

        Page<DeviceMonitorRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<DeviceMonitorRecord> resultPage = recordMapper.selectPage(page, wrapper);

        // 转换为 VO
        Page<RecordingVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public RecordingStatisticsVO getRecordingStatistics(RecordingQueryDTO query) {
        LambdaQueryWrapper<DeviceMonitorRecord> wrapper = buildQueryWrapper(query);

        RecordingStatisticsVO vo = new RecordingStatisticsVO();
        vo.setTotalCount(recordMapper.selectCount(wrapper));

        // 告警录像数量
        LambdaQueryWrapper<DeviceMonitorRecord> alarmWrapper = buildQueryWrapper(query);
        alarmWrapper.eq(DeviceMonitorRecord::getRecordingType, "alarm");
        vo.setAlarmCount(recordMapper.selectCount(alarmWrapper));

        // AI标记录像数量
        LambdaQueryWrapper<DeviceMonitorRecord> aiWrapper = buildQueryWrapper(query);
        aiWrapper.eq(DeviceMonitorRecord::getHasAiMarks, 1);
        vo.setAiMarkCount(recordMapper.selectCount(aiWrapper));

        // 总存储大小 — 使用参数化查询避免 SQL 注入
        StringBuilder sql = new StringBuilder(
            "SELECT COALESCE(SUM(file_size), 0) FROM device_monitor_record WHERE deleted = 0 AND status = 'completed'");
        List<Object> params = new java.util.ArrayList<>();
        if (query.getDeviceId() != null) {
            sql.append(" AND device_id = ?");
            params.add(query.getDeviceId());
        }
        if (query.getStartTime() != null) {
            sql.append(" AND start_time >= ?");
            params.add(query.getStartTime());
        }
        if (query.getEndTime() != null) {
            sql.append(" AND end_time <= ?");
            params.add(query.getEndTime());
        }
        if (query.getOrgId() != null) {
            sql.append(" AND org_id = ?");
            params.add(query.getOrgId());
        }
        if (query.getRecordingType() != null) {
            sql.append(" AND recording_type = ?");
            params.add(query.getRecordingType());
        }
        Long totalSize = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        vo.setTotalFileSize(totalSize != null ? totalSize : 0L);

        return vo;
    }

    /**
     * 构建录像查询条件（共用）
     */
    private LambdaQueryWrapper<DeviceMonitorRecord> buildQueryWrapper(RecordingQueryDTO query) {
        LambdaQueryWrapper<DeviceMonitorRecord> wrapper = new LambdaQueryWrapper<>();

        // 条件过滤
        wrapper.eq(query.getDeviceId() != null, DeviceMonitorRecord::getDeviceId, query.getDeviceId());
        wrapper.ge(query.getStartTime() != null, DeviceMonitorRecord::getStartTime, query.getStartTime());
        wrapper.le(query.getEndTime() != null, DeviceMonitorRecord::getEndTime, query.getEndTime());
        wrapper.eq(query.getOrgId() != null, DeviceMonitorRecord::getOrgId, query.getOrgId());
        wrapper.eq(query.getRecordingType() != null, DeviceMonitorRecord::getRecordingType, query.getRecordingType());

        // 只查已完成的录像
        wrapper.eq(DeviceMonitorRecord::getStatus, "completed");
        wrapper.orderByDesc(DeviceMonitorRecord::getStartTime);
        return wrapper;
    }

    @Override
    public RecordingVO getRecordingDetail(Long id) {
        DeviceMonitorRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException("录像记录不存在");
        }
        return convertToVO(record);
    }

    @Override
    public String getPlaybackUrl(Long id) {
        DeviceMonitorRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException("录像记录不存在");
        }
        // 返回静态资源 URL: /recordings/{deviceId}/{fileName}
        return "/recordings/" + record.getDeviceId() + "/" + record.getFileName();
    }

    @Override
    public void deleteRecording(Long id) {
        DeviceMonitorRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException("录像记录不存在");
        }

        // 证据录像禁止手动删除
        if (record.getIsEvidence() != null && record.getIsEvidence() == 1) {
            throw BizException.badRequest("证据录像不可手动删除，原因: " + record.getEvidenceReason());
        }

        // 级联删除关联的视频片段
        List<DeviceVideoClip> clips = clipMapper.selectList(
                new LambdaQueryWrapper<DeviceVideoClip>()
                        .eq(DeviceVideoClip::getRecordingId, id)
                        .eq(DeviceVideoClip::getDeleted, 0));
        for (DeviceVideoClip clip : clips) {
            if (StrUtil.isNotBlank(clip.getFilePath())) {
                try { Files.deleteIfExists(Paths.get(clip.getFilePath())); }
                catch (Exception e) { log.warn("删除片段文件失败: {}", clip.getFilePath(), e); }
            }
            clipMapper.deleteById(clip.getId());
        }

        // 级联删除关联的截图
        List<DeviceScreenshot> screenshots = screenshotMapper.selectList(
                new LambdaQueryWrapper<DeviceScreenshot>()
                        .eq(DeviceScreenshot::getRecordingId, id)
                        .eq(DeviceScreenshot::getDeleted, 0));
        for (DeviceScreenshot screenshot : screenshots) {
            if (StrUtil.isNotBlank(screenshot.getFilePath())) {
                try { Files.deleteIfExists(Paths.get(screenshot.getFilePath())); }
                catch (Exception e) { log.warn("删除截图文件失败: {}", screenshot.getFilePath(), e); }
            }
            screenshotMapper.deleteById(screenshot.getId());
        }

        // 置空关联告警的 recording_id
        List<DeviceAlert> alerts = alertMapper.selectList(
                new LambdaQueryWrapper<DeviceAlert>()
                        .eq(DeviceAlert::getRecordingId, id));
        if (!alerts.isEmpty()) {
            alertMapper.update(null, new LambdaUpdateWrapper<DeviceAlert>()
                    .eq(DeviceAlert::getRecordingId, id)
                    .set(DeviceAlert::getRecordingId, null));
        }

        // 物理删除录像文件
        if (StrUtil.isNotBlank(record.getFilePath())) {
            try {
                Files.deleteIfExists(Paths.get(record.getFilePath()));
            } catch (Exception e) {
                log.warn("删除录像文件失败: {}", record.getFilePath(), e);
            }
        }

        // 物理删除缩略图
        if (StrUtil.isNotBlank(record.getThumbnailPath())) {
            try {
                Files.deleteIfExists(Paths.get(record.getThumbnailPath()));
            } catch (Exception e) {
                log.warn("删除缩略图失败: {}", record.getThumbnailPath(), e);
            }
        }

        // 软删除 DB 记录
        recordMapper.deleteById(id);
        log.info("删除录像: id={}, deviceId={}, fileName={}, 级联删除片段{}条, 截图{}条, 告警关联{}条",
                id, record.getDeviceId(), record.getFileName(), clips.size(), screenshots.size(), alerts.size());
    }

    // ========== 实体 → VO 转换 ==========

    private RecordingVO convertToVO(DeviceMonitorRecord record) {
        RecordingVO vo = new RecordingVO();
        vo.setId(record.getId());
        vo.setDeviceId(record.getDeviceId());
        vo.setDeviceName(record.getDeviceName());
        vo.setLocation(record.getLocation());
        vo.setStartTime(record.getStartTime());
        vo.setEndTime(record.getEndTime());
        vo.setDuration(record.getDuration());
        vo.setDurationFormat(record.getDuration() != null ? formatDuration(record.getDuration()) : "00:00");
        vo.setFileSize(record.getFileSize());
        vo.setFileSizeFormat(record.getFileSize() != null ? formatFileSize(record.getFileSize()) : "0B");
        vo.setResolution(record.getResolution());
        vo.setRecordingType(record.getRecordingType());
        vo.setRecordingTypeName(RECORDING_TYPE_NAMES.getOrDefault(record.getRecordingType(), "未知"));
        vo.setPlaybackUrl("/recordings/" + record.getDeviceId() + "/" + record.getFileName());
        vo.setDownloadUrl("/recordings/" + record.getDeviceId() + "/" + record.getFileName() + "?download=true");
        vo.setHasAiMarks(record.getHasAiMarks() != null && record.getHasAiMarks() == 1);
        vo.setIsEvidence(record.getIsEvidence() != null && record.getIsEvidence() == 1);
        vo.setRetentionDays(record.getRetentionDays());
        vo.setExpiresAt(record.getExpiresAt());
        vo.setEvidenceReason(record.getEvidenceReason());
        vo.setCreatedAt(record.getCreatedAt());

        // 缩略图 URL
        if (StrUtil.isNotBlank(record.getThumbnailPath())) {
            String thumbFileName = Paths.get(record.getThumbnailPath()).getFileName().toString();
            vo.setThumbnailUrl("/recordings/" + record.getDeviceId() + "/" + thumbFileName);
        }

        return vo;
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
}
