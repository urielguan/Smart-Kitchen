package com.xykj.recipe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.recipe.dto.MaterialNotificationQueryDTO;
import com.xykj.recipe.entity.MaterialNotification;
import com.xykj.recipe.vo.MaterialNotificationVO;

import java.util.List;

/**
 * 物料预警通知服务
 */
public interface MaterialNotificationService {

    /**
     * 扫描临期物料并生成通知
     * @param days 提前多少天预警
     * @return 生成的通知数量
     */
    int scanExpiringMaterials(int days);

    /**
     * 获取通知列表
     */
    Page<MaterialNotificationVO> getNotifications(MaterialNotificationQueryDTO query);

    /**
     * 获取通知详情
     */
    MaterialNotificationVO getNotificationDetail(Long id);

    /**
     * 标记通知为已读
     */
    void markAsRead(Long id);

    /**
     * 批量标记为已读
     */
    void batchMarkAsRead(List<Long> ids);

    /**
     * 标记为已处理
     */
    void markAsHandled(Long id, Long handlerId, String remark);

    /**
     * 忽略通知
     */
    void dismiss(Long id);

    /**
     * 获取未读通知数量
     */
    int getUnreadCount(Long orgId);

    /**
     * 获取高优先级通知
     */
    List<MaterialNotificationVO> getHighPriorityNotifications(Long orgId, int limit);

    /**
     * 获取通知统计
     */
    NotificationStats getStats(Long orgId);

    /**
     * 通知统计
     */
    record NotificationStats(
        int total,
        int unread,
        int highPriority,
        int expiring,
        int expired
    ) {}
}
