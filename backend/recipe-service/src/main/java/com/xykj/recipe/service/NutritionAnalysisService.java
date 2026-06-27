package com.xykj.recipe.service;

import com.xykj.recipe.dto.NutritionAnalysisDTO;
import com.xykj.recipe.vo.AINutritionAssessmentVO;
import com.xykj.recipe.vo.NutritionTargetVO;

import java.util.List;
import java.util.Map;

/**
 * 营养分析服务接口
 */
public interface NutritionAnalysisService {

    /**
     * 分析营养
     */
    AINutritionAssessmentVO analyzeNutrition(NutritionAnalysisDTO dto);

    /**
     * 获取营养目标
     */
    NutritionTargetVO getNutritionTargets(String targetGroup, String healthStatus);

    /**
     * 获取目标人群列表
     */
    List<Map<String, Object>> getTargetGroups();

    /**
     * 获取健康状况列表
     */
    List<Map<String, Object>> getHealthStatuses();
}
