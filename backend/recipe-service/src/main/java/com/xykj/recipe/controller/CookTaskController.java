package com.xykj.recipe.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.recipe.dto.CookTaskCompleteDTO;
import com.xykj.recipe.dto.CookTaskQueryDTO;
import com.xykj.recipe.dto.CookTaskStartDTO;
import com.xykj.recipe.service.CookTaskService;
import com.xykj.recipe.vo.CookAIMonitorVO;
import com.xykj.recipe.vo.CookDashboardVO;
import com.xykj.recipe.vo.CookTaskDetailVO;
import com.xykj.recipe.vo.CookTaskVO;
import com.xykj.recipe.vo.CookTemperaturePointVO;
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

    @GetMapping("/dashboard")
    public R<CookDashboardVO> getDashboard(@ModelAttribute CookTaskQueryDTO query) {
        return R.ok(cookTaskService.getDashboard(query));
    }

    @GetMapping("/tasks")
    public R<PageResult<CookTaskVO>> getTasks(@ModelAttribute CookTaskQueryDTO query) {
        return R.ok(cookTaskService.getTaskPage(query));
    }

    @GetMapping("/tasks/{id}")
    public R<CookTaskDetailVO> getTaskDetail(@PathVariable Long id) {
        return R.ok(cookTaskService.getTaskDetail(id));
    }

    @PostMapping("/tasks/{id}/start")
    public R<CookTaskVO> startTask(@PathVariable Long id, @RequestBody(required = false) CookTaskStartDTO dto) {
        return R.ok(cookTaskService.startTask(id, dto == null ? new CookTaskStartDTO() : dto));
    }

    @PostMapping("/tasks/{id}/complete")
    public R<CookTaskVO> completeTask(@PathVariable Long id, @RequestBody(required = false) CookTaskCompleteDTO dto) {
        return R.ok(cookTaskService.completeTask(id, dto == null ? new CookTaskCompleteDTO() : dto));
    }

    @GetMapping("/tasks/{id}/temperature")
    public R<List<CookTemperaturePointVO>> getTemperatureRecords(@PathVariable Long id) {
        return R.ok(cookTaskService.getTemperatureRecords(id));
    }

    @GetMapping("/tasks/{id}/ai-monitor")
    public R<List<CookAIMonitorVO>> getAiMonitorRecords(@PathVariable Long id) {
        return R.ok(cookTaskService.getAiMonitorRecords(id));
    }
}
