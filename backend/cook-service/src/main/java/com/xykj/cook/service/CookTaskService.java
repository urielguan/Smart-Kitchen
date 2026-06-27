package com.xykj.cook.service;

import com.xykj.common.result.PageResult;
import com.xykj.cook.dto.CookTaskArchiveDTO;
import com.xykj.cook.dto.CookTaskAssignDTO;
import com.xykj.cook.dto.CookTaskCancelDTO;
import com.xykj.cook.dto.CookTaskCompleteDTO;
import com.xykj.cook.dto.CookTaskQueryDTO;
import com.xykj.cook.dto.CookTaskStartDTO;
import com.xykj.cook.dto.TemperatureRecordDTO;
import com.xykj.cook.entity.CookTask;
import com.xykj.cook.vo.*;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 烹饪任务服务接口
 */
public interface CookTaskService {

    /**
     * 获取烹饪看板数据
     */
    CookDashboardVO getDashboard(CookTaskQueryDTO query);

    /**
     * 分页查询烹饪任务
     */
    PageResult<CookTaskVO> getTaskPage(CookTaskQueryDTO query);

    /**
     * 获取烹饪任务详情
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
     * 取消烹饪任务
     */
    CookTaskVO cancelTask(Long id, CookTaskCancelDTO dto);

    /**
     * 归档/交接烹饪任务
     */
    CookTaskVO archiveTask(Long id, CookTaskArchiveDTO dto);

    /**
     * 获取温度记录
     */
    List<CookTemperaturePointVO> getTemperatureRecords(Long taskId);

    /**
     * 获取温度增量记录（sinceId 之后的记录）
     */
    List<CookTemperaturePointVO> getTemperatureRecordsSince(Long taskId, Long sinceId);

    /**
     * 上报温度记录
     */
    void reportTemperature(TemperatureRecordDTO dto);

    /**
     * 获取AI监控记录
     */
    List<CookAIMonitorVO> getAiMonitorRecords(Long taskId);

    /**
     * 确认AI预警记录
     */
    void acknowledgeAlert(Long taskId, Integer alertIndex, String operatorName);

    /**
     * 获取任务操作时间线（聚合温度+AI+状态变更）
     */
    List<CookTimelineEventVO> getTaskTimeline(Long taskId);

    /**
     * 根据计划ID查询烹饪任务
     */
    List<CookTask> getTasksByPlanId(Long planId);

    /**
     * 根据菜谱计划生成烹饪任务（供recipe-service调用）
     */
    List<CookTask> generateTasksFromPlan(Long planId);

    /**
     * 取消计划相关的所有烹饪任务
     */
    void cancelTasksByPlanId(Long planId);

    /**
     * 确认温度异常（主管操作）
     */
    CookTaskVO confirmTempAbnormal(Long id);

    /**
     * 分派厨师（主管操作）
     */
    CookTaskVO assignChef(Long id, CookTaskAssignDTO dto);

    /**
     * 导出烹饪记录Excel
     */
    void exportTasks(CookTaskQueryDTO query, HttpServletResponse response);
}
