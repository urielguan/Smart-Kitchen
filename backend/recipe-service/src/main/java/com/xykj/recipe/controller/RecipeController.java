package com.xykj.recipe.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.recipe.dto.*;
import com.xykj.recipe.service.RecipeService;
import com.xykj.recipe.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 菜谱管理控制器
 * API路径: /api/v1/recipe/recipes
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recipe/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    /**
     * 菜谱列表（分页）
     * GET /api/v1/recipe/recipes
     */
    @GetMapping
    public R<PageResult<RecipeVO>> list(@Valid RecipeQueryDTO query) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RecipeVO> page = recipeService.list(query);
        PageResult<RecipeVO> result = new PageResult<>();
        result.setList(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return R.ok(result);
    }

    /**
     * 导出菜谱列表
     * GET /api/v1/recipe/recipes/export
     */
    @GetMapping("/export")
    public void exportRecipes(@Valid RecipeQueryDTO query, HttpServletResponse response) {
        recipeService.exportRecipes(query, response);
    }

    /**
     * 下载菜谱导入模板
     * GET /api/v1/recipe/recipes/import/template
     */
    @GetMapping("/import/template")
    public void downloadImportTemplate(HttpServletResponse response) {
        recipeService.downloadImportTemplate(response);
    }

    /**
     * 导入菜谱
     * POST /api/v1/recipe/recipes/import
     */
    @PostMapping("/import")
    public R<RecipeImportResultDTO> importRecipes(@RequestParam("file") MultipartFile file) {
        RecipeImportResultDTO result = recipeService.importRecipes(file);
        return R.ok(result);
    }

    /**
     * 下载导入错误文件
     * GET /api/v1/recipe/recipes/import/errors/{fileName}
     */
    @GetMapping("/import/errors/{fileName}")
    public void downloadImportErrorFile(@PathVariable String fileName, HttpServletResponse response) {
        recipeService.downloadImportErrorFile(fileName, response);
    }

    /**
     * 菜谱详情
     * GET /api/v1/recipe/recipes/{id}
     */
    @GetMapping("/{id}")
    public R<RecipeDetailVO> getDetail(@PathVariable Long id) {
        RecipeDetailVO detail = recipeService.getDetail(id);
        return R.ok(detail);
    }

    /**
     * 新增菜谱
     * POST /api/v1/recipe/recipes
     */
    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody RecipeCreateDTO dto) {
        Long id = recipeService.create(dto);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("menuCode", dto.getMenuCode());
        result.put("menuName", dto.getMenuName());
        return R.ok(result);
    }

    /**
     * 编辑菜谱
     * PUT /api/v1/recipe/recipes/{id}
     */
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody RecipeUpdateDTO dto) {
        recipeService.update(id, dto);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("menuName", dto.getMenuName());
        return R.ok(result);
    }

    /**
     * 删除菜谱
     * DELETE /api/v1/recipe/recipes/{id}
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        recipeService.delete(id);
        return R.ok();
    }

    /**
     * 切换菜谱状态
     * PUT /api/v1/recipe/recipes/{id}/status
     */
    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id) {
        recipeService.toggleStatus(id);
        return R.ok();
    }

    /**
     * 菜谱统计数据
     * GET /api/v1/recipe/recipes/statistics
     */
    @GetMapping("/statistics")
    public R<RecipeStatisticsVO> getStatistics(@Valid RecipeQueryDTO query) {
        RecipeStatisticsVO statistics = recipeService.getStatistics(query);
        return R.ok(statistics);
    }

    /**
     * AI营养成分分析
     * GET /api/v1/recipe/recipes/{id}/ai-nutrition
     */
    @GetMapping("/{id}/ai-nutrition")
    public R<AINutritionAnalysisVO> getAiNutrition(@PathVariable Long id) {
        AINutritionAnalysisVO analysis = recipeService.getAiNutrition(id);
        return R.ok(analysis);
    }

    /**
     * 菜谱营养结果
     * GET /api/v1/recipe/recipes/{id}/nutrition-result
     */
    @GetMapping("/{id}/nutrition-result")
    public R<RecipeNutritionResultVO> getNutritionResult(@PathVariable Long id) {
        return R.ok(recipeService.getNutritionResult(id));
    }

    /**
     * AI智能菜谱优化
     * GET /api/v1/recipe/recipes/{id}/ai-optimization
     */
    @GetMapping("/{id}/ai-optimization")
    public R<AIOptimizationAnalysisVO> getAiOptimization(@PathVariable Long id) {
        AIOptimizationAnalysisVO analysis = recipeService.getAiOptimization(id);
        return R.ok(analysis);
    }

    /**
     * AI烹饪参数建议
     * POST /api/v1/recipe/recipes/ai-cooking-suggestion
     */
    @PostMapping("/ai-cooking-suggestion")
    public R<AICookingSuggestionVO> getAiCookingSuggestion(@RequestBody AICookingSuggestionDTO dto) {
        AICookingSuggestionVO suggestion = recipeService.getAiCookingSuggestion(dto);
        return R.ok(suggestion);
    }

    /**
     * 上传菜谱图片
     * POST /api/v1/recipe/recipes/{id}/image
     */
    @PostMapping("/{id}/image")
    public R<Map<String, String>> uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = recipeService.uploadImage(id, file.getBytes(), file.getOriginalFilename());
            Map<String, String> result = new HashMap<>();
            result.put("imageUrl", imageUrl);
            return R.ok(result);
        } catch (Exception e) {
            log.error("上传图片失败", e);
            return R.fail("上传图片失败: " + e.getMessage());
        }
    }
}
