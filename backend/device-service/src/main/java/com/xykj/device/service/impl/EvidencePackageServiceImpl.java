package com.xykj.device.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.exception.BizException;
import com.xykj.device.dto.EvidencePackageCreateDTO;
import com.xykj.device.entity.*;
import com.xykj.device.mapper.*;
import com.xykj.device.config.StreamConfig;
import com.xykj.device.service.EvidencePackageService;
import com.xykj.device.vo.EvidencePackageVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 证据包导出服务实现
 * 异步 ZIP 打包 + 轮询状态 + 流式下载
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidencePackageServiceImpl implements EvidencePackageService {

    private final EvidencePackageMapper packageMapper;
    private final DeviceMonitorRecordMapper recordMapper;
    private final DeviceVideoClipMapper clipMapper;
    private final DeviceScreenshotMapper screenshotMapper;
    private final StreamConfig streamConfig;

    private String getRecordingOutputDir() { return streamConfig.getRecording().getOutputDir(); }
    private int getExpireHours() { return streamConfig.getRecording().getEvidencePackageExpireHours(); }
    private int getMaxItems() { return streamConfig.getRecording().getEvidencePackageMaxItems(); }

    private static final Map<String, String> STATUS_NAMES = Map.of(
            "packing", "打包中",
            "completed", "已完成",
            "failed", "打包失败",
            "expired", "已过期"
    );

    @Override
    public EvidencePackageVO createPackage(EvidencePackageCreateDTO dto) {
        // 1. 校验：至少选中一个资源
        int totalCount = countItems(dto);
        if (totalCount == 0) {
            throw BizException.badRequest("请至少选择一个录像、片段或截图");
        }
        if (totalCount > getMaxItems()) {
            throw BizException.badRequest("单次导出不能超过" + getMaxItems() + "个文件，当前选中" + totalCount + "个");
        }

        // 2. 校验 ID 存在性
        validateIdsExist(dto);

        // 3. 生成编号
        String packageNo = generatePackageNo();

        // 4. 生成名称
        String packageName = StrUtil.isNotBlank(dto.getPackageName())
                ? dto.getPackageName()
                : "证据包_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // 5. 创建 DB 记录
        EvidencePackage pkg = new EvidencePackage();
        pkg.setPackageNo(packageNo);
        pkg.setPackageName(packageName);
        pkg.setStatus("packing");
        pkg.setRecordingIds(dto.getRecordingIds());
        pkg.setClipIds(dto.getClipIds());
        pkg.setScreenshotIds(dto.getScreenshotIds());
        pkg.setOrgId(dto.getOrgId());
        pkg.setItemCount(totalCount);
        pkg.setDownloadCount(0);
        packageMapper.insert(pkg);

        log.info("创建证据包: id={}, packageNo={}, items={}", pkg.getId(), packageNo, totalCount);

        // 6. 异步打包
        final Long packageId = pkg.getId();
        CompletableFuture.runAsync(() -> doPackaging(packageId));

        return convertToVO(pkg);
    }

    @Override
    public EvidencePackageVO getPackageStatus(Long id) {
        EvidencePackage pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw new BizException("证据包不存在");
        }
        return convertToVO(pkg);
    }

    @Override
    public void downloadPackage(Long id, HttpServletResponse response) {
        EvidencePackage pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw new BizException("证据包不存在");
        }
        if (!"completed".equals(pkg.getStatus())) {
            throw BizException.badRequest("证据包尚未完成打包，当前状态: " + STATUS_NAMES.getOrDefault(pkg.getStatus(), pkg.getStatus()));
        }
        if (pkg.getExpiresAt() != null && pkg.getExpiresAt().isBefore(LocalDateTime.now())) {
            // 自动标记为过期
            pkg.setStatus("expired");
            packageMapper.updateById(pkg);
            throw BizException.badRequest("下载链接已过期，请重新创建证据包");
        }

        Path zipPath = Paths.get(pkg.getFilePath());
        if (!Files.exists(zipPath)) {
            throw new BizException("ZIP文件不存在，可能已被清理");
        }

        try {
            // 设置响应头
            String encodedFileName = URLEncoder.encode(pkg.getFileName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            response.setContentLengthLong(pkg.getFileSize());

            // 流式输出
            Files.copy(zipPath, response.getOutputStream());
            response.getOutputStream().flush();

            // 更新下载计数
            pkg.setDownloadCount(pkg.getDownloadCount() != null ? pkg.getDownloadCount() + 1 : 1);
            pkg.setDownloadedAt(LocalDateTime.now());
            packageMapper.updateById(pkg);

            log.info("证据包下载: id={}, packageNo={}, downloadCount={}", pkg.getId(), pkg.getPackageNo(), pkg.getDownloadCount());
        } catch (IOException e) {
            log.error("证据包下载失败: id={}, error={}", id, e.getMessage());
            throw new BizException("下载失败: " + e.getMessage());
        }
    }

    @Override
    public EvidencePackageVO retryPackage(Long id) {
        EvidencePackage pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw new BizException("证据包不存在");
        }
        if (!"failed".equals(pkg.getStatus())) {
            throw BizException.badRequest("只有打包失败的证据包才能重试");
        }

        // 重置状态
        pkg.setStatus("packing");
        pkg.setFailReason(null);
        packageMapper.updateById(pkg);

        // 重新异步打包
        CompletableFuture.runAsync(() -> doPackaging(id));

        log.info("证据包重试打包: id={}, packageNo={}", id, pkg.getPackageNo());
        return convertToVO(pkg);
    }

    @Override
    public Page<EvidencePackageVO> getPackageList(int pageNum, int pageSize, Long orgId) {
        LambdaQueryWrapper<EvidencePackage> wrapper = new LambdaQueryWrapper<EvidencePackage>()
                .eq(orgId != null, EvidencePackage::getOrgId, orgId)
                .orderByDesc(EvidencePackage::getCreatedAt);

        Page<EvidencePackage> page = packageMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<EvidencePackageVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    // ========== 异步打包核心逻辑 ==========

    private void doPackaging(Long packageId) {
        try {
            EvidencePackage pkg = packageMapper.selectById(packageId);
            if (pkg == null) return;

            // 确定输出目录
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path outputDir = Paths.get(getRecordingOutputDir(), "evidence",
                    String.valueOf(pkg.getOrgId() != null ? pkg.getOrgId() : 0), dateDir);
            Files.createDirectories(outputDir);

            String zipFileName = pkg.getPackageNo() + ".zip";
            Path zipPath = outputDir.resolve(zipFileName);

            int itemCount = 0;

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
                // 打包录像文件
                if (pkg.getRecordingIds() != null) {
                    for (Long recId : pkg.getRecordingIds()) {
                        itemCount += addRecordingToZip(zos, recId);
                    }
                }

                // 打包片段文件
                if (pkg.getClipIds() != null) {
                    for (Long clipId : pkg.getClipIds()) {
                        itemCount += addClipToZip(zos, clipId);
                    }
                }

                // 打包截图文件
                if (pkg.getScreenshotIds() != null) {
                    for (Long ssId : pkg.getScreenshotIds()) {
                        itemCount += addScreenshotToZip(zos, ssId);
                    }
                }
            }

            // 更新状态：如果所有文件都未找到则标记为失败
            if (itemCount == 0) {
                pkg.setStatus("failed");
                pkg.setFailReason("所有选中的文件均不存在或已被清理，请检查录像/片段/截图是否有效");
                packageMapper.updateById(pkg);
                // 删除空的 ZIP 文件
                try { Files.deleteIfExists(zipPath); } catch (Exception ignored) {}
                log.warn("证据包打包失败（无有效文件）: id={}, packageNo={}", packageId, pkg.getPackageNo());
            } else {
                pkg.setStatus("completed");
                pkg.setFilePath(zipPath.toString());
                pkg.setFileName(zipFileName);
                pkg.setFileSize(Files.size(zipPath));
                pkg.setItemCount(itemCount);
                pkg.setExpiresAt(LocalDateTime.now().plusHours(getExpireHours()));
                packageMapper.updateById(pkg);
                log.info("证据包打包完成: id={}, packageNo={}, items={}, size={}bytes",
                        packageId, pkg.getPackageNo(), itemCount, pkg.getFileSize());
            }

        } catch (Exception e) {
            log.error("证据包打包失败: id={}, error={}", packageId, e.getMessage(), e);
            try {
                EvidencePackage pkg = packageMapper.selectById(packageId);
                if (pkg != null) {
                    pkg.setStatus("failed");
                    pkg.setFailReason(e.getMessage() != null && e.getMessage().length() > 500
                            ? e.getMessage().substring(0, 500) : e.getMessage());
                    packageMapper.updateById(pkg);
                }
            } catch (Exception ex) {
                log.error("更新失败状态异常: {}", ex.getMessage());
            }
        }
    }

    /** 添加录像到 ZIP（MP4 + 缩略图） */
    private int addRecordingToZip(ZipOutputStream zos, Long recordingId) {
        DeviceMonitorRecord record = recordMapper.selectById(recordingId);
        if (record == null || StrUtil.isBlank(record.getFilePath())) {
            log.warn("录像不存在或无文件路径, recordingId={}", recordingId);
            return 0;
        }

        int count = 0;
        try {
            // 录像 MP4
            Path mp4Path = Paths.get(record.getFilePath());
            if (Files.exists(mp4Path)) {
                String safeName = sanitizeFileName(record.getDeviceName()) + "_"
                        + formatDateTime(record.getStartTime()) + ".mp4";
                addToZip(zos, "录像/" + safeName, mp4Path);
                count++;
            }

            // 缩略图
            if (StrUtil.isNotBlank(record.getThumbnailPath())) {
                Path thumbPath = Paths.get(record.getThumbnailPath());
                if (Files.exists(thumbPath)) {
                    String thumbName = sanitizeFileName(record.getDeviceName()) + "_"
                            + formatDateTime(record.getStartTime()) + "_thumb.jpg";
                    addToZip(zos, "录像/" + thumbName, thumbPath);
                    count++;
                }
            }
        } catch (Exception e) {
            log.warn("添加录像到ZIP失败, recordingId={}: {}", recordingId, e.getMessage());
        }
        return count;
    }

    /** 添加片段到 ZIP */
    private int addClipToZip(ZipOutputStream zos, Long clipId) {
        DeviceVideoClip clip = clipMapper.selectById(clipId);
        if (clip == null || StrUtil.isBlank(clip.getFilePath())) {
            log.warn("片段不存在或无文件路径, clipId={}", clipId);
            return 0;
        }

        try {
            Path clipPath = Paths.get(clip.getFilePath());
            if (Files.exists(clipPath)) {
                String safeName = sanitizeFileName(clip.getDeviceName()) + "_clip_" + clipId + ".mp4";
                addToZip(zos, "片段/" + safeName, clipPath);
                return 1;
            }
        } catch (Exception e) {
            log.warn("添加片段到ZIP失败, clipId={}: {}", clipId, e.getMessage());
        }
        return 0;
    }

    /** 添加截图到 ZIP */
    private int addScreenshotToZip(ZipOutputStream zos, Long screenshotId) {
        DeviceScreenshot screenshot = screenshotMapper.selectById(screenshotId);
        if (screenshot == null || StrUtil.isBlank(screenshot.getFilePath())) {
            log.warn("截图不存在或无文件路径, screenshotId={}", screenshotId);
            return 0;
        }

        try {
            Path ssPath = Paths.get(screenshot.getFilePath());
            if (Files.exists(ssPath)) {
                String safeName = sanitizeFileName(screenshot.getDeviceName()) + "_screenshot_" + screenshotId + ".jpg";
                addToZip(zos, "截图/" + safeName, ssPath);
                return 1;
            }
        } catch (Exception e) {
            log.warn("添加截图到ZIP失败, screenshotId={}: {}", screenshotId, e.getMessage());
        }
        return 0;
    }

    /** 将文件写入 ZIP */
    private void addToZip(ZipOutputStream zos, String entryName, Path filePath) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(filePath, zos);
        zos.closeEntry();
    }

    // ========== 工具方法 ==========

    private int countItems(EvidencePackageCreateDTO dto) {
        int count = 0;
        if (dto.getRecordingIds() != null) count += dto.getRecordingIds().size();
        if (dto.getClipIds() != null) count += dto.getClipIds().size();
        if (dto.getScreenshotIds() != null) count += dto.getScreenshotIds().size();
        return count;
    }

    /**
     * 校验录像/片段/截图 ID 在数据库中是否存在
     * 收集所有不存在的 ID 并一次性报错
     */
    private void validateIdsExist(EvidencePackageCreateDTO dto) {
        List<String> missing = new ArrayList<>();

        if (dto.getRecordingIds() != null && !dto.getRecordingIds().isEmpty()) {
            for (Long id : dto.getRecordingIds()) {
                if (recordMapper.selectById(id) == null) {
                    missing.add("录像#" + id);
                }
            }
        }
        if (dto.getClipIds() != null && !dto.getClipIds().isEmpty()) {
            for (Long id : dto.getClipIds()) {
                if (clipMapper.selectById(id) == null) {
                    missing.add("片段#" + id);
                }
            }
        }
        if (dto.getScreenshotIds() != null && !dto.getScreenshotIds().isEmpty()) {
            for (Long id : dto.getScreenshotIds()) {
                if (screenshotMapper.selectById(id) == null) {
                    missing.add("截图#" + id);
                }
            }
        }

        if (!missing.isEmpty()) {
            throw BizException.badRequest("以下资源不存在: " + String.join(", ", missing));
        }
    }

    private String generatePackageNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seqPart = String.format("%04d", new Random().nextInt(10000));
        return "EP-" + datePart + "-" + seqPart;
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "unknown";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }

    private EvidencePackageVO convertToVO(EvidencePackage pkg) {
        EvidencePackageVO vo = new EvidencePackageVO();
        vo.setId(pkg.getId());
        vo.setPackageNo(pkg.getPackageNo());
        vo.setPackageName(pkg.getPackageName());
        vo.setStatus(pkg.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(pkg.getStatus(), pkg.getStatus()));
        vo.setRecordingIds(pkg.getRecordingIds());
        vo.setClipIds(pkg.getClipIds());
        vo.setScreenshotIds(pkg.getScreenshotIds());
        vo.setFileName(pkg.getFileName());
        vo.setFileSize(pkg.getFileSize());
        vo.setFileSizeFormat(pkg.getFileSize() != null ? formatFileSize(pkg.getFileSize()) : null);
        vo.setItemCount(pkg.getItemCount());
        vo.setDownloadUrl("completed".equals(pkg.getStatus())
                ? "/api/v1/device/evidence-packages/" + pkg.getId() + "/download" : null);
        vo.setExpiresAt(pkg.getExpiresAt());
        vo.setDownloadedAt(pkg.getDownloadedAt());
        vo.setDownloadCount(pkg.getDownloadCount());
        vo.setFailReason(pkg.getFailReason());
        vo.setCreatedAt(pkg.getCreatedAt());
        return vo;
    }
}
