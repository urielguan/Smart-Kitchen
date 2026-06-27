package com.xykj.recipe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.recipe.dto.*;
import com.xykj.recipe.service.InventoryValidationService;
import com.xykj.recipe.service.RecipePlanAdjustmentService;
import com.xykj.recipe.service.RecipePlanService;
import com.xykj.recipe.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜谱计划控制器
 * API路径: /api/v1/recipe/plans
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recipe/plans")
@RequiredArgsConstructor
public class RecipePlanController {

    private final RecipePlanService planService;
    private final RecipePlanAdjustmentService adjustmentService;
    private final InventoryValidationService inventoryValidationService;

    /**
     * 菜谱计划列表（分页）
     * GET /api/v1/recipe/plans
     */
    @GetMapping
    public R<PageResult<RecipePlanVO>> list(@Valid RecipePlanQueryDTO query) {
        Page<RecipePlanVO> page = planService.list(query);
        PageResult<RecipePlanVO> result = new PageResult<>();
        result.setList(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return R.ok(result);
    }

    /**
     * 导出菜谱计划
     * GET /api/v1/recipe/plans/export
     */
    @GetMapping("/export")
    public void exportPlans(RecipePlanQueryDTO query, HttpServletResponse response) {
        planService.exportPlans(query, response);
    }

    /**
     * 下载菜谱计划导入模板
     * GET /api/v1/recipe/plans/import/template
     */
    @GetMapping("/import/template")
    public void downloadImportTemplate(HttpServletResponse response) {
        planService.downloadImportTemplate(response);
    }

    /**
     * 导入菜谱计划
     * POST /api/v1/recipe/plans/import
     */
    @PostMapping("/import")
    public R<RecipePlanImportResultDTO> importPlans(@RequestParam("file") MultipartFile file) {
        RecipePlanImportResultDTO result = planService.importPlans(file);
        return R.ok(result);
    }

    /**
     * 下载导入错误文件
     * GET /api/v1/recipe/plans/import/errors/{fileName}
     */
    @GetMapping("/import/errors/{fileName}")
    public void downloadImportErrorFile(@PathVariable String fileName, HttpServletResponse response) {
        planService.downloadImportErrorFile(fileName, response);
    }

    /**
     * 菜谱计划详情
     * GET /api/v1/recipe/plans/{id}
     */
    @GetMapping("/{id}")
    public R<RecipePlanDetailVO> getDetail(@PathVariable Long id) {
        RecipePlanDetailVO detail = planService.getDetail(id);
        return R.ok(detail);
    }

    /**
     * 新增菜谱计划
     * POST /api/v1/recipe/plans
     */
    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody RecipePlanCreateDTO dto) {
        Long id = planService.create(dto);
        RecipePlanDetailVO detail = planService.getDetail(id);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("planCode", detail.getPlanCode());
        result.put("planDate", dto.getPlanDate());

        if (Boolean.TRUE.equals(dto.getUseAiNutrition())) {
            result.put("aiNutritionAnalysis", planService.getAiNutritionAssessment(id));
        }

        return R.ok(result);
    }

