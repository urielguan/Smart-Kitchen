package com.xykj.sys.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sys.dto.*;
import com.xykj.sys.service.OrganizationService;
import com.xykj.sys.vo.OrganizationStatisticsVO;
import com.xykj.sys.vo.OrganizationTreeVO;
import com.xykj.sys.vo.OrganizationVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织管理控制器
 * API路径: /api/v1/sys/organizations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sys/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    /**
     * 组织列表（分页）
     * GET /api/v1/sys/organizations
     */
    @GetMapping
    public R<PageResult<OrganizationVO>> list(OrganizationQueryDTO query) {
        PageResult<OrganizationVO> result = organizationService.list(query);
        return R.ok(result);
    }

    /**
     * 组织详情
     * GET /api/v1/sys/organizations/{id}
     */
    @GetMapping("/{id}")
    public R<OrganizationVO> getDetail(@PathVariable Long id) {
        OrganizationVO org = organizationService.getDetail(id);
        return R.ok(org);
    }

    /**
     * 新增组织
     * POST /api/v1/sys/organizations
     */
    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody OrganizationCreateDTO dto) {
        Map<String, Object> result = organizationService.create(dto);
        return R.ok(result);
    }

    /**
     * 编辑组织
     * PUT /api/v1/sys/organizations/{id}
     */
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody OrganizationUpdateDTO dto) {
        Map<String, Object> result = organizationService.update(id, dto);
        return R.ok(result);
    }

    /**
     * 删除组织
     * DELETE /api/v1/sys/organizations/{id}
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        organizationService.delete(id);
        return R.ok();
    }

    /**
     * 启用/禁用组织
     * PUT /api/v1/sys/organizations/{id}/status
     */
    @PutMapping("/{id}/status")
    public R<Map<String, Object>> updateStatus(@PathVariable Long id, @Valid @RequestBody OrganizationStatusDTO dto) {
        Map<String, Object> result = organizationService.updateStatus(id, dto);
        return R.ok(result);
    }

    /**
     * 组织树
     * GET /api/v1/sys/organizations/tree
     */
    @GetMapping("/tree")
    public R<List<OrganizationTreeVO>> getTree(
            @RequestParam(required = false) String orgType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") Boolean includeChildren) {
        List<OrganizationTreeVO> tree = organizationService.getTree(orgType, status, keyword, includeChildren);
        return R.ok(tree);
    }

    /**
     * 统计数据
     * GET /api/v1/sys/organizations/statistics
     */
    @GetMapping("/statistics")
    public R<OrganizationStatisticsVO> getStatistics() {
        OrganizationStatisticsVO statistics = organizationService.getStatistics();
        return R.ok(statistics);
    }

    // ==================== 导入导出接口 ====================

    /**
     * 下载导入模板
     * GET /api/v1/sys/organizations/import/template
     */
    @GetMapping("/import/template")
    public void downloadImportTemplate(HttpServletResponse response) {
        organizationService.downloadImportTemplate(response);
    }

    /**
     * 导入组织
     * POST /api/v1/sys/organizations/import
     */
    @PostMapping("/import")
    public R<OrganizationImportResultDTO> importOrganizations(@RequestParam("file") MultipartFile file) {
        OrganizationImportResultDTO result = organizationService.importOrganizations(file);
        return R.ok(result);
    }

    /**
     * 导出组织
     * GET /api/v1/sys/organizations/export
     */
    @GetMapping("/export")
    public void exportOrganizations(
            @RequestParam(required = false) String orgType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") Boolean includeChildren,
            HttpServletResponse response) {
        organizationService.exportOrganizations(orgType, status, keyword, includeChildren, response);
    }

    /**
     * 下载导入错误文件
     * GET /api/v1/sys/organizations/import/errors/{fileName}
     */
    @GetMapping("/import/errors/{fileName}")
    public void downloadErrorFile(@PathVariable String fileName, HttpServletResponse response) {
        organizationService.downloadErrorFile(fileName, response);
    }
}
