package com.xykj.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.context.UserContext;
import com.xykj.common.result.PageResult;
import com.xykj.sys.dto.NotificationQueryDTO;
import com.xykj.sys.entity.Notification;
import com.xykj.sys.mapper.NotificationMapper;
import com.xykj.sys.service.NotificationService;
import com.xykj.sys.vo.NotificationDetailVO;
import com.xykj.sys.vo.NotificationStatsVO;
import com.xykj.sys.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 消息通知服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // ========== 名称映射 ==========

    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "system_notice", "系统通知",
            "food_safety_alert", "食安告警",
            "approval_todo", "审批待办",
            "security_risk", "安全风控",
            "platform_announcement", "平台公告"
    );

    private static final Map<String, String> SUB_CATEGORY_NAMES = Map.of(
            "alert_dispatch", "告警派单",
            "complaint_dispatch", "投诉派单",
            "threshold_alert", "阈值告警",
            "material_alert", "物料告警",
            "inventory_alert", "库存告警",
            "equipment_alert", "设备告警"
    );

    private static final Map<String, String> RISK_LEVEL_NAMES = Map.of(
            "normal", "普通",
            "attention", "关注",
            "high", "高",
            "severe", "严重"
    );

    private static final Map<String, String> READ_STATUS_NAMES = Map.of(
            "unread", "未读",
            "read", "已读"
    );

    private static final Map<String, String> PROCESS_STATUS_NAMES = Map.of(
            "pending", "待处理",
            "processing", "处理中",
            "processed", "已处理",
            "closed", "已关闭",
            "invalidated", "已失效",
            "deleted", "已删除"
    );

    // ========== 列表 ==========

    @Override
    public PageResult<NotificationVO> list(NotificationQueryDTO query) {
        Long userId = UserContext.getUserId();
        Long tenantId = UserContext.getTenantId();

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getTenantId, tenantId)
                .ne(Notification::getProcessStatus, "deleted")
                .eq(Notification::getDeleted, 0);

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Notification::getTitle, query.getKeyword());
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(Notification::getCategory, query.getCategory());
        }
        if (StringUtils.hasText(query.getSubCategory())) {
            wrapper.eq(Notification::getSubCategory, query.getSubCategory());
        }
        if (StringUtils.hasText(query.getReadStatus())) {
            wrapper.eq(Notification::getReadStatus, query.getReadStatus());
        }
        if (StringUtils.hasText(query.getProcessStatus())) {
            wrapper.eq(Notification::getProcessStatus, query.getProcessStatus());
        }
        if (StringUtils.hasText(query.getRiskLevel())) {
            wrapper.eq(Notification::getRiskLevel, query.getRiskLevel());
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(Notification::getSendTime, query.getStartTime());
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(Notification::getSendTime, query.getEndTime());
        }

        // 排序：未读优先 → 时间降序 → id降序
        wrapper.last("ORDER BY CASE WHEN read_status = 'unread' THEN 0 ELSE 1 END, " +
                "send_time DESC, id DESC");

        Page<Notification> page = notificationMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        List<NotificationVO> voList = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(voList, Long.valueOf(query.getPageNum()), Long.valueOf(query.getPageSize()), page.getTotal());
    }

    // ========== 详情 ==========

    @Override
    public NotificationDetailVO getDetail(Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(UserContext.getUserId())) {
            throw new RuntimeException("消息不存在");
        }

        NotificationDetailVO vo = new NotificationDetailVO();
        vo.setId(n.getId());
        vo.setMessageId(n.getMessageId());
        vo.setCategory(n.getCategory());
        vo.setCategoryName(CATEGORY_NAMES.getOrDefault(n.getCategory(), n.getCategory()));
        vo.setSubCategory(n.getSubCategory());
        vo.setSubCategoryName(SUB_CATEGORY_NAMES.getOrDefault(n.getSubCategory(), n.getSubCategory()));
        vo.setTitle(n.getTitle());
        vo.setSummary(n.getSummary());
        vo.setBody(n.getBody());
        vo.setRiskLevel(n.getRiskLevel());
        vo.setRiskLevelName(RISK_LEVEL_NAMES.getOrDefault(n.getRiskLevel(), n.getRiskLevel()));
        vo.setReadStatus(n.getReadStatus());
        vo.setReadStatusName(READ_STATUS_NAMES.getOrDefault(n.getReadStatus(), n.getReadStatus()));
        vo.setProcessStatus(n.getProcessStatus());
        vo.setProcessStatusName(PROCESS_STATUS_NAMES.getOrDefault(n.getProcessStatus(), n.getProcessStatus()));
        vo.setSourceModule(n.getSourceModule());
        vo.setRelatedBusinessId(n.getRelatedBusinessId());
        vo.setRelatedBusinessType(n.getRelatedBusinessType());
        vo.setRelatedOrgId(n.getRelatedOrgId());
        vo.setRelatedWarehouseId(n.getRelatedWarehouseId());
        vo.setRelatedMaterialId(n.getRelatedMaterialId());
        vo.setSendTime(n.getSendTime());
        vo.setExpiryTime(n.getExpiryTime());
        vo.setAllowDelete(n.getAllowDelete() != null && n.getAllowDelete() == 1);

        // 解析 JSON 字段
        try {
            if (n.getSourceSnapshot() != null) {
                vo.setSourceSnapshot(objectMapper.readValue(n.getSourceSnapshot(), new TypeReference<>() {}));
            }
            if (n.getExecutableActions() != null) {
                vo.setExecutableActions(objectMapper.readValue(n.getExecutableActions(), new TypeReference<>() {}));
            }
        } catch (Exception e) {
            log.warn("解析通知JSON失败: id={}", n.getId(), e);
        }

        // 自动标记已读
        if ("unread".equals(n.getReadStatus())) {
            markAsRead(id);
            vo.setReadStatus("read");
            vo.setReadStatusName("已读");
        }

        return vo;
    }

    // ========== 统计 ==========

    @Override
    public NotificationStatsVO getStats(Long userId) {
        NotificationStatsVO vo = new NotificationStatsVO();

        // 未读总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_notification WHERE user_id = ? AND read_status = 'unread' AND process_status <> 'deleted' AND deleted = 0",
                Long.class, userId);
        vo.setTotalUnread(total != null ? total.intValue() : 0);

        // 按分类统计
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT category, COUNT(*) AS cnt FROM sys_notification WHERE user_id = ? AND read_status = 'unread' AND process_status <> 'deleted' AND deleted = 0 GROUP BY category",
                userId);
        for (Map<String, Object> row : rows) {
            categoryCounts.put(String.valueOf(row.get("category")), ((Number) row.get("cnt")).intValue());
        }
        vo.setCategoryCounts(categoryCounts);

        return vo;
    }

    @Override
    public int getUnreadCount(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_notification WHERE user_id = ? AND read_status = 'unread' AND process_status <> 'deleted' AND deleted = 0",
                Long.class, userId);
        return count != null ? count.intValue() : 0;
    }

    // ========== 标记已读/未读 ==========

    @Override
    public void markAsRead(Long id) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, UserContext.getUserId())
                .set(Notification::getReadStatus, "read"));
    }

    @Override
    public void markAsUnread(Long id) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, UserContext.getUserId())
                .set(Notification::getReadStatus, "unread"));
    }

    @Override
    public void batchMarkAsRead(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        Long userId = UserContext.getUserId();
        for (Long id : ids) {
            notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                    .eq(Notification::getId, id)
                    .eq(Notification::getUserId, userId)
                    .eq(Notification::getReadStatus, "unread")
                    .set(Notification::getReadStatus, "read"));
        }
    }

    @Override
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        Long userId = UserContext.getUserId();
        for (Long id : ids) {
            notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                    .eq(Notification::getId, id)
                    .eq(Notification::getUserId, userId)
                    .set(Notification::getProcessStatus, "deleted")
                    .set(Notification::getDeleted, 1));
        }
    }

    // ========== 辅助方法 ==========

    private NotificationVO toVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setMessageId(n.getMessageId());
        vo.setCategory(n.getCategory());
        vo.setCategoryName(CATEGORY_NAMES.getOrDefault(n.getCategory(), n.getCategory()));
        vo.setSubCategory(n.getSubCategory());
        vo.setSubCategoryName(SUB_CATEGORY_NAMES.getOrDefault(n.getSubCategory(), n.getSubCategory()));
        vo.setTitle(n.getTitle());
        vo.setSummary(n.getSummary());
        vo.setRiskLevel(n.getRiskLevel());
        vo.setRiskLevelName(RISK_LEVEL_NAMES.getOrDefault(n.getRiskLevel(), n.getRiskLevel()));
        vo.setReadStatus(n.getReadStatus());
        vo.setReadStatusName(READ_STATUS_NAMES.getOrDefault(n.getReadStatus(), n.getReadStatus()));
        vo.setProcessStatus(n.getProcessStatus());
        vo.setProcessStatusName(PROCESS_STATUS_NAMES.getOrDefault(n.getProcessStatus(), n.getProcessStatus()));
        vo.setSourceModule(n.getSourceModule());
        vo.setRelatedBusinessId(n.getRelatedBusinessId());
        vo.setRelatedBusinessType(n.getRelatedBusinessType());
        vo.setSendTime(n.getSendTime());
        vo.setTimeDisplay(formatTimeDisplay(n.getSendTime()));
        vo.setExecutableActions(n.getExecutableActions());
        return vo;
    }

    /** 时间显示：<1h → "XX分钟前"，当天 → "HH:mm"，跨天 → "YYYY-MM-DD HH:mm" */
    private String formatTimeDisplay(LocalDateTime time) {
        if (time == null) return "";
        Duration d = Duration.between(time, LocalDateTime.now());
        long minutes = d.toMinutes();
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        if (time.toLocalDate().equals(LocalDateTime.now().toLocalDate())) {
            return String.format("%02d:%02d", time.getHour(), time.getMinute());
        }
        return time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
