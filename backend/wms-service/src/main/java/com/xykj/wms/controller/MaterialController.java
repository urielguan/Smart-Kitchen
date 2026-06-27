package com.xykj.wms.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.wms.dto.MaterialCreateDTO;
import com.xykj.wms.dto.MaterialImportResultDTO;
import com.xykj.wms.dto.MaterialQueryDTO;
import com.xykj.wms.dto.MaterialUpdateDTO;
import com.xykj.wms.service.MaterialService;
import com.xykj.wms.service.alert.MaterialAlertEngine;
import com.xykj.wms.vo.MaterialStatisticsVO;
import com.xykj.wms.vo.MaterialVO;
import com.xykj.wms.vo.NutritionSyncResultVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 物料管理控制器
 * API路径: /api/v1/wms/materials
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/wms/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;
    private final MaterialAlertEngine materialAlertEngine;

    /**
     * 物料列表（分页）
     * GET /api/v1/wms/materials
     */
    @GetMapping
    public R<PageResult<MaterialVO>> list(@Valid MaterialQueryDTO query) {
        PageResult<MaterialVO> result = materialService.list(query);
        return R.ok(result);
    }

    /**
     * 物料详情
     * GET /api/v1/wms/materials/{id}
     */
    @GetMapping("/{id:\\d+}")
    public R<MaterialVO> getDetail(@PathVariable Long id) {
        MaterialVO material = materialService.getDetail(id);
        return R.ok(material);
    }

    /**
     * 新增物料
     * POST /api/v1/wms/materials
     */
    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody MaterialCreateDTO dto) {
        Long id = materialService.create(dto);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("materialCode", dto.getMaterialCode());
        result.put("materialName", dto.getMaterialName());
        return R.ok(result);
    }

    /**
     * 编辑物料
     * PUT /api/v1/wms/materials/{id}
     */
    @PutMapping("/{id:\\d+}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody MaterialUpdateDTO dto) {
        materialService.update(id, dto);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("materialName", dto.getMaterialName());
        return R.ok(result);
    }

    /**
     * 删除物料
     * DELETE /api/v1/wms/materials/{id}
     */
    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return R.ok();
    }

    /**
     * 更新物料状态（启用/停用）
     * PUT /api/v1/wms/materials/{id}/status
     */
    @PutMapping("/{id:\\d+}/status")
    public R<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        materialService.updateStatus(id, status);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("status", status);
        return R.ok(result);
    }

    /**
     * 物料统计数据
     * GET /api/v1/wms/materials/statistics
     */
    @GetMapping("/statistics")
    public R<MaterialStatisticsVO> getStatistics(@Valid MaterialQueryDTO query) {
        MaterialStatisticsVO statistics = materialService.getStatistics(query);
        return R.ok(statistics);
    }

    /**
     * 上传物料图片
     * POST /api/v1/wms/materials/upload-image
     */
    @PostMapping("/upload-image")
    public R<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String imageUrl = materialService.uploadImage(file);
        Map<String, String> result = new HashMap<>();
        result.put("imageUrl", imageUrl);
        return R.ok(result);
    }

    /**
     * 下载物料导入模板
     * GET /api/v1/wms/materials/import/template
     */
    @GetMapping("/import/template")
    public void downloadImportTemplate(HttpServletResponse response) {
        materialService.downloadImportTemplate(response);
    }

    /**
     * 导入物料
     * POST /api/v1/wms/materials/import
     */
    @PostMapping("/import")
    public R<MaterialImportResultDTO> importMaterials(@RequestParam("file") MultipartFile file) {
        MaterialImportResultDTO result = materialService.importMaterials(file);
        return R.ok(result);
    }

    /**
     * 导出物料
     * GET /api/v1/wms/materials/export
     */
    @GetMapping("/export")
    public void exportMaterials(@Valid MaterialQueryDTO query, HttpServletResponse response) {
        materialService.exportMaterials(query, response);
    }

    /**
     * 下载导入错误文件
     * GET /api/v1/wms/materials/import/errors/{fileName}
     */
    @GetMapping("/import/errors/{fileName}")
    public void downloadErrorFile(@PathVariable String fileName, HttpServletResponse response) {
        materialService.downloadErrorFile(fileName, response);
    }

    /**
     * 手动触发物料告警巡检（测试用）
     * POST /api/v1/wms/materials/alert/scan
     */
    @PostMapping("/alert/scan")
    public R<Void> triggerAlertScan() {
        log.info("手动触发物料告警巡检");
        materialAlertEngine.checkAllMaterials();
        return R.ok();
    }

    /**
     * 手动触发指定物料的告警检查（测试用）
     * POST /api/v1/wms/materials/alert/check/{materialId}
     */
    @PostMapping("/alert/check/{materialId}")
    public R<Void> triggerAlertCheck(@PathVariable Long materialId) {
        log.info("手动触发物料告警检查: materialId={}", materialId);
        materialAlertEngine.checkMaterial(materialId);
        return R.ok();
    }

    /**
     * 同步物料营养
     * POST /api/v1/wms/materials/{id}/nutrition-sync
     */
    @PostMapping("/{id:\\d+}/nutrition-sync")
    public R<NutritionSyncResultVO> syncNutrition(@PathVariable Long id) {
        return R.ok(materialService.syncNutrition(id));
    }
}
