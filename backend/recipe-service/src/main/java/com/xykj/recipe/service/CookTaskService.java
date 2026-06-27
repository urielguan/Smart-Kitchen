package com.xykj.recipe.service;

import com.xykj.common.result.PageResult;
import com.xykj.recipe.dto.CookTaskCompleteDTO;
import com.xykj.recipe.dto.CookTaskQueryDTO;
import com.xykj.recipe.dto.CookTaskStartDTO;
import com.xykj.recipe.entity.CookTask;
import com.xykj.recipe.vo.CookAIMonitorVO;
import com.xykj.recipe.vo.CookDashboardVO;
import com.xykj.recipe.vo.CookTaskDetailVO;
import com.xykj.recipe.vo.CookTaskVO;
import com.xykj.recipe.vo.CookTemperaturePointVO;

import java.util.List;

/**
 * 烹饪任务服务接口
 */
public interface CookTaskService {

    /**
     * 根据菜谱计划生成烹饪任务
     * @param planId 菜谱计划ID
     * @return 生成的任务列表
     */
    List<CookTask> generateTasksFromPlan(Long planId);

    /**
     * 根据计划ID查询烹饪任务
     * @param planId 计划ID
     * @return 任务列表
     */
    List<CookTask> getTasksByPlanId(Long planId);

    /**
     * 烹饪首页看板
     */
    CookDashboardVO getDashboard(CookTaskQueryDTO query);

    /**
     * 烹饪任务列表
     */
    PageResult<CookTaskVO> getTaskPage(CookTaskQueryDTO query);

    /**
     * 烹饪任务详情
     */
    CookTaskDetailVO getTaskDetail(Long id);

    /**
     * 开始烹饪
     */
    CookTaskVO startTask(Long id, CookTaskStartDTO dto);

    /**
     * 完成烹饪
     */
    CookTaskVO completeTask(Long id, CookTaskCompleteDTO dto);

    /**
     * 温度曲线
     */
    List<CookTemperaturePointVO> getTemperatureRecords(Long id);

    /**
     * AI监控记录
     */
    List<CookAIMonitorVO> getAiMonitorRecords(Long id);

    /**
     * 取消计划相关的所有烹饪任务
     * @param planId 计划ID
     */
    void cancelTasksByPlanId(Long planId);

    /**
     * 取消计划下未来未开始的烹饪任务
     * @param planId 计划ID
     * @return 取消的任务数量
     */
    int cancelPendingFutureTasksByPlanId(Long planId);

    /**
     * 调整任务（调整申请通过后调用）
     * @param planId 计划ID
     */
    void adjustTasksForPlan(Long planId);

    /**
     * 取消指定计划下指定菜谱从生效日期开始的未来待执行任务
     * @param planId 计划ID
     * @param recipeIds 菜谱ID集合
     * @param effectiveDate 生效日期（含）
     * @return 取消数量
     */
    int cancelPendingFutureTasksByPlanIdAndRecipeIds(Long planId, java.util.Set<Long> recipeIds, java.time.LocalDate effectiveDate);

    /**
     * 同步指定计划下未来待执行任务的菜谱展示与份数
     * @param planId 计划ID
     * @param targetItems 目标计划项，key 为 recipeId
     * @param effectiveDate 生效日期（含）
     */
    void syncPendingFutureTasksForPlanItems(Long planId, java.util.Map<Long, com.xykj.recipe.entity.RecipePlanItem> targetItems, java.time.LocalDate effectiveDate);

    /**
     * 为指定计划的指定菜谱从生效日期开始补未来任务
     * @param planId 计划ID
     * @param effectiveDate 生效日期（含）
     * @param recipeIds 菜谱ID集合
     * @return 新增任务列表
     */
    java.util.List<CookTask> generateFutureTasksForPlan(Long planId, java.time.LocalDate effectiveDate, java.util.Set<Long> recipeIds);
}
