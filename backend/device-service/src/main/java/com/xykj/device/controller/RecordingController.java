package com.xykj.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.device.dto.RecordingQueryDTO;
import com.xykj.device.service.RecordingService;
import com.xykj.device.vo.RecordingStatisticsVO;
import com.xykj.device.vo.RecordingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 视频录像控制器
 * API路径: /api/v1/device/recordings
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device/recordings")
@RequiredArgsConstructor
public class RecordingController {

    private final RecordingService recordingService;

    /**
     * 获取录像统计（所有符合筛选条件的数据汇总）
     * GET /api/v1/device/recordings/statistics
     */
    @GetMapping("/statistics")
    public R<RecordingStatisticsVO> getRecordingStatistics(RecordingQueryDTO query) {
        RecordingStatisticsVO statistics = recordingService.getRecordingStatistics(query);
        return R.ok(statistics);
    }

    /**
     * 获取录像列表
     * GET /api/v1/device/recordings
     */
    @GetMapping
    public R<PageResult<RecordingVO>> getRecordingList(RecordingQueryDTO query) {
        Page<RecordingVO> page = recordingService.getRecordingList(query);
        return R.ok(PageResult.of(page));
    }

    /**
     * 获取录像详情
     * GET /api/v1/device/recordings/{id}
     */
    @GetMapping("/{id}")
    public R<RecordingVO> getRecordingDetail(@PathVariable Long id) {
        RecordingVO vo = recordingService.getRecordingDetail(id);
        return R.ok(vo);
    }

    /**
     * 获取播放地址
     * GET /api/v1/device/recordings/{id}/playback
     */
    @GetMapping("/{id}/playback")
    @AuditLog(module = AuditModule.DEVICE_RECORDING, operationType = AuditOperationType.VIEW,
            desc = "'回放录像'", targetId = "#id")
    public R<Map<String, String>> getPlaybackUrl(@PathVariable Long id) {
        String url = recordingService.getPlaybackUrl(id);
        return R.ok(Map.of("playbackUrl", url));
    }

    /**
     * 删除录像
     * DELETE /api/v1/device/recordings/{id}
     */
    @DeleteMapping("/{id}")
    @AuditLog(module = AuditModule.DEVICE_RECORDING, operationType = AuditOperationType.DELETE,
            desc = "'删除录像'", targetId = "#id")
    public R<Void> deleteRecording(@PathVariable Long id) {
        recordingService.deleteRecording(id);
        return R.ok();
    }
}
