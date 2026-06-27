package com.xykj.sys.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sys.dto.NotificationQueryDTO;
import com.xykj.sys.service.NotificationService;
import com.xykj.sys.vo.NotificationDetailVO;
import com.xykj.sys.vo.NotificationStatsVO;
import com.xykj.sys.vo.NotificationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息通知控制器
 * API路径: /api/v1/sys/notifications
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sys/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 消息列表（分页）
     * GET /api/v1/sys/notifications
     */
    @GetMapping
    public R<PageResult<NotificationVO>> list(@Valid NotificationQueryDTO query) {
        PageResult<NotificationVO> result = notificationService.list(query);
        return R.ok(result);
    }

    /**
     * 未读统计
     * GET /api/v1/sys/notifications/stats
     */
    @GetMapping("/stats")
    public R<NotificationStatsVO> getStats() {
        Long userId = com.xykj.common.context.UserContext.getUserId();
        NotificationStatsVO stats = notificationService.getStats(userId);
        return R.ok(stats);
    }

    /**
     * 未读数量
     * GET /api/v1/sys/notifications/unread-count
     */
    @GetMapping("/unread-count")
    public R<Map<String, Object>> getUnreadCount() {
        Long userId = com.xykj.common.context.UserContext.getUserId();
        int count = notificationService.getUnreadCount(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        return R.ok(result);
    }

    /**
     * 消息详情
     * GET /api/v1/sys/notifications/{id}
     */
    @GetMapping("/{id:\\d+}")
    public R<NotificationDetailVO> getDetail(@PathVariable Long id) {
        NotificationDetailVO detail = notificationService.getDetail(id);
        return R.ok(detail);
    }

    /**
     * 标记已读
     * PUT /api/v1/sys/notifications/{id}/read
     */
    @PutMapping("/{id:\\d+}/read")
    public R<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return R.ok();
    }

    /**
     * 标记未读
     * PUT /api/v1/sys/notifications/{id}/unread
     */
    @PutMapping("/{id:\\d+}/unread")
    public R<Void> markAsUnread(@PathVariable Long id) {
        notificationService.markAsUnread(id);
        return R.ok();
    }

    /**
     * 批量标记已读
     * PUT /api/v1/sys/notifications/batch-read
     */
    @PutMapping("/batch-read")
    public R<Void> batchMarkAsRead(@RequestBody List<Long> ids) {
        notificationService.batchMarkAsRead(ids);
        return R.ok();
    }

    /**
     * 批量删除
     * DELETE /api/v1/sys/notifications/batch
     */
    @DeleteMapping("/batch")
    public R<Void> batchDelete(@RequestBody List<Long> ids) {
        notificationService.batchDelete(ids);
        return R.ok();
    }
}
