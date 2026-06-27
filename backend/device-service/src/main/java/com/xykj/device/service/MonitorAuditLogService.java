package com.xykj.device.service;

import com.xykj.device.dto.MonitorAuditLogDTO;
import com.xykj.device.dto.MonitorAuditLogQueryDTO;
import com.xykj.device.vo.MonitorAuditLogVO;

import java.util.List;

/**
 * 监控审计日志服务
 */
public interface MonitorAuditLogService {

    /**
     * 记录前端操作（画面切换、布局变更等）
     */
    void logFrontendAction(MonitorAuditLogDTO dto);

    /**
     * 查询监控审计日志
     */
    List<MonitorAuditLogVO> getAuditLogList(MonitorAuditLogQueryDTO query);

    /**
     * 查询审计日志总数
     */
    Long getAuditLogCount(MonitorAuditLogQueryDTO query);
}
