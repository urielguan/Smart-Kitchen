package com.xykj.health.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.common.service.FileStorageService;
import com.xykj.health.dto.HealthCertificateCreateDTO;
import com.xykj.health.dto.HealthCertificateQueryDTO;
import com.xykj.health.service.HealthCertificateService;
import com.xykj.health.vo.HealthCertificateDashboardVO;
import com.xykj.health.vo.HealthCertificateVO;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 健康证管理控制器
 * API路径: /api/v1/health/certificate
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health/certificate")
@RequiredArgsConstructor
@Tag(name = "健康证管理", description = "健康证录入、查询、状态管理接口")
public class HealthCertificateController {

    private final HealthCertificateService certificateService;
    private final FileStorageService fileStorageService;

    /**
     * 创建/更新健康证
     */
    @PostMapping
    @Operation(summary = "保存健康证", description = "新增或更新员工健康证信息")
    public R<HealthCertificateVO> save(@Valid @RequestBody HealthCertificateCreateDTO dto) {
        log.info("保存健康证: employeeId={}, certificateNo={}", dto.getEmployeeId(), dto.getCertificateNo());
        HealthCertificateVO vo = certificateService.save(dto);
        return R.ok(vo);
    }

    /**
     * 根据员工ID获取健康证
     */
    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "获取员工健康证", description = "根据员工ID获取健康证信息")
    public R<HealthCertificateVO> getByEmployeeId(
            @Parameter(description = "员工ID") @PathVariable Long employeeId) {
        HealthCertificateVO vo = certificateService.getByEmployeeId(employeeId);
        if (vo == null) {
            return R.fail("该员工暂无健康证信息");
        }
        return R.ok(vo);
    }

    /**
     * 获取健康证列表
     */
    @GetMapping("/list")
    @Operation(summary = "健康证列表", description = "获取健康证列表，支持按状态和组织筛选")
    public R<List<HealthCertificateVO>> list(HealthCertificateQueryDTO query) {
        query.setOrgId(null);
        List<HealthCertificateVO> list = certificateService.list(query);
        return R.ok(list);
    }

    /**
     * 健康证看板数据
     */
    @GetMapping("/dashboard")
    @Operation(summary = "健康证看板", description = "获取健康证统计数据和紧急预警列表")
    public R<HealthCertificateDashboardVO> getDashboard(HealthCertificateQueryDTO query) {
        query.setOrgId(null);
        HealthCertificateDashboardVO dashboard = certificateService.getDashboard(query);
        return R.ok(dashboard);
    }

    /**
     * 根据ID获取健康证详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "健康证详情", description = "根据健康证ID获取详细信息")
    public R<HealthCertificateVO> getById(
            @Parameter(description = "健康证ID") @PathVariable Long id) {
        HealthCertificateVO vo = certificateService.getById(id);
        if (vo == null) {
            return R.fail("健康证不存在");
        }
        return R.ok(vo);
    }

    /**
     * 分页获取健康证列表
     */
    @GetMapping("/page")
    @Operation(summary = "健康证分页列表", description = "分页获取健康证列表，支持按状态、组织、员工姓名筛选")
    public R<PageResult<HealthCertificateVO>> listPage(HealthCertificateQueryDTO query) {
        query.setOrgId(null);
        PageResult<HealthCertificateVO> result = certificateService.listPage(query);
        return R.ok(result);
    }

    /**
     * 获取即将过期的健康证
     */
    @GetMapping("/expiring")
    @Operation(summary = "即将过期健康证", description = "获取指定天数内即将过期的健康证列表")
    public R<List<HealthCertificateVO>> listExpiring(HealthCertificateQueryDTO query) {
        List<HealthCertificateVO> list = certificateService.listExpiring(query);
        return R.ok(list);
    }

    /**
     * 删除健康证
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除健康证", description = "逻辑删除健康证记录")
    public R<Boolean> delete(@Parameter(description = "健康证ID") @PathVariable Long id) {
        boolean result = certificateService.delete(id);
        if (result) {
            return R.ok("删除成功", true);
        }
        return R.fail("删除失败");
    }

    /**
     * 上传健康证照片
     */
    @PostMapping("/upload-image")
    @Operation(summary = "上传健康证照片", description = "上传健康证电子版照片，返回图片URL")
    public R<java.util.Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String imageUrl = fileStorageService.upload(file, "certificates");
        return R.ok(java.util.Map.of("imageUrl", imageUrl));
    }

    /**
     * 手动刷新健康证状态
     */
    @PostMapping("/refresh-status")
    @Operation(summary = "刷新状态", description = "手动触发健康证状态批量更新")
    public R<Integer> refreshStatus() {
        int count = certificateService.refreshStatus();
        return R.ok("更新了 " + count + " 条记录", count);
    }

    /**
     * 导出健康证数据
     */
    @GetMapping("/export")
    @Operation(summary = "导出健康证", description = "根据筛选条件导出健康证数据为Excel文件")
    public void exportCertificates(HealthCertificateQueryDTO query, HttpServletResponse response) {
        query.setOrgId(null);
        certificateService.exportCertificates(query, response);
    }
}
