package com.xykj.device.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.ai.AiModuleCode;
import com.xykj.common.ai.AiServiceType;
import com.xykj.common.ai.config.AiProperties;
import com.xykj.common.ai.entity.AiServiceConfig;
import com.xykj.common.ai.model.AiVisionDetectionResult;
import com.xykj.common.ai.service.AiServiceConfigService;
import com.xykj.common.ai.service.OpenAiCompatibleService;
import com.xykj.common.exception.BizException;
import com.xykj.common.context.UserContext;
import com.xykj.device.dto.ScreenshotQueryDTO;
import com.xykj.device.dto.ScreenshotUploadDTO;
import com.xykj.device.entity.DeviceAlert;
import com.xykj.device.entity.DeviceMonitorRecord;
import com.xykj.device.entity.DeviceScreenshot;
import com.xykj.device.entity.DeviceVisionAnalysisTask;
import com.xykj.device.event.ViolationEvent;
import com.xykj.device.mapper.DeviceAlertMapper;
import com.xykj.device.mapper.DeviceMonitorRecordMapper;
import com.xykj.device.mapper.DeviceScreenshotMapper;
import com.xykj.device.mapper.DeviceVisionAnalysisTaskMapper;
import com.xykj.device.config.StreamConfig;
import com.xykj.device.service.ScreenshotService;
import com.xykj.device.vo.DeviceVisionAnalysisTaskVO;
import com.xykj.device.vo.ScreenshotVO;
import com.xykj.device.vo.ViolationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenshotServiceImpl implements ScreenshotService {

    private final DeviceScreenshotMapper screenshotMapper;
    private final DeviceMonitorRecordMapper recordMapper;
    private final DeviceVisionAnalysisTaskMapper analysisTaskMapper;
    private final DeviceAlertMapper alertMapper;
    private final JdbcTemplate jdbcTemplate;
    private final StreamConfig streamConfig;
    private final AiServiceConfigService aiServiceConfigService;
    private final OpenAiCompatibleService openAiCompatibleService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private String getRecordingOutputDir() { return streamConfig.getRecording().getOutputDir(); }

    private static final Map<String, String> PURPOSE_TAG_NAMES = Map.of(
            "violation_trace", "违规追溯",
            "accident_review", "事故核查",
            "process_review", "流程复检"
    );

    private static final Map<String, String> STATUS_NAMES = Map.of(
            "completed", "已完成",
            "failed", "失败"
    );

    @Override
    public ScreenshotVO uploadScreenshot(MultipartFile file, ScreenshotUploadDTO dto) {
        // 1. 校验来源录像
        DeviceMonitorRecord record = recordMapper.selectById(dto.getRecordingId());
        if (record == null) {
            throw new BizException("录像记录不存在");
        }

        // 2. 校验文件
        if (file == null || file.isEmpty()) {
            throw new BizException("截图文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.contains("jpeg") && !contentType.contains("jpg") && !contentType.contains("png"))) {
            throw new BizException("仅支持 JPEG/PNG 格式");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BizException("截图文件不能超过5MB");
        }

        // 3. 校验用途标签
        if (!PURPOSE_TAG_NAMES.containsKey(dto.getPurposeTag())) {
            throw new BizException("无效的用途标签");
        }

        // 4. 创建记录
        DeviceScreenshot screenshot = new DeviceScreenshot();
        screenshot.setRecordingId(dto.getRecordingId());
        screenshot.setDeviceId(record.getDeviceId());
        screenshot.setDeviceName(record.getDeviceName());
        screenshot.setOrgId(record.getOrgId());
        screenshot.setCaptureTimeOffset(dto.getCaptureTimeOffset());
        screenshot.setPurposeTag(dto.getPurposeTag());
        screenshot.setStatus("completed");
        screenshot.setVersionNo(1);
        screenshot.setFileSize(file.getSize());

        screenshotMapper.insert(screenshot);

        // 5. 保存文件
        String dateDir = record.getStartTime() != null
                ? record.getStartTime().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String fileName = "snp_" + screenshot.getId() + ".jpg";
        String dirPath = String.format("%s/screenshots/%d/%d/%s",
                getRecordingOutputDir(), record.getOrgId(), record.getDeviceId(), dateDir);
        String filePath = dirPath + "/" + fileName;

        try {
            Files.createDirectories(Paths.get(dirPath));
            file.transferTo(Paths.get(filePath));
        } catch (Exception e) {
            log.error("保存截图文件失败: {}", filePath, e);
            // 删除 DB 记录
            screenshotMapper.deleteById(screenshot.getId());
            throw new BizException("截图保存失败");
        }

        // 6. 更新文件信息
        screenshot.setFilePath(filePath);
        screenshot.setFileName(fileName);
        screenshotMapper.updateById(screenshot);

        log.info("截图上传成功: id={}, recordingId={}, timeOffset={}s",
                screenshot.getId(), dto.getRecordingId(), dto.getCaptureTimeOffset());

        return convertToVO(screenshot);
    }

    @Override
    public Page<ScreenshotVO> getScreenshotList(ScreenshotQueryDTO query) {
        LambdaQueryWrapper<DeviceScreenshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getRecordingId() != null, DeviceScreenshot::getRecordingId, query.getRecordingId());
        wrapper.eq(query.getDeviceId() != null, DeviceScreenshot::getDeviceId, query.getDeviceId());
        wrapper.eq(StrUtil.isNotBlank(query.getPurposeTag()), DeviceScreenshot::getPurposeTag, query.getPurposeTag());
        wrapper.orderByDesc(DeviceScreenshot::getCreatedAt);

        Page<DeviceScreenshot> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<DeviceScreenshot> resultPage = screenshotMapper.selectPage(page, wrapper);

        Page<ScreenshotVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public ScreenshotVO getScreenshotDetail(Long id) {
        DeviceScreenshot screenshot = screenshotMapper.selectById(id);
        if (screenshot == null) {
            throw new BizException("截图记录不存在");
        }
        return convertToVO(screenshot);
    }

    @Override
    public void deleteScreenshot(Long id) {
        DeviceScreenshot screenshot = screenshotMapper.selectById(id);
        if (screenshot == null) {
            throw new BizException("截图记录不存在");
        }

        // 物理删除文件
        if (StrUtil.isNotBlank(screenshot.getFilePath())) {
            try {
                Files.deleteIfExists(Paths.get(screenshot.getFilePath()));
            } catch (Exception e) {
                log.warn("删除截图文件失败: {}", screenshot.getFilePath(), e);
            }
        }

        screenshotMapper.deleteById(id);
        log.info("删除截图: id={}, recordingId={}", id, screenshot.getRecordingId());
    }

    @Override
    public DeviceVisionAnalysisTaskVO analyzeScreenshot(Long id) {
        DeviceScreenshot screenshot = screenshotMapper.selectById(id);
        if (screenshot == null) {
            throw new BizException("截图记录不存在");
        }
        DeviceVisionAnalysisTask task = new DeviceVisionAnalysisTask();
        task.setScreenshotId(screenshot.getId());
        task.setRecordingId(screenshot.getRecordingId());
        task.setDeviceId(screenshot.getDeviceId());
        task.setDeviceName(screenshot.getDeviceName());
        task.setOrgId(screenshot.getOrgId());
        task.setTaskStatus("processing");
        task.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L);
        analysisTaskMapper.insert(task);

        try {
            AiServiceConfig config = aiServiceConfigService.getActiveByModule(AiServiceType.VISION, AiModuleCode.VIOLATION_RECOGNITION);
            byte[] bytes = Files.readAllBytes(Paths.get(screenshot.getFilePath()));
            String prompt = "请识别后厨人员是否存在未佩戴口罩(no_mask)或未佩戴厨师帽(no_hat)两类违规。"
                    + "只输出JSON：{\"summary\":\"...\",\"modelVersion\":\"...\",\"violations\":[{\"violationType\":\"no_mask|no_hat\",\"violationTypeName\":\"未佩戴口罩|未佩戴厨师帽\",\"confidence\":0-1,\"personCount\":1,\"explanation\":\"...\"}]}。";
            AiVisionDetectionResult result = openAiCompatibleService.analyzeImage(config, bytes, "image/jpeg", prompt, AiModuleCode.VIOLATION_RECOGNITION, "business");
            if (!result.isSuccess()) {
                task.setTaskStatus("failed");
                task.setErrorMessage(result.getErrorMessage());
                analysisTaskMapper.updateById(task);
                return toTaskVo(task);
            }
            task.setSummary(result.getSummary());
            task.setModelVersion(result.getModelVersion());
            task.setRawResponse(result.getRawResponse());
            AiVisionDetectionResult.ViolationItem top = result.getViolations().stream()
                    .filter(item -> "no_mask".equals(item.getViolationType()) || "no_hat".equals(item.getViolationType()))
                    .max(java.util.Comparator.comparing(item -> item.getConfidence() == null ? 0D : item.getConfidence()))
                    .orElse(null);
            if (top == null) {
                task.setTaskStatus("threshold_not_met");
                analysisTaskMapper.updateById(task);
                return toTaskVo(task);
            }
            task.setViolationType(top.getViolationType());
            task.setConfidence((int) Math.round((top.getConfidence() == null ? 0D : top.getConfidence()) * 100));
            if ((top.getConfidence() == null ? 0D : top.getConfidence()) >= aiProperties.getViolationThreshold()) {
                DeviceAlert alert = createViolationAlert(screenshot, task, top);
                task.setAlertId(alert.getId());
                task.setTaskStatus("alerted");
                analysisTaskMapper.updateById(task);
                eventPublisher.publishEvent(new ViolationEvent(this, buildViolationVo(alert, screenshot, top)));
            } else {
                task.setTaskStatus("threshold_not_met");
                analysisTaskMapper.updateById(task);
            }
            return toTaskVo(task);
        } catch (Exception ex) {
            task.setTaskStatus("failed");
            task.setErrorMessage(ex.getMessage());
            analysisTaskMapper.updateById(task);
            log.warn("截图AI分析失败, screenshotId={}: {}", id, ex.getMessage());
            return toTaskVo(task);
        }
    }

    @Override
    public DeviceVisionAnalysisTaskVO getLatestAnalysis(Long screenshotId) {
        DeviceVisionAnalysisTask task = analysisTaskMapper.selectOne(new LambdaQueryWrapper<DeviceVisionAnalysisTask>()
                .eq(DeviceVisionAnalysisTask::getScreenshotId, screenshotId)
                .orderByDesc(DeviceVisionAnalysisTask::getCreatedAt)
                .last("LIMIT 1"));
        return task == null ? null : toTaskVo(task);
    }

    // ========== 转换 ==========

    private ScreenshotVO convertToVO(DeviceScreenshot s) {
        ScreenshotVO vo = new ScreenshotVO();
        vo.setId(s.getId());
        vo.setRecordingId(s.getRecordingId());
        vo.setDeviceId(s.getDeviceId());
        vo.setDeviceName(s.getDeviceName());
        vo.setCaptureTimeOffset(s.getCaptureTimeOffset());
        vo.setCaptureTimeOffset(s.getCaptureTimeOffset());
        vo.setCaptureTimeFormat(formatDuration(s.getCaptureTimeOffset() != null ? s.getCaptureTimeOffset() : 0));
        vo.setFileSize(s.getFileSize());
        vo.setFileSizeFormat(s.getFileSize() != null ? formatFileSize(s.getFileSize()) : "0B");
        vo.setResolution(s.getResolution());
        vo.setPurposeTag(s.getPurposeTag());
        vo.setPurposeTagName(PURPOSE_TAG_NAMES.getOrDefault(s.getPurposeTag(), "未知"));
        vo.setStatus(s.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(s.getStatus(), "未知"));
        vo.setVersionNo(s.getVersionNo());
        vo.setCreatedAt(s.getCreatedAt());

        // URL: /screenshots/{orgId}/{deviceId}/{yyyyMMdd}/{fileName}
        if (StrUtil.isNotBlank(s.getFileName())) {
            String dateDir = getDateDir(s);
            String url = "/screenshots/" + s.getOrgId() + "/" + s.getDeviceId() + "/" + dateDir + "/" + s.getFileName();
            vo.setPreviewUrl(url);
            vo.setDownloadUrl(url + "?download=true");
        }

        if (s.getCreatedBy() != null) {
            vo.setCreatedByName(resolveEmployeeName(s.getCreatedBy()));
        }
        vo.setLatestAnalysis(getLatestAnalysis(s.getId()));

        return vo;
    }

    private DeviceAlert createViolationAlert(DeviceScreenshot screenshot, DeviceVisionAnalysisTask task, AiVisionDetectionResult.ViolationItem top) throws Exception {
        DeviceAlert alert = new DeviceAlert();
        alert.setAlertNo("AIV" + System.currentTimeMillis());
        alert.setAlertType("ai_violation");
        alert.setAlertLevel((top.getConfidence() != null && top.getConfidence() >= 0.9D) ? "danger" : "warning");
        alert.setDeviceId(screenshot.getDeviceId());
        alert.setDeviceName(screenshot.getDeviceName());
        alert.setRecordingId(screenshot.getRecordingId());
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setStatus("pending");
        alert.setOrgId(screenshot.getOrgId());
        alert.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L);
        alert.setAlertContent(top.getViolationTypeName() + "，置信度" + Math.round((top.getConfidence() == null ? 0D : top.getConfidence()) * 100) + "%");
        alert.setAlertImages(objectMapper.writeValueAsString(java.util.List.of("/screenshots/" + screenshot.getOrgId() + "/" + screenshot.getDeviceId() + "/" + getDateDir(screenshot) + "/" + screenshot.getFileName())));
        alert.setAlertDetail(objectMapper.writeValueAsString(java.util.Map.of(
                "violationType", top.getViolationType(),
                "violationTypeName", top.getViolationTypeName(),
                "confidence", Math.round((top.getConfidence() == null ? 0D : top.getConfidence()) * 100),
                "summary", StrUtil.blankToDefault(top.getExplanation(), task.getSummary()),
                "screenshotId", screenshot.getId(),
                "modelVersion", StrUtil.blankToDefault(task.getModelVersion(), "")
        )));
        alertMapper.insert(alert);
        return alert;
    }

    private ViolationVO buildViolationVo(DeviceAlert alert, DeviceScreenshot screenshot, AiVisionDetectionResult.ViolationItem top) {
        ViolationVO vo = new ViolationVO();
        vo.setId(alert.getId());
        vo.setViolationType(top.getViolationType());
        vo.setViolationTypeName(top.getViolationTypeName());
        vo.setInvolvedCount(top.getPersonCount() == null ? 1 : top.getPersonCount());
        vo.setAlertLevel(alert.getAlertLevel());
        vo.setAlertLevelName("danger".equals(alert.getAlertLevel()) ? "危险" : "警告");
        vo.setLocation("后厨监控点");
        vo.setDeviceId(alert.getDeviceId());
        vo.setDeviceName(alert.getDeviceName());
        vo.setConfidence((int) Math.round((top.getConfidence() == null ? 0D : top.getConfidence()) * 100));
        vo.setOccurredAt(alert.getTriggeredAt());
        vo.setStatus(alert.getStatus());
        vo.setStatusName("待处理");
        vo.setScreenshotUrl("/screenshots/" + screenshot.getOrgId() + "/" + screenshot.getDeviceId() + "/" + getDateDir(screenshot) + "/" + screenshot.getFileName());
        vo.setRecordingId(alert.getRecordingId());
        return vo;
    }

    private DeviceVisionAnalysisTaskVO toTaskVo(DeviceVisionAnalysisTask task) {
        if (task == null) {
            return null;
        }
        DeviceVisionAnalysisTaskVO vo = new DeviceVisionAnalysisTaskVO();
        vo.setId(task.getId());
        vo.setScreenshotId(task.getScreenshotId());
        vo.setRecordingId(task.getRecordingId());
        vo.setDeviceId(task.getDeviceId());
        vo.setDeviceName(task.getDeviceName());
        vo.setTaskStatus(task.getTaskStatus());
        vo.setViolationType(task.getViolationType());
        vo.setConfidence(task.getConfidence());
        vo.setSummary(task.getSummary());
        vo.setModelVersion(task.getModelVersion());
        vo.setAlertId(task.getAlertId());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setCreatedAt(task.getCreatedAt());
        return vo;
    }

    private String getDateDir(DeviceScreenshot s) {
        if (StrUtil.isNotBlank(s.getFilePath())) {
            Path parent = Paths.get(s.getFilePath()).getParent();
            if (parent != null) return parent.getFileName().toString();
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private String formatDuration(int seconds) {
        int h = seconds / 3600, m = (seconds % 3600) / 60, sec = seconds % 60;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024));
    }

    private String resolveEmployeeName(Long id) {
        if (id == null) return null;
        try {
            return jdbcTemplate.queryForObject("SELECT name FROM sys_employee WHERE id = ? AND deleted = 0", String.class, id);
        } catch (Exception e) {
            return "用户" + id;
        }
    }
}
