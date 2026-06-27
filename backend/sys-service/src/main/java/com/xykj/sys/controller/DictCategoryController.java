package com.xykj.sys.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sys.dto.DictCategoryAreaSuggestionDTO;
import com.xykj.sys.dto.DictCategoryAreaCoefficientRecalcDTO;
import com.xykj.sys.dto.DictCategoryCreateDTO;
import com.xykj.sys.dto.DictCategoryQueryDTO;
import com.xykj.sys.dto.DictCategoryStatusDTO;
import com.xykj.sys.dto.DictCategoryUpdateDTO;
import com.xykj.sys.service.DictCategoryService;
import com.xykj.sys.vo.DictCategoryAreaSuggestionVO;
import com.xykj.sys.vo.DictCategoryAreaCoefficientHistoryVO;
import com.xykj.sys.vo.DictCategoryAreaCoefficientRecalcTaskVO;
import com.xykj.sys.vo.DictCategoryDetailVO;
import com.xykj.sys.vo.DictCategoryItemVO;
import com.xykj.sys.vo.DictCategoryMetaVO;
import com.xykj.sys.vo.DictCategoryOptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 字典分类维护控制器
 */
@RestController
@RequestMapping("/api/v1/sys/dict-categories")
@RequiredArgsConstructor
public class DictCategoryController {

    private final DictCategoryService dictCategoryService;

    @GetMapping("/categories")
    public R<List<DictCategoryMetaVO>> listCategories() {
        return R.ok(dictCategoryService.listCategories());
    }

    @GetMapping
    public R<PageResult<DictCategoryItemVO>> list(DictCategoryQueryDTO queryDTO) {
        return R.ok(dictCategoryService.list(queryDTO));
    }

    @GetMapping("/{id}")
    public R<DictCategoryDetailVO> getDetail(@PathVariable Long id, @RequestParam String categoryType) {
        return R.ok(dictCategoryService.getDetail(categoryType, id));
    }

    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody DictCategoryCreateDTO dto) {
        return R.ok(dictCategoryService.create(dto));
    }

    @PutMapping("/{id}")
    public R<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestParam String categoryType,
            @Valid @RequestBody DictCategoryUpdateDTO dto) {
        return R.ok(dictCategoryService.update(categoryType, id, dto));
    }

    @PutMapping("/{id}/status")
    public R<Map<String, Object>> updateStatus(
            @PathVariable Long id,
            @RequestParam String categoryType,
            @Valid @RequestBody DictCategoryStatusDTO dto) {
        return R.ok(dictCategoryService.updateStatus(categoryType, id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, @RequestParam String categoryType) {
        dictCategoryService.delete(categoryType, id);
        return R.ok();
    }

    @GetMapping("/options")
    public R<List<DictCategoryOptionVO>> getOptions(
            @RequestParam String categoryType,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return R.ok(dictCategoryService.getOptions(categoryType, includeInactive));
    }

    @PostMapping("/area-coefficient-suggestion")
    public R<DictCategoryAreaSuggestionVO> getAreaCoefficientSuggestion(
            @Valid @RequestBody DictCategoryAreaSuggestionDTO dto) {
        return R.ok(dictCategoryService.getAreaCoefficientSuggestion(dto));
    }

    @GetMapping("/{id}/area-coefficient-history")
    public R<List<DictCategoryAreaCoefficientHistoryVO>> getAreaCoefficientHistory(
            @PathVariable Long id,
            @RequestParam String categoryType) {
        return R.ok(dictCategoryService.getAreaCoefficientHistory(categoryType, id));
    }

    @PostMapping("/{id}/area-coefficient-recalc")
    public R<Map<String, Object>> startAreaCoefficientRecalc(
            @PathVariable Long id,
            @RequestParam String categoryType,
            @Valid @RequestBody DictCategoryAreaCoefficientRecalcDTO dto) {
        return R.ok(dictCategoryService.startAreaCoefficientRecalc(categoryType, id, dto));
    }

    @GetMapping("/{id}/area-coefficient-recalc-tasks/{taskId}")
    public R<DictCategoryAreaCoefficientRecalcTaskVO> getAreaCoefficientRecalcTaskDetail(
            @PathVariable Long id,
            @PathVariable Long taskId,
            @RequestParam String categoryType) {
        return R.ok(dictCategoryService.getAreaCoefficientRecalcTaskDetail(categoryType, id, taskId));
    }
}
