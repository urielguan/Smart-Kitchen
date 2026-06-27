package com.xykj.sample.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sample.dto.BatchArchiveDTO;
import com.xykj.sample.dto.BatchVoidDTO;
import com.xykj.sample.dto.SampleHistoryTaskQueryDTO;
import com.xykj.sample.dto.SampleManualDisposalSupplementDTO;
import com.xykj.sample.dto.SampleOperationLockAcquireDTO;
import com.xykj.sample.dto.SampleOperationLockRefreshDTO;
import com.xykj.sample.dto.SampleOperationLockReleaseDTO;
import com.xykj.sample.dto.SampleRecordCreateDTO;
import com.xykj.sample.dto.SampleRecordDisposeDTO;
import com.xykj.sample.dto.SampleRecordHistoryCreateDTO;
import com.xykj.sample.dto.SampleRecordQueryDTO;
import com.xykj.sample.dto.SampleRecordRegisterDTO;
import com.xykj.sample.dto.SampleRecordUpdateDTO;
import com.xykj.sample.service.SampleRecordService;
import com.xykj.sample.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 留样记录控制器
 * API路径: /api/v1/sample
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sample")
@RequiredArgsConstructor
public class SampleRecordController {

    private final SampleRecordService sampleRecordService;

    /**
     * 留样管理看板
     * GET /api/v1/sample/dashboard
     */
    @GetMapping("/dashboard")
    public R<SampleDashboardVO> getDashboard(SampleRecordQueryDTO query) {
        return R.ok(sampleRecordService.getDashboard(query));
    }

    /**
     * 留样记录列表（分页）
     * GET /api/v1/sample/records
     */
    @GetMapping("/records")
    public R<PageResult<SampleRecordVO>> getRecords(SampleRecordQueryDTO query) {
        return R.ok(sampleRecordService.getRecordPage(query));
    }

    /**
     * 手工新增可选烹饪任务
     * GET /api/v1/sample/manual-task-options
     */
    @GetMapping("/manual-task-options")
    public R<List<SampleAvailableCookTaskVO>> getManualTaskOptions() {
        return R.ok(sampleRecordService.getAvailableCookTasks());
    }

    /**
     * 管理员历史补录可选烹饪任务
     * GET /api/v1/sample/history-task-options
     */
    @GetMapping("/history-task-options")
    public R<List<SampleAvailableCookTaskVO>> getHistoryTaskOptions(SampleHistoryTaskQueryDTO query) {
        return R.ok(sampleRecordService.getHistoryAvailableCookTasks(query));
    }

    /**
     * 新增留样记录
     * POST /api/v1/sample/records
     */
    @PostMapping("/records")
    public R<SampleRecordDetailVO> createRecord(@RequestBody SampleRecordCreateDTO dto) {
        return R.ok(sampleRecordService.createRecord(dto));
    }

    /**
     * 历史补录留样记录
     * POST /api/v1/sample/records/history-supplement
     */
    @PostMapping("/records/history-supplement")
    public R<SampleRecordDetailVO> createHistoricalRecord(@RequestBody SampleRecordHistoryCreateDTO dto) {
        return R.ok(sampleRecordService.createHistoricalRecord(dto));
    }

    /**
     * 执行留样登记
     * POST /api/v1/sample/records/{id}/register
     */
    @PostMapping("/records/{id}/register")
    public R<SampleRecordDetailVO> registerRecord(@PathVariable Long id,
                                                  @RequestBody SampleRecordRegisterDTO dto) {
        return R.ok(sampleRecordService.registerRecord(id, dto));
    }

    /**
     * 留样记录详情
     * GET /api/v1/sample/records/{id}
     */
    @GetMapping("/records/{id}")
    public R<SampleRecordDetailVO> getRecordDetail(@PathVariable Long id) {
        return R.ok(sampleRecordService.getRecordDetail(id));
    }

    /**
     * 抢占留样操作锁
     * POST /api/v1/sample/records/{id}/operation-lock/acquire
     */
    @PostMapping("/records/{id}/operation-lock/acquire")
    public R<SampleOperationLockVO> acquireOperationLock(@PathVariable Long id,
                                                         @RequestBody SampleOperationLockAcquireDTO dto) {
        return R.ok(sampleRecordService.acquireOperationLock(id, dto));
    }

    /**
     * 续租留样操作锁
     * POST /api/v1/sample/records/{id}/operation-lock/refresh
     */
    @PostMapping("/records/{id}/operation-lock/refresh")
    public R<SampleOperationLockVO> refreshOperationLock(@PathVariable Long id,
                                                         @RequestBody SampleOperationLockRefreshDTO dto) {
        return R.ok(sampleRecordService.refreshOperationLock(id, dto));
    }

    /**
     * 释放留样操作锁
     * POST /api/v1/sample/records/{id}/operation-lock/release
     */
    @PostMapping("/records/{id}/operation-lock/release")
    public R<Void> releaseOperationLock(@PathVariable Long id,
                                        @RequestBody SampleOperationLockReleaseDTO dto) {
        sampleRecordService.releaseOperationLock(id, dto);
        return R.ok(null);
    }

