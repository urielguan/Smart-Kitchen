package com.xykj.recipe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.result.R;
import com.xykj.recipe.dto.MaterialNotificationQueryDTO;
import com.xykj.recipe.service.MaterialNotificationService;
import com.xykj.recipe.vo.MaterialNotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 物料预警通知控制器
 */
@Tag(name = "物料预警通知", description = "临期物料预警和推荐菜谱接口")
@RestController
@RequestMapping("/api/v1/recipe/notifications")
@RequiredArgsConstructor
public class MaterialNotificationController {

    private final MaterialNotificationService notificationService;

    /**
     * 获取通知列表
     */
    @Operation(summary = "获取通知列表", description = "分页获取物料预警通知列表")
    @GetMapping
    public R<Page<MaterialNotificationVO>> getNotifications(MaterialNotificationQueryDTO query) {
        return R.ok(notificationService.getNotifications(query));
    }

    /**
     * 获取通知详情
     */
    @Operation(summary = "获取通知详情", description = "根据ID获取通知详情")
    @GetMapping("/{id}")
    public R<MaterialNotificationVO> getNotificationDetail(
            @Parameter(description = "通知ID") @PathVariable Long id) {
        MaterialNotificationVO detail = notificationService.getNotificationDetail(id);
        if (detail == null) {
            return R.fail("通知不存在");
        }
        return R.ok(detail);
    }

    /**
     * 标记为已读
     */
    @Operation(summary = "标记为已读", description = "将通知标记为已读状态")
    @PutMapping("/{id}/read")
    public R<Void> markAsRead(
            @Parameter(description = "通知ID") @PathVariable Long id) {
        notificationService.markAsRead(id);
        return R.ok();
    }

    /**
     * 批量标记为已读
     */
    @Operation(summary = "批量标记已读", description = "批量将通知标记为已读")
    @PutMapping("/batch-read")
    public R<Void> batchMarkAsRead(@RequestBody List<Long> ids) {
        notificationService.batchMarkAsRead(ids);
        return R.ok();
    }

    /**
     * 标记为已处理
     */
    @Operation(summary = "标记为已处理", description = "将通知标记为已处理状态")
    @PutMapping("/{id}/handle")
    public R<Void> markAsHandled(
            @Parameter(description = "通知ID") @PathVariable Long id,
            @Parameter(description = "处理备注") @RequestParam(required = false) String remark,
            @Parameter(description = "处理人ID") @RequestParam(required = false) Long handlerId) {
        notificationService.markAsHandled(id, handlerId, remark);
        return R.ok();
    }

    /**
     * 忽略通知
     */
    @Operation(summary = "忽略通知", description = "忽略该通知")
    @PutMapping("/{id}/dismiss")
    public R<Void> dismiss(
            @Parameter(description = "通知ID") @PathVariable Long id) {
        notificationService.dismiss(id);
        return R.ok();
    }

    /**
     * 获取未读数量
     */
    @Operation(summary = "获取未读数量", description = "获取当前未读通知数量")
    @GetMapping("/unread-count")
    public R<Map<String, Integer>> getUnreadCount(
            @Parameter(description = "组织ID") @RequestParam(required = false) Long orgId) {
        int count = notificationService.getUnreadCount(orgId);
        return R.ok(Map.of("count", count));
    }

    /**
     * 获取高优先级通知
     */
    @Operation(summary = "获取高优先级通知", description = "获取高优先级未读通知")
    @GetMapping("/high-priority")
    public R<List<MaterialNotificationVO>> getHighPriorityNotifications(
            @Parameter(description = "组织ID") @RequestParam(required = false) Long orgId,
            @Parameter(description = "数量限制") @RequestParam(defaultValue = "5") int limit) {
        return R.ok(notificationService.getHighPriorityNotifications(orgId, limit));
    }

    /**
     * 获取通知统计
     */
    @Operation(summary = "获取通知统计", description = "获取通知统计数据")
    @GetMapping("/stats")
    public R<MaterialNotificationService.NotificationStats> getStats(
            @Parameter(description = "组织ID") @RequestParam(required = false) Long orgId) {
        return R.ok(notificationService.getStats(orgId));
    }

    /**
     * 手动触发扫描临期物料
     */
    @Operation(summary = "手动扫描临期物料", description = "手动触发临期物料扫描")
    @PostMapping("/scan")
    public R<Map<String, Integer>> scanExpiringMaterials(
            @Parameter(description = "提前预警天数") @RequestParam(defaultValue = "7") int days) {
        int count = notificationService.scanExpiringMaterials(days);
        return R.ok(Map.of("generatedCount", count));
    }
}
