package com.xykj.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.device.dto.ScreenshotQueryDTO;
import com.xykj.device.dto.ScreenshotUploadDTO;
import com.xykj.device.service.ScreenshotService;
import com.xykj.device.vo.DeviceVisionAnalysisTaskVO;
import com.xykj.device.vo.ScreenshotVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/device/screenshots")
@RequiredArgsConstructor
public class ScreenshotController {

    private final ScreenshotService screenshotService;

    @PostMapping("/upload")
    @AuditLog(module = AuditModule.DEVICE_SCREENSHOT, operationType = AuditOperationType.CREATE,
            desc = "'上传截图'", targetId = "#result.data.id")
    public R<ScreenshotVO> uploadScreenshot(
            @RequestParam("file") MultipartFile file,
            @Valid ScreenshotUploadDTO dto) {
        ScreenshotVO vo = screenshotService.uploadScreenshot(file, dto);
        return R.ok(vo);
    }

    @GetMapping
    public R<PageResult<ScreenshotVO>> getScreenshotList(ScreenshotQueryDTO query) {
        Page<ScreenshotVO> page = screenshotService.getScreenshotList(query);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/{id}")
    public R<ScreenshotVO> getScreenshotDetail(@PathVariable Long id) {
        ScreenshotVO vo = screenshotService.getScreenshotDetail(id);
        return R.ok(vo);
    }

    @PostMapping("/{id}/analyze")
    public R<DeviceVisionAnalysisTaskVO> analyzeScreenshot(@PathVariable Long id) {
        return R.ok(screenshotService.analyzeScreenshot(id));
    }

    @GetMapping("/{id}/analysis/latest")
    public R<DeviceVisionAnalysisTaskVO> getLatestAnalysis(@PathVariable Long id) {
        return R.ok(screenshotService.getLatestAnalysis(id));
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = AuditModule.DEVICE_SCREENSHOT, operationType = AuditOperationType.DELETE,
            desc = "'删除截图'", targetId = "#id")
    public R<Void> deleteScreenshot(@PathVariable Long id) {
        screenshotService.deleteScreenshot(id);
        return R.ok();
    }
}
