package com.xykj.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.device.dto.ClipExtractDTO;
import com.xykj.device.dto.ClipQueryDTO;
import com.xykj.device.service.VideoClipService;
import com.xykj.device.vo.VideoClipVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 视频片段截取控制器
 * API路径: /api/v1/device/clips
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device/clips")
@RequiredArgsConstructor
public class VideoClipController {

    private final VideoClipService videoClipService;

    /**
     * 提取视频片段
     * POST /api/v1/device/clips/extract
     */
    @PostMapping("/extract")
    @AuditLog(module = AuditModule.DEVICE_CLIP, operationType = AuditOperationType.CREATE,
            desc = "'提取视频片段'", targetId = "#result.data.id")
    public R<VideoClipVO> extractClip(@Valid @RequestBody ClipExtractDTO dto) {
        VideoClipVO vo = videoClipService.extractClip(dto);
        return R.ok(vo);
    }

    /**
     * 获取片段列表
     * GET /api/v1/device/clips
     */
    @GetMapping
    public R<PageResult<VideoClipVO>> getClipList(ClipQueryDTO query) {
        Page<VideoClipVO> page = videoClipService.getClipList(query);
        return R.ok(PageResult.of(page));
    }

    /**
     * 获取片段详情（用于轮询状态）
     * GET /api/v1/device/clips/{id}
     */
    @GetMapping("/{id}")
    public R<VideoClipVO> getClipDetail(@PathVariable Long id) {
        VideoClipVO vo = videoClipService.getClipDetail(id);
        return R.ok(vo);
    }

    /**
     * 删除片段
     * DELETE /api/v1/device/clips/{id}
     */
    @DeleteMapping("/{id}")
    @AuditLog(module = AuditModule.DEVICE_CLIP, operationType = AuditOperationType.DELETE,
            desc = "'删除片段'", targetId = "#id")
    public R<Void> deleteClip(@PathVariable Long id) {
        videoClipService.deleteClip(id);
        return R.ok();
    }
}
