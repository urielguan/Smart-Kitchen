package com.xykj.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.device.dto.ViolationHandleDTO;
import com.xykj.device.dto.ViolationQueryDTO;
import com.xykj.device.service.ViolationService;
import com.xykj.device.vo.ViolationOperationLogVO;
import com.xykj.device.vo.ViolationStatisticsVO;
import com.xykj.device.vo.ViolationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;
import com.xykj.device.event.ViolationEvent;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * AI违规识别控制器
 * API路径: /api/v1/device/violations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device/violations")
@RequiredArgsConstructor
public class ViolationController {

    private final ViolationService violationService;
    private final ApplicationEventPublisher eventPublisher;

    private static final Map<String, String> VIOLATION_TYPES = Map.of(
            "no_mask", "未佩戴口罩", "no_hat", "未佩戴厨师帽",
            "smoking", "吸烟行为", "phone", "使用手机",
            "outsider", "陌生人闯入", "fighting", "打架斗殴",
            "falling", "人员跌倒", "gathering", "异常聚集"
    );
    private static final Map<String, String> ALERT_LEVELS = Map.of(
            "info", "提示", "warning", "警告", "urgent", "紧急", "danger", "危险"
    );
    private static final String[] LOCATIONS = {"后厨加工区", "烹饪区", "食材存储区", "餐具清洗区", "前厅入口"};
    private static final String[] STATUSES = {"pending", "assigned", "processing", "resolved", "reviewed"};
    private final Random random = new Random();

    /**
     * 获取违规事件列表
     * GET /api/v1/device/violations
     */
    @GetMapping
    public R<PageResult<ViolationVO>> getViolationList(ViolationQueryDTO query) {
        Page<ViolationVO> page = violationService.getViolationList(query);
        return R.ok(PageResult.of(page));
    }

    /**
     * 获取违规事件详情
     * GET /api/v1/device/violations/{id}
     */
    @GetMapping("/{id}")
    public R<ViolationVO> getViolationDetail(@PathVariable Long id) {
        ViolationVO vo = violationService.getViolationDetail(id);
        return R.ok(vo);
    }

    /**
     * 处理违规事件
     * POST /api/v1/device/violations/{id}/handle
     */
    @PostMapping("/{id}/handle")
    @AuditLog(module = AuditModule.DEVICE_VIOLATION, operationType = AuditOperationType.STATUS_CHANGE,
            desc = "'处理违规事件'", targetId = "#id")
    public R<Void> handleViolation(
            @PathVariable Long id,
            @Valid @RequestBody ViolationHandleDTO dto) {
        boolean success = violationService.handleViolation(id, dto);
        return success ? R.ok() : R.fail("INTERNAL_ERROR", "处理失败");
    }

    /**
     * 批量处理违规事件
     * POST /api/v1/device/violations/batch-handle
     */
    @PostMapping("/batch-handle")
    @AuditLog(module = AuditModule.DEVICE_VIOLATION, operationType = AuditOperationType.STATUS_CHANGE,
            desc = "'批量处理违规事件'")
    public R<Void> batchHandleViolations(
            @RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) params.get("ids");
        ViolationHandleDTO dto = new ViolationHandleDTO();
        dto.setStatus((String) params.get("status"));
        dto.setHandleRemark((String) params.get("handleRemark"));

        boolean success = violationService.batchHandleViolations(ids, dto);
        return success ? R.ok() : R.fail("INTERNAL_ERROR", "批量处理失败");
    }

    /**
     * 获取违规统计数据
     * GET /api/v1/device/violations/statistics
     */
    @GetMapping("/statistics")
    public R<ViolationStatisticsVO> getStatistics(@RequestParam(required = false) Long orgId) {
        ViolationStatisticsVO vo = violationService.getViolationStatistics(orgId);
        return R.ok(vo);
    }

    /**
     * 获取违规事件操作日志
     * GET /api/v1/device/violations/{id}/logs
     */
    @GetMapping("/{id}/logs")
    public R<List<ViolationOperationLogVO>> getOperationLogs(@PathVariable Long id) {
        List<ViolationOperationLogVO> logs = violationService.getOperationLogs(id);
        return R.ok(logs);
    }

    /**
     * 测试用：手动触发一条模拟违规事件（SSE推送）
     * POST /api/v1/device/violations/test-trigger
     */
    @PostMapping("/test-trigger")
    public R<ViolationVO> testTrigger() {
        ViolationVO vo = new ViolationVO();
        vo.setId(System.currentTimeMillis());
        String[] types = VIOLATION_TYPES.keySet().toArray(new String[0]);
        String type = types[random.nextInt(types.length)];
        vo.setViolationType(type);
        vo.setViolationTypeName(VIOLATION_TYPES.get(type));
        vo.setInvolvedCount(1 + random.nextInt(3));
        String[] levels = {"warning", "urgent", "danger"};
        String level = levels[random.nextInt(levels.length)];
        vo.setAlertLevel(level);
        vo.setAlertLevelName(ALERT_LEVELS.get(level));
        vo.setLocation(LOCATIONS[random.nextInt(LOCATIONS.length)]);
        vo.setDeviceId((long) (random.nextInt(5) + 1));
        vo.setDeviceName("摄像头-" + vo.getDeviceId());
        vo.setOccurredAt(java.time.LocalDateTime.now());
        vo.setDuration(10 + random.nextInt(120));
        vo.setConfidence(75 + random.nextInt(25));
        vo.setStatus("pending");
        vo.setStatusName("待处理");
        vo.setScreenshotUrl("/static/violations/screenshot_test.jpg");

        eventPublisher.publishEvent(new ViolationEvent(this, vo));
        log.info("测试触发违规事件: id={}, type={}", vo.getId(), vo.getViolationType());
        return R.ok(vo);
    }
}
