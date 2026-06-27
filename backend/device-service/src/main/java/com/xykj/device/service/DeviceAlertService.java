package com.xykj.device.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.dto.AlertCloseDTO;
import com.xykj.device.dto.AlertDispatchDTO;
import com.xykj.device.dto.AlertDispatchQueryDTO;
import com.xykj.device.dto.AlertProcessDTO;
import com.xykj.device.dto.AlertQueryDTO;
import com.xykj.device.dto.AlertReviewDTO;
import com.xykj.device.vo.AlertDashboardVO;
import com.xykj.device.vo.AlertDetailVO;
import com.xykj.device.vo.AlertDispatchDetailVO;
import com.xykj.device.vo.AlertDispatchVO;
import com.xykj.device.vo.AlertVO;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 告警服务接口
 */
public interface DeviceAlertService {

    AlertDashboardVO getDashboard(AlertQueryDTO query);

    Page<AlertVO> list(AlertQueryDTO query);

    AlertDetailVO getDetail(Long id);

    /** 告警派单（自动/人工） */
    Map<String, Object> dispatch(Long id, AlertDispatchDTO dto);

    /** 派单工单列表 */
    Page<AlertDispatchVO> listDispatch(AlertDispatchQueryDTO query);

    /** 派单工单详情 */
    AlertDispatchDetailVO getDispatchDetail(Long dispatchId);

    /** 通过告警ID获取派单详情（用于告警列表的详情查看） */
    AlertDispatchDetailVO getDispatchDetailByAlertId(Long alertId);

    /** 处理工单 */
    void processDispatch(Long dispatchId, AlertProcessDTO dto);

    /** 复核工单 */
    void reviewDispatch(Long dispatchId, AlertReviewDTO dto);

    /** 关闭告警 */
    void close(Long id, AlertCloseDTO dto);

    /** 获取告警派单可选处理人（根据关联规则的派单范围过滤） */
    List<Map<String, Object>> getDispatchHandlers(Long alertId);

    /** 导出告警列表 */
    void exportAlerts(AlertQueryDTO query, HttpServletResponse response);

    /** 尝试自动派单（规则开启了自动派单时调用，失败保持待处理状态） */
    void tryAutoDispatch(Long alertId);

    Map<String, Object> testDb();
}