    /**
     * 执行销样
     * POST /api/v1/sample/records/{id}/disposal
     */
    @PostMapping("/records/{id}/disposal")
    public R<SampleRecordDetailVO> disposeRecord(@PathVariable Long id,
                                                  @RequestBody SampleRecordDisposeDTO dto) {
        return R.ok(sampleRecordService.disposeRecord(id, dto));
    }

    /**
     * 销样手工补录
     * POST /api/v1/sample/records/{id}/disposal/manual-supplement
     */
    @PostMapping("/records/{id}/disposal/manual-supplement")
    public R<SampleRecordDetailVO> manualSupplementDisposal(@PathVariable Long id,
                                                            @RequestBody SampleManualDisposalSupplementDTO dto) {
        return R.ok(sampleRecordService.manualSupplementDisposal(id, dto));
    }

    /**
     * 销样提醒列表
     * GET /api/v1/sample/disposal-reminders
     */
    @GetMapping("/disposal-reminders")
    public R<PageResult<DisposalReminderVO>> getDisposalReminders(SampleRecordQueryDTO query) {
        return R.ok(sampleRecordService.getDisposalReminders(query));
    }

    /**
     * 销样详情
     * GET /api/v1/sample/records/{id}/disposal
     */
    @GetMapping("/records/{id}/disposal")
    public R<SampleRecordDetailVO> getDisposalDetail(@PathVariable Long id) {
        return R.ok(sampleRecordService.getDisposalDetail(id));
    }

    /**
     * AI智能评估
     * POST /api/v1/sample/records/{id}/ai-evaluate
     */
    @PostMapping("/records/{id}/ai-evaluate")
    public R<AiEvaluateVO> aiEvaluateRecord(@PathVariable Long id) {
        return R.ok(sampleRecordService.aiEvaluateRecord(id));
    }

    /**
     * 编辑留样记录
     * PUT /api/v1/sample/records/{id}
     */
    @PutMapping("/records/{id}")
    public R<SampleRecordDetailVO> updateRecord(@PathVariable Long id,
                                                 @RequestBody SampleRecordUpdateDTO dto) {
        return R.ok(sampleRecordService.updateRecord(id, dto));
    }

    /**
     * 作废留样记录
     * POST /api/v1/sample/records/{id}/void
     */
    @PostMapping("/records/{id}/void")
    public R<SampleRecordDetailVO> voidRecord(@PathVariable Long id,
                                               @RequestParam String reason) {
        return R.ok(sampleRecordService.voidRecord(id, reason));
    }

    /**
     * 禁止物理删除
     * DELETE /api/v1/sample/records/{id}
     */
    @DeleteMapping("/records/{id}")
    public R<Void> deleteRecord(@PathVariable Long id) {
        sampleRecordService.deleteRecord(id);
        return R.ok(null);
    }

    /**
     * 上传留样图片
     * POST /api/v1/sample/upload-image
     */
    @PostMapping("/upload-image")
    public R<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String imageUrl = sampleRecordService.uploadImage(file);
        Map<String, String> result = new HashMap<>();
        result.put("imageUrl", imageUrl);
        return R.ok(result);
    }

    /**
     * 归档留样记录
     * PUT /api/v1/sample/records/{id}/archive
     */
    @PutMapping("/records/{id}/archive")
    public R<SampleRecordDetailVO> archiveRecord(@PathVariable Long id) {
        return R.ok(sampleRecordService.archiveRecord(id));
    }

    /**
     * 导出留样记录
     * GET /api/v1/sample/records/export
     */
    @GetMapping("/records/export")
    public void exportRecords(SampleRecordQueryDTO query, HttpServletResponse response) {
        sampleRecordService.exportRecords(query, response);
    }

    /**
     * 获取操作日志
     * GET /api/v1/sample/records/{id}/logs
     */
    @GetMapping("/records/{id}/logs")
    public R<List<OperationLogVO>> getOperationLogs(@PathVariable Long id) {
        return R.ok(sampleRecordService.getOperationLogs(id));
    }

    /**
     * 批量作废
     * POST /api/v1/sample/records/batch-void
     */
    @PostMapping("/records/batch-void")
    public R<Void> batchVoidRecords(@RequestBody BatchVoidDTO dto) {
        sampleRecordService.batchVoidRecords(dto.getIds(), dto.getReason());
        return R.ok(null);
    }

    /**
     * 批量归档
     * PUT /api/v1/sample/records/batch-archive
     */
    @PutMapping("/records/batch-archive")
    public R<Void> batchArchiveRecords(@RequestBody BatchArchiveDTO dto) {
        sampleRecordService.batchArchiveRecords(dto.getIds());
        return R.ok(null);
    }

    /**
     * 监管锁定
     * POST /api/v1/sample/records/{id}/lock
     */
    @PostMapping("/records/{id}/lock")
    public R<SampleRecordDetailVO> lockRecord(@PathVariable Long id,
                                               @RequestParam String lockStatus) {
        return R.ok(sampleRecordService.lockRecord(id, lockStatus));
    }

    /**
     * 解除锁定
     * POST /api/v1/sample/records/{id}/unlock
     */
    @PostMapping("/records/{id}/unlock")
    public R<SampleRecordDetailVO> unlockRecord(@PathVariable Long id) {
        return R.ok(sampleRecordService.unlockRecord(id));
    }
}
