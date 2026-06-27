package com.xykj.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.device.dto.EvidencePackageCreateDTO;
import com.xykj.device.service.EvidencePackageService;
import com.xykj.device.vo.EvidencePackageVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 证据包导出控制器
 * API路径: /api/v1/device/evidence-packages
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device/evidence-packages")
@RequiredArgsConstructor
public class EvidencePackageController {

    private final EvidencePackageService evidencePackageService;

    /**
     * 创建证据包（异步打包）
     * POST /api/v1/device/evidence-packages
     */
    @PostMapping
    @AuditLog(module = AuditModule.DEVICE_EVIDENCE, operationType = AuditOperationType.EXPORT,
            desc = "'创建证据包'", targetId = "#result.data.id")
    public R<EvidencePackageVO> createPackage(@RequestBody EvidencePackageCreateDTO dto) {
        EvidencePackageVO vo = evidencePackageService.createPackage(dto);
        return R.ok(vo);
    }

    /**
     * 查询打包状态（轮询用）
     * GET /api/v1/device/evidence-packages/{id}
     */
    @GetMapping("/{id}")
    public R<EvidencePackageVO> getPackageStatus(@PathVariable Long id) {
        EvidencePackageVO vo = evidencePackageService.getPackageStatus(id);
        return R.ok(vo);
    }

    /**
     * 下载证据包 ZIP
     * GET /api/v1/device/evidence-packages/{id}/download
     */
    @GetMapping("/{id}/download")
    @AuditLog(module = AuditModule.DEVICE_EVIDENCE, operationType = AuditOperationType.DOWNLOAD,
            desc = "'下载证据包'", targetId = "#id")
    public void downloadPackage(@PathVariable Long id, HttpServletResponse response) {
        evidencePackageService.downloadPackage(id, response);
    }

    /**
     * 失败重试
     * POST /api/v1/device/evidence-packages/{id}/retry
     */
    @PostMapping("/{id}/retry")
    @AuditLog(module = AuditModule.DEVICE_EVIDENCE, operationType = AuditOperationType.PROCESS,
            desc = "'重试证据包'", targetId = "#id")
    public R<EvidencePackageVO> retryPackage(@PathVariable Long id) {
        EvidencePackageVO vo = evidencePackageService.retryPackage(id);
        return R.ok(vo);
    }

    /**
     * 证据包历史列表
     * GET /api/v1/device/evidence-packages
     */
    @GetMapping
    public R<PageResult<EvidencePackageVO>> getPackageList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long orgId) {
        Page<EvidencePackageVO> page = evidencePackageService.getPackageList(pageNum, pageSize, orgId);
        return R.ok(PageResult.of(page));
    }
}
