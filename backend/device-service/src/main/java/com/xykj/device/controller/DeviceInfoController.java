package com.xykj.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.device.dto.DeviceCreateDTO;
import com.xykj.device.dto.DeviceOnlineStatusUpdateDTO;
import com.xykj.device.dto.DeviceQueryDTO;
import com.xykj.device.dto.DeviceUpdateDTO;
import com.xykj.device.dto.DeviceImportResultDTO;
import com.xykj.device.dto.DeviceBatchOperationDTO;
import com.xykj.device.dto.DeviceBatchOperationResultDTO;
import com.xykj.device.service.DeviceInfoService;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.device.vo.DeviceDashboardVO;
import com.xykj.device.vo.DeviceDetailVO;
import com.xykj.device.vo.DeviceStatusLogVO;
import com.xykj.device.vo.DeviceVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 设备管理控制器
 * API路径: /api/v1/device
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device")
@RequiredArgsConstructor
public class DeviceInfoController {

    private final DeviceInfoService deviceInfoService;

    /**
     * 设备管理首页看板
     * GET /api/v1/device/dashboard
     */
    @GetMapping("/dashboard")
    public R<DeviceDashboardVO> dashboard(DeviceQueryDTO query) {
        DeviceDashboardVO vo = deviceInfoService.getDashboard(query);
        return R.ok(vo);
    }

    /**
     * 设备列表（分页）
     * GET /api/v1/device/list
     */
    @GetMapping("/list")
    public R<PageResult<DeviceVO>> list(DeviceQueryDTO query) {
        Page<DeviceVO> page = deviceInfoService.list(query);
        return R.ok(PageResult.of(page));
    }

    /**
     * 设备详情
     * GET /api/v1/device/{id}
     */
    @GetMapping("/{id}")
    public R<DeviceDetailVO> getDetail(@PathVariable Long id) {
        DeviceDetailVO detail = deviceInfoService.getDetail(id);
        return R.ok(detail);
    }

    /**
     * 设备状态履历
     * GET /api/v1/device/{id}/status-logs
     */
    @GetMapping("/{id}/status-logs")
    public R<List<DeviceStatusLogVO>> getStatusLogs(@PathVariable Long id) {
        return R.ok(deviceInfoService.getStatusLogs(id));
    }

    /**
     * 新增设备
     * POST /api/v1/device
     */
    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody DeviceCreateDTO dto) {
        Long id = deviceInfoService.create(dto);
        Map<String, Object> result = Map.of(
                "id", id,
                "deviceCode", dto.getDeviceCode(),
                "deviceName", dto.getDeviceName()
        );
        return R.ok(result);
    }

    /**
     * 编辑设备
     * PUT /api/v1/device/{id}
     */
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody DeviceUpdateDTO dto) {
        deviceInfoService.update(id, dto);
        Map<String, Object> result = Map.of("id", id);
        return R.ok(result);
    }

    /**
     * 删除设备
     * DELETE /api/v1/device/{id}
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deviceInfoService.delete(id);
        return R.ok();
    }

    /**
     * 批量删除设备
     * POST /api/v1/device/batch-delete
     */
    @PostMapping("/batch-delete")
    @AuditLog(module = AuditModule.DEVICE_INFO, operationType = AuditOperationType.DELETE,
            desc = "'批量删除设备，数量: ' + #dto.ids.size()")
    public R<DeviceBatchOperationResultDTO> batchDelete(@Valid @RequestBody DeviceBatchOperationDTO dto) {
        return R.ok(deviceInfoService.batchDelete(dto.getIds()));
    }

    /**
     * 更新设备在线状态
     * PUT /api/v1/device/{id}/online-status
     */
    @PutMapping("/{id}/online-status")
    public R<Void> updateOnlineStatus(@PathVariable Long id, @Valid @RequestBody DeviceOnlineStatusUpdateDTO dto) {
        deviceInfoService.updateOnlineStatus(id, dto);
        return R.ok();
    }

    /**
     * 切换设备启用/停用状态
     * PUT /api/v1/device/{id}/toggle-status
     */
    @PutMapping("/{id}/toggle-status")
    public R<Map<String, Object>> toggleStatus(@PathVariable Long id) {
        deviceInfoService.toggleStatus(id);
        return R.ok(Map.of("id", id));
    }

    /**
     * 批量启用设备
     * POST /api/v1/device/batch-enable
     */
    @PostMapping("/batch-enable")
    @AuditLog(module = AuditModule.DEVICE_INFO, operationType = AuditOperationType.STATUS_CHANGE,
            desc = "'批量启用设备，数量: ' + #dto.ids.size()")
    public R<DeviceBatchOperationResultDTO> batchEnable(@Valid @RequestBody DeviceBatchOperationDTO dto) {
        return R.ok(deviceInfoService.batchEnable(dto.getIds()));
    }

    /**
     * 批量停用设备
     * POST /api/v1/device/batch-disable
     */
    @PostMapping("/batch-disable")
    @AuditLog(module = AuditModule.DEVICE_INFO, operationType = AuditOperationType.STATUS_CHANGE,
            desc = "'批量停用设备，数量: ' + #dto.ids.size()")
    public R<DeviceBatchOperationResultDTO> batchDisable(@Valid @RequestBody DeviceBatchOperationDTO dto) {
        return R.ok(deviceInfoService.batchDisable(dto.getIds()));
    }

    /**
     * 导出设备列表到Excel
     * GET /api/v1/device/export
     */
    @GetMapping("/export")
    public void exportDevices(DeviceQueryDTO query, HttpServletResponse response) {
        deviceInfoService.exportDevices(query, response);
    }

    /**
     * 批量导入设备
     * POST /api/v1/device/import
     */
    @PostMapping("/import")
    @AuditLog(module = AuditModule.DEVICE_INFO, operationType = AuditOperationType.IMPORT,
            desc = "'批量导入设备，文件: ' + #file.originalFilename")
    public R<DeviceImportResultDTO> importDevices(@RequestParam("file") MultipartFile file) {
        DeviceImportResultDTO result = deviceInfoService.importDevices(file);
        return R.ok(result);
    }

    /**
     * 下载设备导入模板
     * GET /api/v1/device/import/template
     */
    @GetMapping("/import/template")
    public void downloadTemplate(HttpServletResponse response) {
        deviceInfoService.downloadTemplate(response);
    }
}
