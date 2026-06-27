package com.xykj.recipe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.recipe.dto.RecipeCategoryCreateDTO;
import com.xykj.recipe.dto.RecipeCategoryQueryDTO;
import com.xykj.recipe.service.RecipeCategoryService;
import com.xykj.recipe.vo.RecipeCategoryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜谱类别管理控制器
 * API路径: /api/v1/recipe/categories
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recipe/categories")
@RequiredArgsConstructor
public class RecipeCategoryController {

    private final RecipeCategoryService categoryService;

    /**
     * 类别列表（分页）
     * GET /api/v1/recipe/categories
     */
    @GetMapping
    public R<PageResult<RecipeCategoryVO>> list(@Valid RecipeCategoryQueryDTO query) {
        Page<RecipeCategoryVO> page = categoryService.list(query);
        PageResult<RecipeCategoryVO> result = new PageResult<>();
        result.setList(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return R.ok(result);
    }

    /**
     * 获取所有启用的类别（用于下拉选择）
     * GET /api/v1/recipe/categories/active
     */
    @GetMapping("/active")
    public R<List<RecipeCategoryVO>> listActive() {
        List<RecipeCategoryVO> list = categoryService.listActive();
        return R.ok(list);
    }

    /**
     * 类别详情
     * GET /api/v1/recipe/categories/{id}
     */
    @GetMapping("/{id}")
    public R<RecipeCategoryVO> getDetail(@PathVariable Long id) {
        RecipeCategoryVO detail = categoryService.getDetail(id);
        return R.ok(detail);
    }

    /**
     * 新增类别
     * POST /api/v1/recipe/categories
     */
    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody RecipeCategoryCreateDTO dto) {
        Long id = categoryService.create(dto);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("categoryCode", dto.getCategoryCode());
        result.put("categoryName", dto.getCategoryName());
        return R.ok(result);
    }

    /**
     * 编辑类别
     * PUT /api/v1/recipe/categories/{id}
     */
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody RecipeCategoryCreateDTO dto) {
        categoryService.update(id, dto);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("categoryName", dto.getCategoryName());
        return R.ok(result);
    }

    /**
     * 删除类别
     * DELETE /api/v1/recipe/categories/{id}
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }

    /**
     * 切换类别状态
     * PUT /api/v1/recipe/categories/{id}/status
     */
    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id) {
        categoryService.toggleStatus(id);
        return R.ok();
    }
}
