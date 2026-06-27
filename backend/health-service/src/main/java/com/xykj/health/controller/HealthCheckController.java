package com.xykj.health.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.health.dto.AiCheckDTO;
import com.xykj.health.dto.HealthCheckCreateDTO;
import com.xykj.health.dto.HealthCheckQueryDTO;
import com.xykj.health.dto.HealthCheckUpdateDTO;
import com.xykj.health.scheduler.HealthTaskScheduler;
import com.xykj.health.service.HealthCheckService;
import com.xykj.health.service.HealthTaskLinkageService;
import com.xykj.health.vo.AiCheckResultVO;
import com.xykj.health.vo.HealthCheckDetailVO;
import com.xykj.health.vo.HealthCheckLinkageVersionVO;
import com.xykj.health.vo.HealthCheckRecordVO;
import com.xykj.health.vo.HealthDashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 晨检记录控制器
 * API路径: /api/v1/health
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "晨检管理", description = "员工晨检记录管理接口")
public class HealthCheckController {

    private final HealthCheckService healthCheckService;
    private final HealthTaskScheduler healthTaskScheduler;
    private final HealthTaskLinkageService healthTaskLinkageService;

    /**
     * 晨检管理看板
     * GET /api/v1/health/dashboard
     */
    @GetMapping("/dashboard")
    @Operation(summary = "晨检看板", description = "获取今日晨检统计数据")
    public R<HealthDashboardVO> getDashboard(HealthCheckQueryDTO query) {
        query.setOrgId(null);
        return R.ok(healthCheckService.getDashboard(query));
    }

    /**
     * 晨检联动版本
     * GET /api/v1/health/linkage/version
     */
    @GetMapping("/linkage/version")
    @Operation(summary = "晨检联动版本", description = "获取晨检任务异动联动版本，用于前端即时刷新")
    public R<HealthCheckLinkageVersionVO> getLinkageVersion() {
        return R.ok(healthCheckService.getLinkageVersion());
    }

    /**
     * 待晨检列表
     * GET /api/v1/health/records/pending
     */
    @GetMapping("/records/pending")
    @Operation(summary = "待晨检列表", description = "获取今日待晨检员工列表")
    public R<PageResult<HealthCheckRecordVO>> getPendingList(HealthCheckQueryDTO query) {
        return R.ok(healthCheckService.getPendingList(query));
    }

    /**
     * 已完成/全部记录列表（分页）
     * GET /api/v1/health/records
     */
    @GetMapping("/records")
    @Operation(summary = "晨检记录列表", description = "获取已完成晨检记录（分页）")
    public R<PageResult<HealthCheckRecordVO>> getRecords(HealthCheckQueryDTO query) {
        query.setOrgId(null);
        return R.ok(healthCheckService.getRecordPage(query));
    }

    /**
     * 执行晨检（传统方式，手动选择员工）
     * POST /api/v1/health/records
     */
    @PostMapping("/records")
    @Operation(summary = "执行晨检", description = "手动选择员工执行晨检")
    public R<HealthCheckDetailVO> createRecord(@Valid @RequestBody HealthCheckCreateDTO dto) {
        log.info("执行晨检: employeeId={}", dto.getEmployeeId());
        return R.ok(healthCheckService.createRecord(dto));
    }

    /**
     * 晨检记录详情
     * GET /api/v1/health/records/{id}
     */
    @GetMapping("/records/{id}")
    @Operation(summary = "晨检记录详情", description = "获取晨检记录详细信息")
    public R<HealthCheckDetailVO> getRecordDetail(
            @Parameter(description = "记录ID") @PathVariable Long id) {
        return R.ok(healthCheckService.getRecordDetail(id));
    }

    /**
     * 更新晨检记录
     * PUT /api/v1/health/records/{id}
     */
    @PutMapping("/records/{id}")
    @Operation(summary = "更新晨检记录", description = "更新晨检记录信息")
    public R<HealthCheckDetailVO> updateRecord(
            @Parameter(description = "记录ID") @PathVariable Long id,
            @Valid @RequestBody HealthCheckUpdateDTO dto) {
        return R.ok(healthCheckService.updateRecord(id, dto));
    }

    /**
     * 归档晨检记录
     * PUT /api/v1/health/records/{id}/archive
     */
    @PutMapping("/records/{id}/archive")
    @Operation(summary = "归档晨检记录", description = "将晨检记录标记为已归档")
    public R<HealthCheckDetailVO> archiveRecord(
            @Parameter(description = "记录ID") @PathVariable Long id) {
        return R.ok(healthCheckService.archiveRecord(id));
    }

    /**
     * AI一键晨检
     * POST /api/v1/health/check-records/ai-check
     */
    @PostMapping("/check-records/ai-check")
    @Operation(summary = "AI一键晨检", description = "传入人脸照片，自动完成人脸识别+体温+健康证+手部/着装全流程检查")
    public R<AiCheckResultVO> aiCheck(@Valid @RequestBody AiCheckDTO dto) {
        log.info("AI一键晨检: expectedEmployeeId={}, deviceId={}", dto.getExpectedEmployeeId(), dto.getDeviceId());
        return R.ok(healthCheckService.aiCheck(dto));
    }

    /**
     * 手动触发生成待晨检记录
     */
    @PostMapping("/generate-pending")
    @Operation(summary = "手动生成待晨检记录", description = "根据活跃员工自动生成今日待晨检记录")
    public R<String> generatePendingTasks() {
        healthTaskScheduler.autoGenerateHealthCheckTasks();
        healthTaskLinkageService.reconcileTodayTasks();
        return R.ok("已触发晨检任务生成");
    }
}