    /**
     * 编辑菜谱计划
     * PUT /api/v1/recipe/plans/{id}
     */
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody RecipePlanCreateDTO dto) {
        RecipePlanDetailVO detail = planService.update(id, dto);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("planNo", detail.getPlanCode());
        return R.ok(result);
    }

    /**
     * 删除菜谱计划
     * DELETE /api/v1/recipe/plans/{id}
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return R.ok();
    }

    /**
     * 提交菜谱计划审核
     * POST /api/v1/recipe/plans/{id}/submit
     */
    @PostMapping("/{id}/submit")
    public R<Map<String, Object>> submit(@PathVariable Long id) {
        String status = planService.submit(id);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("status", status);
        return R.ok(result);
    }

    /**
     * 审核菜谱计划
     * PUT /api/v1/recipe/plans/{id}/audit
     */
    @PutMapping("/{id}/audit")
    public R<Map<String, Object>> audit(@PathVariable Long id, @RequestBody AuditDTO dto) {
        AuditResultVO result = planService.audit(id, dto.getStatus(), dto.getRemark());
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", result.getStatus());
        response.put("cookTaskGenerated", result.getCookTaskGenerated());
        return R.ok(response);
    }

    /**
     * 驳回后重新提交菜谱计划
     * PUT /api/v1/recipe/plans/{id}/resubmit
     */
    @PutMapping("/{id}/resubmit")
    public R<Map<String, Object>> resubmit(@PathVariable Long id, @RequestBody RecipePlanCreateDTO dto) {
        RecipePlanDetailVO detail = planService.resubmit(id, dto);
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", detail.getStatus());
        return R.ok(response);
    }

    /**
     * 复制菜谱计划
     * POST /api/v1/recipe/plans/{id}/copy
     */
    @PostMapping("/{id}/copy")
    public R<CopyPlanResultVO> copyPlan(@PathVariable Long id) {
        CopyPlanResultVO result = planService.copyPlan(id);
        return R.ok(result);
    }

    /**
     * 撤回已审核的菜谱计划
     * PUT /api/v1/recipe/plans/{id}/withdraw
     */
    @PutMapping("/{id}/withdraw")
    public R<Map<String, Object>> withdraw(@PathVariable Long id, @Valid @RequestBody WithdrawDTO dto) {
        planService.withdraw(id, dto.getReason());
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "draft");
        return R.ok(response);
    }

    /**
     * 获取计划审批历史
     * GET /api/v1/recipe/plans/{id}/audit-log
     */
    @GetMapping("/{id}/audit-log")
    public R<List<RecipePlanAuditLogVO>> getAuditLog(@PathVariable Long id) {
        return R.ok(planService.getAuditLog(id));
    }

    /**
     * AI营养评估
     * GET /api/v1/recipe/plans/{id}/ai-nutrition-assessment
     */
    @GetMapping("/{id}/ai-nutrition-assessment")
    public R<Object> getAiNutritionAssessment(@PathVariable Long id) {
        Object assessment = planService.getAiNutritionAssessment(id);
        return R.ok(assessment);
    }

    /**
     * AI智能推荐菜谱
     * POST /api/v1/recipe/plans/ai-recommend
     */
    @PostMapping("/ai-recommend")
    public R<Map<String, Object>> getAiRecommendRecipes(@RequestBody RecipePlanQueryDTO query) {
        AIRecommendResultVO enhanced = planService.getAiRecommendRecipesEnhanced(query);

        Map<String, Object> response = new HashMap<>();

        Map<String, Object> recommendStats = new HashMap<>();
        if (enhanced.getStatistics() != null) {
            recommendStats.put("recommendCount", enhanced.getStatistics().getTotalRecipes());
            recommendStats.put("estimateTotalCost", enhanced.getStatistics().getEstimatedTotalCost());
            recommendStats.put("perCapitaCost", enhanced.getStatistics().getPerCapitaCost());
            recommendStats.put("budgetRemaining", enhanced.getStatistics().getBudgetRemaining());
        }
        response.put("recommendStats", recommendStats);

        // 按文档结构返回 recommendRecipes: [{ mealType, recipes }]
        Map<String, Object> mealRecommendation = new HashMap<>();
        mealRecommendation.put("mealType", query.getMealType());
        mealRecommendation.put("recipes", enhanced.getRecipes());
        response.put("recommendRecipes", List.of(mealRecommendation));

        Map<String, Object> nutritionOverview = new HashMap<>();
        if (enhanced.getNutritionOverview() != null) {
            nutritionOverview.put("totalProtein", enhanced.getNutritionOverview().getTotalProtein());
            nutritionOverview.put("totalCarbs", enhanced.getNutritionOverview().getTotalCarbohydrate());
            nutritionOverview.put("totalFat", enhanced.getNutritionOverview().getTotalFat());
            nutritionOverview.put("totalCalories", enhanced.getNutritionOverview().getTotalCalories());
        }
        response.put("nutritionOverview", nutritionOverview);

        return R.ok(response);
    }

    /**
     * AI智能推荐菜谱（增强版，支持预算和周计划）
     * POST /api/v1/recipe/plans/ai-recommend-enhanced
     */
    @PostMapping("/ai-recommend-enhanced")
    public R<AIRecommendResultVO> getAiRecommendRecipesEnhanced(@RequestBody RecipePlanQueryDTO query) {
        AIRecommendResultVO result = planService.getAiRecommendRecipesEnhanced(query);
        return R.ok(result);
    }

    /**
     * 菜谱计划调整申请
     * POST /api/v1/recipe/plans/{id}/adjust
     */
    @PostMapping("/{id}/adjust")
    public R<Map<String, Object>> createAdjustment(
            @PathVariable Long id,
            @Valid @RequestBody AdjustmentDTO dto) {
        AdjustmentResultVO result = adjustmentService.createAdjustment(id, dto);
        Map<String, Object> response = new HashMap<>();
        response.put("id", result.getId());
        response.put("planId", result.getPlanId());
        response.put("adjustCode", result.getAdjustCode());
        response.put("status", result.getStatus());
        return R.ok(response);
    }

    /**
     * 菜谱计划统计
     * GET /api/v1/recipe/plans/statistics
     */
    @GetMapping("/statistics")
    public R<RecipePlanStatisticsVO> getStatistics() {
        return R.ok(planService.getStatistics());
    }

    /**
     * 库存校验
     * GET /api/v1/recipe/plans/{id}/validate-stock
     */
    @GetMapping("/{id}/validate-stock")
    public R<StockValidationDTO> validateStock(@PathVariable Long id) {
        StockValidationDTO result = inventoryValidationService.validateRecipePlanStock(id);
        return R.ok(result);
    }

    /**
     * 获取菜谱计划食材汇总
     * GET /api/v1/recipe/plans/{id}/materials
     */
    @GetMapping("/{id}/materials")
    public R<List<RecipePlanMaterialSummaryVO>> getMaterialSummary(@PathVariable Long id) {
        List<RecipePlanMaterialSummaryVO> result = inventoryValidationService.getMaterialSummary(id);
        return R.ok(result);
    }

    /**
     * 批量删除菜谱计划
     * POST /api/v1/recipe/plans/batch-delete
     */
    @PostMapping("/batch-delete")
    public R<BatchOperationResultVO> batchDelete(@Valid @RequestBody BatchDeleteDTO dto) {
        BatchOperationResultVO result = planService.batchDelete(dto.getPlanIds());
        return R.ok(result);
    }

    /**
     * 批量审核菜谱计划
     * PUT /api/v1/recipe/plans/batch-audit
     */
    @PutMapping("/batch-audit")
    public R<BatchOperationResultVO> batchAudit(@Valid @RequestBody BatchAuditDTO dto) {
        BatchOperationResultVO result = planService.batchAudit(dto.getPlanIds(), dto.getStatus(), dto.getRemark());
        return R.ok(result);
    }

    /**
     * 审核DTO
     */
    @lombok.Data
    public static class AuditDTO {
        private String status;
        private String remark;
    }

    /**
     * 调整申请DTO
     */
    @lombok.Data
    public static class AdjustmentDTO {
        @NotBlank(message = "调整原因不能为空")
        private String adjustReason;
        @NotBlank(message = "调整类型不能为空")
        private String adjustType;
        @NotBlank(message = "调整数据不能为空")
        private String afterData;
    }

    /**
     * 撤回原因DTO
     */
    @lombok.Data
    public static class WithdrawDTO {
        @NotBlank(message = "撤回原因不能为空")
        private String reason;
    }

    /**
     * 批量删除DTO
     */
    @lombok.Data
    public static class BatchDeleteDTO {
        @jakarta.validation.constraints.NotEmpty(message = "计划ID列表不能为空")
        private List<Long> planIds;
    }

    /**
     * 批量审核DTO
     */
    @lombok.Data
    public static class BatchAuditDTO {
        @jakarta.validation.constraints.NotEmpty(message = "计划ID列表不能为空")
        private List<Long> planIds;
        @NotBlank(message = "审核状态不能为空")
        private String status;
        private String remark;
    }
}
