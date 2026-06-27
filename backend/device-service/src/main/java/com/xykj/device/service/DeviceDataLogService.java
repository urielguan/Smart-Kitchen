package com.xykj.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xykj.device.dto.DataLogQueryDTO;
import com.xykj.device.dto.DataLogReceiveDTO;
import com.xykj.device.entity.DeviceDataLog;
import com.xykj.device.vo.DataLogVO;

/**
 * 设备数据采集日志服务接口
 */
public interface DeviceDataLogService extends IService<DeviceDataLog> {

    /**
     * 分页查询设备数据采集日志
     */
    Page<DataLogVO> list(DataLogQueryDTO query);

    /**
     * 接收设备数据（心跳类型会触发设备在线状态更新）
     */
    Long receive(DataLogReceiveDTO dto);
}
