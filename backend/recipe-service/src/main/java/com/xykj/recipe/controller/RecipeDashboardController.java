package com.xykj.recipe.controller;

import com.xykj.common.result.R;
import com.xykj.recipe.dto.DashboardQueryDTO;
import com.xykj.recipe.dto.RecipeQueryDTO;
import com.xykj.recipe.service.RecipeService;
import com.xykj.recipe.vo.DashboardOverviewVO;
import com.xykj.recipe.vo.RecipeStatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 菜谱可视化看板控制器
 * API路径: /api/v1/recipe/dashboard
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recipe")
@RequiredArgsConstructor
public class RecipeDashboardController {

    private final RecipeService recipeService;

    /**
     * 菜谱可视化看板
     * GET /api/v1/recipe/dashboard
     */
    @GetMapping("/dashboard")
    public R<RecipeStatisticsVO> getDashboard(@ModelAttribute RecipeQueryDTO query) {
        RecipeStatisticsVO statistics = recipeService.getStatistics(query);
        return R.ok(statistics);
    }

    /**
     * 数据看板总览
     * GET /api/v1/recipe/dashboard/overview
     */
    @GetMapping("/dashboard/overview")
    public R<DashboardOverviewVO> getDashboardOverview(@ModelAttribute DashboardQueryDTO query) {
        DashboardOverviewVO overview = recipeService.getDashboardOverview(query);
        return R.ok(overview);
    }
}
