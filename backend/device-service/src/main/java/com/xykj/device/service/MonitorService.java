package com.xykj.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.dto.MonitorQueryDTO;
import com.xykj.device.vo.MonitorCameraVO;
import com.xykj.device.vo.MonitorStatisticsVO;

/**
 * 视频监控服务接口
 */
public interface MonitorService {

    /**
     * 获取实时监控列表（摄像头列表）
     * @param query 查询条件
     * @return 分页结果
     */
    Page<MonitorCameraVO> getRealtimeMonitors(MonitorQueryDTO query);

    /**
     * 获取监控统计数据
     * @param orgId 组织ID
     * @return 统计数据
     */
    MonitorStatisticsVO getMonitorStatistics(MonitorQueryDTO query);

    /**
     * 获取摄像头详情
     * @param id 设备ID
     * @return 摄像头详情
     */
    MonitorCameraVO getCameraDetail(Long id);

    /**
     * 云台控制
     * @param deviceId 设备ID
     * @param direction 方向：up/down/left/right/zoom_in/zoom_out/stop
     * @param speed 速度（1-10）
     * @return 是否成功
     */
    boolean ptzControl(Long deviceId, String direction, Integer speed);
}
