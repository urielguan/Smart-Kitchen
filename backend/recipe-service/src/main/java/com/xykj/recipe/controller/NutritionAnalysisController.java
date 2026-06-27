package com.xykj.recipe.controller;

import com.xykj.common.result.R;
import com.xykj.recipe.dto.NutritionAnalysisDTO;
import com.xykj.recipe.service.NutritionAnalysisService;
import com.xykj.recipe.vo.AINutritionAssessmentVO;
import com.xykj.recipe.vo.NutritionTargetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 营养分析控制器
 */
@Tag(name = "营养分析", description = "营养评估和目标查询接口")
@RestController
@RequestMapping("/api/v1/recipe/nutrition")
@RequiredArgsConstructor
public class NutritionAnalysisController {

    private final NutritionAnalysisService nutritionAnalysisService;

    /**
     * 分析营养
     */
    @Operation(summary = "分析营养", description = "根据选择的菜谱和就餐人数分析营养情况")
    @PostMapping("/analyze")
    public R<AINutritionAssessmentVO> analyzeNutrition(
            @Valid @RequestBody NutritionAnalysisDTO dto) {
        return R.ok(nutritionAnalysisService.analyzeNutrition(dto));
    }

    /**
     * 获取营养目标
     */
    @Operation(summary = "获取营养目标", description = "根据目标人群获取营养目标参考值")
    @GetMapping("/targets/{targetGroup}")
    public R<NutritionTargetVO> getNutritionTargets(
            @Parameter(description = "目标人群") @PathVariable String targetGroup,
            @Parameter(description = "健康状况（逗号分隔）") @RequestParam(required = false) String healthStatus) {
        return R.ok(nutritionAnalysisService.getNutritionTargets(targetGroup, healthStatus));
    }

    /**
     * 获取所有目标人群
     */
    @Operation(summary = "获取目标人群列表", description = "获取所有可用的目标人群选项")
    @GetMapping("/target-groups")
    public R<?> getTargetGroups() {
        return R.ok(nutritionAnalysisService.getTargetGroups());
    }

    /**
     * 获取所有健康状况
     */
    @Operation(summary = "获取健康状况列表", description = "获取所有可用的健康状况选项")
    @GetMapping("/health-statuses")
    public R<?> getHealthStatuses() {
        return R.ok(nutritionAnalysisService.getHealthStatuses());
    }
}
