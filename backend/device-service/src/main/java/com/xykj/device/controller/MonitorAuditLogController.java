package com.xykj.device.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.device.dto.MonitorAuditLogDTO;
import com.xykj.device.dto.MonitorAuditLogQueryDTO;
import com.xykj.device.service.MonitorAuditLogService;
import com.xykj.device.vo.MonitorAuditLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 监控审计日志控制器
 * API路径: /api/v1/device/monitor-audit-logs
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device/monitor-audit-logs")
@RequiredArgsConstructor
public class MonitorAuditLogController {

    private final MonitorAuditLogService monitorAuditLogService;

    /**
     * 前端记录操作（画面切换、布局变更等）
     * POST /api/v1/device/monitor-audit-logs
     */
    @PostMapping
    public R<Void> logFrontendAction(@Valid @RequestBody MonitorAuditLogDTO dto) {
        monitorAuditLogService.logFrontendAction(dto);
        return R.ok();
    }

    /**
     * 查询监控审计日志（分页+筛选）
     * GET /api/v1/device/monitor-audit-logs
     */
    @GetMapping
    public R<PageResult<MonitorAuditLogVO>> getAuditLogList(MonitorAuditLogQueryDTO query) {
        Long total = monitorAuditLogService.getAuditLogCount(query);
        List<MonitorAuditLogVO> list = monitorAuditLogService.getAuditLogList(query);
        return R.ok(PageResult.of(list, (long) query.getPageNum(), (long) query.getPageSize(), total));
    }
}
