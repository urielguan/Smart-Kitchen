package com.xykj.recipe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.recipe.dto.*;
import com.xykj.recipe.vo.*;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 菜谱服务接口
 */
public interface RecipeService {

    /**
     * 分页查询菜谱列表
     */
    Page<RecipeVO> list(RecipeQueryDTO query);

    /**
     * 获取菜谱详情
     */
    RecipeDetailVO getDetail(Long id);
    /**
     * 创建菜谱
     */
    Long create(RecipeCreateDTO dto);
    /**
     * 更新菜谱
     */
    void update(Long id, RecipeUpdateDTO dto);
    /**
     * 删除菜谱
     */
    void delete(Long id);
    /**
     * 切换菜谱状态
     */
    void toggleStatus(Long id);
    /**
     * 获取统计数据
     */
    RecipeStatisticsVO getStatistics(RecipeQueryDTO query);

    /**
     * 获取数据看板总览
     */
    DashboardOverviewVO getDashboardOverview(DashboardQueryDTO query);
    /**
     * AI营养成分分析
     */
    AINutritionAnalysisVO getAiNutrition(Long id);
    /**
     * AI智能菜谱优化
     */
    AIOptimizationAnalysisVO getAiOptimization(Long id);
    /**
     * AI烹饪参数建议
     */
    AICookingSuggestionVO getAiCookingSuggestion(AICookingSuggestionDTO dto);
    /**
     * 获取菜谱营养结果
     */
    RecipeNutritionResultVO getNutritionResult(Long id);
    /**
     * 上传菜谱图片
     */
    String uploadImage(Long id, byte[] fileData, String originalFilename);
    /**
     * 导出菜谱列表
     */
    void exportRecipes(RecipeQueryDTO query, HttpServletResponse response);

    /**
     * 下载菜谱导入模板
     */
    void downloadImportTemplate(HttpServletResponse response);
    /**
     * 导入菜谱
     */
    RecipeImportResultDTO importRecipes(MultipartFile file);

    /**
     * 下载导入错误文件
     */
    void downloadImportErrorFile(String fileName, HttpServletResponse response);
}
