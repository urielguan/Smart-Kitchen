package com.xykj.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.device.dto.MonitorQueryDTO;
import com.xykj.device.service.MonitorService;
import com.xykj.device.service.StreamTranscodeService;
import com.xykj.device.vo.MonitorCameraVO;
import com.xykj.device.vo.MonitorStatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 视频监控控制器
 * API路径: /api/v1/device/monitors
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device/monitors")
@RequiredArgsConstructor
@Validated
public class MonitorController {

    private final MonitorService monitorService;
    private final StreamTranscodeService streamTranscodeService;

    /**
     * 获取实时监控列表
     * GET /api/v1/device/monitors/realtime
     */
    @GetMapping("/realtime")
    public R<PageResult<MonitorCameraVO>> getRealtimeMonitors(MonitorQueryDTO query) {
        Page<MonitorCameraVO> page = monitorService.getRealtimeMonitors(query);
        return R.ok(PageResult.of(page));
    }

    /**
     * 获取监控统计数据
     * GET /api/v1/device/monitors/statistics
     */
    @GetMapping("/statistics")
    public R<MonitorStatisticsVO> getStatistics(MonitorQueryDTO query) {
        MonitorStatisticsVO vo = monitorService.getMonitorStatistics(query);
        return R.ok(vo);
    }

    /**
     * 获取摄像头详情
     * GET /api/v1/device/cameras/{id}
     */
    @GetMapping("/cameras/{id}")
    public R<MonitorCameraVO> getCameraDetail(@PathVariable Long id) {
        MonitorCameraVO vo = monitorService.getCameraDetail(id);
        return R.ok(vo);
    }

    /**
     * 云台控制
     * POST /api/v1/device/cameras/{id}/ptz
     */
    @PostMapping("/cameras/{id}/ptz")
    @AuditLog(module = AuditModule.DEVICE_MONITOR, operationType = AuditOperationType.CONTROL,
            desc = "'云台控制: 方向=' + #params.get('direction') + ', 速度=' + #params.get('speed')",
            targetId = "#id")
    public R<Void> ptzControl(
            @PathVariable Long id,
            @RequestBody Map<String, Object> params) {

        String direction = (String) params.get("direction");
        Integer speed = params.get("speed") != null ?
                Integer.parseInt(params.get("speed").toString()) : 5;

        // 参数校验
        if (!isValidDirection(direction)) {
            return R.fail("BAD_REQUEST", "无效的云台方向参数");
        }
        if (speed < 1 || speed > 10) {
            return R.fail("BAD_REQUEST", "速度参数必须在1-10之间");
        }

        boolean success = monitorService.ptzControl(id, direction, speed);
        if (success) {
            return R.ok();
        } else {
            return R.fail("INTERNAL_ERROR", "云台控制失败");
        }
    }

    /**
     * 重启指定摄像头的RTSP→HLS转码
     * POST /api/v1/device/monitors/cameras/{id}/restart-transcode
     */
    @PostMapping("/cameras/{id}/restart-transcode")
    public R<Map<String, Object>> restartTranscode(@PathVariable Long id) {
        String hlsUrl = streamTranscodeService.restartDeviceTranscode(id);
        if (hlsUrl == null) {
            return R.fail("BAD_REQUEST", "转码启动失败，请检查设备配置中的rtspUrl");
        }
        return R.ok(Map.of("deviceId", id, "hlsUrl", hlsUrl));
    }

    /**
     * 重启所有在线摄像头的转码
     * POST /api/v1/device/monitors/restart-all-transcodes
     */
    @PostMapping("/restart-all-transcodes")
    public R<Void> restartAllTranscodes() {
        streamTranscodeService.startAllCameraTranscodes();
        return R.ok();
    }

    /**
     * 校验云台方向参数
     */
    private boolean isValidDirection(String direction) {
        return direction != null && (
                direction.equals("up") ||
                direction.equals("down") ||
                direction.equals("left") ||
                direction.equals("right") ||
                direction.equals("zoom_in") ||
                direction.equals("zoom_out") ||
                direction.equals("stop")
        );
    }
}
