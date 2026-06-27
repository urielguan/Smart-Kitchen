package com.xykj.device.controller;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.common.service.FileStorageService;
import com.xykj.device.dto.AlertCloseDTO;
import com.xykj.device.dto.AlertDispatchDTO;
import com.xykj.device.dto.AlertDispatchQueryDTO;
import com.xykj.device.dto.AlertProcessDTO;
import com.xykj.device.dto.AlertQueryDTO;
import com.xykj.device.dto.AlertReviewDTO;
import com.xykj.device.service.DeviceAlertService;
import com.xykj.device.vo.AlertDashboardVO;
import com.xykj.device.vo.AlertDetailVO;
import com.xykj.device.vo.AlertDispatchDetailVO;
import com.xykj.device.vo.AlertDispatchVO;
import com.xykj.device.vo.AlertVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 设备告警控制器
 */
@Tag(name = "设备告警管理")
@RestController
@RequestMapping("/api/v1/device/alerts")
@RequiredArgsConstructor
public class DeviceAlertController {

    private final DeviceAlertService alertService;
    private final FileStorageService fileStorageService;

    @Operation(summary = "告警管理首页看板")
    @GetMapping("/dashboard")
    public R<AlertDashboardVO> getDashboard(AlertQueryDTO query) {
        return R.ok(alertService.getDashboard(query));
    }

    @Operation(summary = "告警列表")
    @GetMapping
    public R<PageResult<AlertVO>> list(AlertQueryDTO query) {
        Page<AlertVO> page = alertService.list(query);
        return R.ok(PageResult.of(page));
    }

    @Operation(summary = "导出告警列表")
    @GetMapping("/export")
    public void exportAlerts(AlertQueryDTO query, HttpServletResponse response) {
        alertService.exportAlerts(query, response);
    }

    @Operation(summary = "告警详情")
    @GetMapping("/{id}")
    public R<AlertDetailVO> getDetail(@PathVariable Long id) {
        return R.ok(alertService.getDetail(id));
    }

    @Operation(summary = "告警派单详情（含派单信息和处理记录）")
    @GetMapping("/{id}/dispatch-detail")
    public R<AlertDispatchDetailVO> getDispatchDetailByAlertId(@PathVariable Long id) {
        return R.ok(alertService.getDispatchDetailByAlertId(id));
    }

    @Operation(summary = "告警派单")
    @PostMapping("/{id}/dispatch")
    public R<Map<String, Object>> dispatch(@PathVariable Long id, @Valid @RequestBody AlertDispatchDTO dto) {
        return R.ok(alertService.dispatch(id, dto));
    }

    @Operation(summary = "获取告警派单可选处理人")
    @GetMapping("/{id}/dispatch-handlers")
    public R<List<Map<String, Object>>> getDispatchHandlers(@PathVariable Long id) {
        return R.ok(alertService.getDispatchHandlers(id));
    }

    @Operation(summary = "派单工单列表")
    @GetMapping("/dispatches")
    public R<PageResult<AlertDispatchVO>> listDispatch(AlertDispatchQueryDTO query) {
        Page<AlertDispatchVO> page = alertService.listDispatch(query);
        return R.ok(PageResult.of(page));
    }

    @Operation(summary = "派单工单详情")
    @GetMapping("/dispatches/{dispatchId}")
    public R<AlertDispatchDetailVO> getDispatchDetail(@PathVariable Long dispatchId) {
        return R.ok(alertService.getDispatchDetail(dispatchId));
    }

    @Operation(summary = "处理工单")
    @PostMapping("/dispatches/{dispatchId}/process")
    public R<Void> processDispatch(@PathVariable Long dispatchId, @Valid @RequestBody AlertProcessDTO dto) {
        alertService.processDispatch(dispatchId, dto);
        return R.ok();
    }

    @Operation(summary = "复核工单")
    @PostMapping("/dispatches/{dispatchId}/review")
    public R<Void> reviewDispatch(@PathVariable Long dispatchId, @Valid @RequestBody AlertReviewDTO dto) {
        alertService.reviewDispatch(dispatchId, dto);
        return R.ok();
    }

    @Operation(summary = "关闭告警")
    @PostMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id, @Valid @RequestBody AlertCloseDTO dto) {
        alertService.close(id, dto);
        return R.ok();
    }

    @Operation(summary = "上传附件")
    @PostMapping("/upload-attachment")
    public R<java.util.Map<String, String>> uploadAttachment(@RequestParam("file") MultipartFile file) {
        String fileUrl = fileStorageService.upload(file, "alert-attachments");
        String originalName = file.getOriginalFilename();
        return R.ok(java.util.Map.of("fileUrl", fileUrl, "fileName", originalName != null ? originalName : ""));
    }
}
