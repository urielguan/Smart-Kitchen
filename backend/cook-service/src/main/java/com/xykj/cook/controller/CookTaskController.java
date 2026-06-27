package com.xykj.cook.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.cook.dto.*;
import com.xykj.cook.service.CookTaskService;
import com.xykj.cook.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 烹饪记录管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cook")
@RequiredArgsConstructor
public class CookTaskController {

    private final CookTaskService cookTaskService;

    /**
     * 获取烹饪看板数据
     */
    @GetMapping("/dashboard")
    public R<CookDashboardVO> getDashboard(@ModelAttribute CookTaskQueryDTO query) {
        return R.ok(cookTaskService.getDashboard(query));
    }

    /**
     * 导出烹饪记录Excel
     */
    @GetMapping("/tasks/export")
    public void exportTasks(@ModelAttribute CookTaskQueryDTO query, HttpServletResponse response) {
        cookTaskService.exportTasks(query, response);
    }

    /**
     * 分页查询烹饪任务
     */
    @GetMapping("/tasks")
    public R<PageResult<CookTaskVO>> getTasks(@ModelAttribute CookTaskQueryDTO query) {
        return R.ok(cookTaskService.getTaskPage(query));
    }

    /**
     * 获取烹饪任务详情
     */
    @GetMapping("/tasks/{id}")
    public R<CookTaskDetailVO> getTaskDetail(@PathVariable Long id) {
        return R.ok(cookTaskService.getTaskDetail(id));
    }

    /**
     * 开始烹饪
     */
    @PostMapping("/tasks/{id}/start")
    public R<CookTaskVO> startTask(@PathVariable Long id, @RequestBody(required = false) CookTaskStartDTO dto) {
        return R.ok(cookTaskService.startTask(id, dto != null ? dto : new CookTaskStartDTO()));
    }

    /**
     * 完成烹饪
     */
    @PostMapping("/tasks/{id}/complete")
    public R<CookTaskVO> completeTask(@PathVariable Long id, @RequestBody(required = false) CookTaskCompleteDTO dto) {
        return R.ok(cookTaskService.completeTask(id, dto != null ? dto : new CookTaskCompleteDTO()));
    }

    /**
     * 取消烹饪任务
     */
    @PostMapping("/tasks/{id}/cancel")
    public R<CookTaskVO> cancelTask(@PathVariable Long id, @RequestBody(required = false) CookTaskCancelDTO dto) {
        return R.ok(cookTaskService.cancelTask(id, dto != null ? dto : new CookTaskCancelDTO()));
    }

    /**
     * 归档/交接烹饪任务
     */
    @PostMapping("/tasks/{id}/archive")
    public R<CookTaskVO> archiveTask(@PathVariable Long id, @RequestBody(required = false) CookTaskArchiveDTO dto) {
        return R.ok(cookTaskService.archiveTask(id, dto != null ? dto : new CookTaskArchiveDTO()));
    }

    /**
     * 确认温度异常（主管操作）
     */
    @PostMapping("/tasks/{id}/confirm-temp-abnormal")
    public R<CookTaskVO> confirmTempAbnormal(@PathVariable Long id) {
        return R.ok(cookTaskService.confirmTempAbnormal(id));
    }

    /**
     * 分派厨师（主管操作）
     */
    @PostMapping("/tasks/{id}/assign")
    public R<CookTaskVO> assignChef(@PathVariable Long id, @RequestBody(required = false) CookTaskAssignDTO dto) {
        return R.ok(cookTaskService.assignChef(id, dto != null ? dto : new CookTaskAssignDTO()));
    }

    /**
     * 获取温度记录
     */
    @GetMapping("/tasks/{id}/temperature")
    public R<List<CookTemperaturePointVO>> getTemperatureRecords(@PathVariable Long id) {
        return R.ok(cookTaskService.getTemperatureRecords(id));
    }

    /**
     * 获取温度增量记录
     */
    @GetMapping("/tasks/{id}/temperature/incremental")
    public R<List<CookTemperaturePointVO>> getTemperatureRecordsSince(
            @PathVariable Long id,
            @RequestParam Long sinceId) {
        return R.ok(cookTaskService.getTemperatureRecordsSince(id, sinceId));
    }

    /**
     * 上报温度记录
     */
    @PostMapping("/temperature")
    public R<Void> reportTemperature(@RequestBody TemperatureRecordDTO dto) {
        cookTaskService.reportTemperature(dto);
        return R.ok();
    }

    /**
     * 获取AI监控记录
     */
    @GetMapping("/tasks/{id}/ai-monitor")
    public R<List<CookAIMonitorVO>> getAiMonitorRecords(@PathVariable Long id) {
        return R.ok(cookTaskService.getAiMonitorRecords(id));
    }

    /**
     * 确认AI预警记录
     */
    @PostMapping("/tasks/{id}/alerts/{alertIndex}/acknowledge")
    public R<Void> acknowledgeAlert(@PathVariable Long id, @PathVariable Integer alertIndex) {
        cookTaskService.acknowledgeAlert(id, alertIndex, null);
        return R.ok();
    }

    /**
     * 获取任务操作时间线
     */
    @GetMapping("/tasks/{id}/timeline")
    public R<List<CookTimelineEventVO>> getTaskTimeline(@PathVariable Long id) {
        return R.ok(cookTaskService.getTaskTimeline(id));
    }
}
