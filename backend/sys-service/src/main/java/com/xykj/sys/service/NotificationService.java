package com.xykj.sys.service;

import com.xykj.common.result.PageResult;
import com.xykj.sys.dto.NotificationQueryDTO;
import com.xykj.sys.vo.NotificationDetailVO;
import com.xykj.sys.vo.NotificationStatsVO;
import com.xykj.sys.vo.NotificationVO;

import java.util.List;

/**
 * 消息通知服务接口
 */
public interface NotificationService {

    PageResult<NotificationVO> list(NotificationQueryDTO query);

    NotificationDetailVO getDetail(Long id);

    NotificationStatsVO getStats(Long userId);

    int getUnreadCount(Long userId);

    void markAsRead(Long id);

    void markAsUnread(Long id);

    void batchMarkAsRead(List<Long> ids);

    void batchDelete(List<Long> ids);
}
