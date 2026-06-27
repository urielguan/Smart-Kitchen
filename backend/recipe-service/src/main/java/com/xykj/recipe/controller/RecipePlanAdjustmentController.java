package com.xykj.recipe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.recipe.dto.RecipePlanQueryDTO;
import com.xykj.recipe.service.RecipePlanAdjustmentService;
import com.xykj.recipe.vo.AdjustmentAuditResultVO;
import com.xykj.recipe.vo.RecipePlanAdjustmentDetailVO;
import com.xykj.recipe.vo.RecipePlanAdjustmentVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 菜谱计划调整申请控制器
 * API路径: /api/v1/recipe/plan-adjustments
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recipe/plan-adjustments")
@RequiredArgsConstructor
public class RecipePlanAdjustmentController {

    private final RecipePlanAdjustmentService adjustmentService;

    /**
     * 调整申请列表
     * GET /api/v1/recipe/plan-adjustments
     */
    @GetMapping
    public R<PageResult<RecipePlanAdjustmentVO>> list(@Valid RecipePlanQueryDTO query) {
        Page<RecipePlanAdjustmentVO> page = adjustmentService.list(query);
        return R.ok(PageResult.of(page));
    }

    /**
     * 导出调整申请列表
     * GET /api/v1/recipe/plan-adjustments/export
     */
    @GetMapping("/export")
    public void exportAdjustments(@Valid RecipePlanQueryDTO query, HttpServletResponse response) {
        adjustmentService.exportAdjustments(query, response);
    }

    /**
     * 调整申请详情
     * GET /api/v1/recipe/plan-adjustments/{id}
     */
    @GetMapping("/{id}")
    public R<RecipePlanAdjustmentDetailVO> getDetail(@PathVariable Long id) {
        return R.ok(adjustmentService.getDetail(id));
    }

    /**
     * 审核调整申请
     * PUT /api/v1/recipe/plan-adjustments/{id}/audit
     */
    @PutMapping("/{id}/audit")
    public R<java.util.Map<String, Object>> auditAdjustment(@PathVariable Long id, @RequestBody AuditDTO dto) {
        AdjustmentAuditResultVO result = adjustmentService.auditAdjustment(id, dto.getStatus(), dto.getRemark());
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", result.getId());
        response.put("planId", result.getPlanId());
        response.put("status", result.getStatus());
        response.put("cookTaskUpdated", result.getCookTaskUpdated());
        return R.ok(response);
    }

    /**
     * 审核DTO
     */
    @lombok.Data
    public static class AuditDTO {
        private String status;
        private String remark;
    }
}
