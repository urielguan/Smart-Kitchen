package com.xykj.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xykj.device.dto.DataLogQueryDTO;
import com.xykj.device.dto.DeviceOnlineStatusUpdateDTO;
import com.xykj.device.dto.DataLogReceiveDTO;
import com.xykj.device.entity.DeviceDataLog;
import com.xykj.device.entity.DeviceInfo;
import com.xykj.device.mapper.DeviceDataLogMapper;
import com.xykj.device.service.DeviceDataLogService;
import com.xykj.device.service.DeviceInfoService;
import com.xykj.device.service.ThresholdAlertEngine;
import com.xykj.device.vo.DataLogVO;
import com.xykj.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 设备数据采集日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceDataLogServiceImpl extends ServiceImpl<DeviceDataLogMapper, DeviceDataLog> implements DeviceDataLogService {

    private final DeviceInfoService deviceInfoService;
    private final ThresholdAlertEngine thresholdAlertEngine;

    /** 数据类型名称映射 */
    private static final Map<String, String> DATA_TYPE_MAP = new HashMap<>();

    static {
        DATA_TYPE_MAP.put("temperature", "温度");
        DATA_TYPE_MAP.put("humidity", "湿度");
        DATA_TYPE_MAP.put("weight", "重量");
        DATA_TYPE_MAP.put("heartbeat", "心跳");
    }

    @Override
    public Page<DataLogVO> list(DataLogQueryDTO query) {
        if (query.getStartTime() != null
                && query.getEndTime() != null
                && query.getStartTime().isAfter(query.getEndTime())) {
            throw BizException.badRequest("开始时间不能晚于结束时间");
        }

        Page<DeviceDataLog> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<DeviceDataLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getDeviceId() != null, DeviceDataLog::getDeviceId, query.getDeviceId());
        wrapper.eq(StringUtils.hasText(query.getDataType()), DeviceDataLog::getDataType, query.getDataType());
        wrapper.ge(query.getStartTime() != null, DeviceDataLog::getCollectedAt, query.getStartTime());
        wrapper.le(query.getEndTime() != null, DeviceDataLog::getCollectedAt, query.getEndTime());
        wrapper.orderByDesc(DeviceDataLog::getCollectedAt);

        Page<DeviceDataLog> resultPage = baseMapper.selectPage(page, wrapper);

        Page<DataLogVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public Long receive(DataLogReceiveDTO dto) {
        // 校验设备是否存在且处于可用状态
        DeviceInfo device = deviceInfoService.getById(dto.getDeviceId());
        if (device == null) {
            throw BizException.notFound("设备不存在");
        }
        if (!"active".equals(device.getStatus())) {
            throw BizException.badRequest("设备已停用，不接收数据");
        }
        if ("fault".equals(device.getOnlineStatus())) {
            throw BizException.badRequest("设备已故障，不接收数据");
        }

        DeviceDataLog dataLog = new DeviceDataLog();
        dataLog.setDeviceId(dto.getDeviceId());
        dataLog.setDeviceCode(device.getDeviceCode());
        dataLog.setDataType(dto.getDataType());
        dataLog.setDataValue(dto.getDataValue());
        dataLog.setDataUnit(dto.getDataUnit());
        dataLog.setDataJson(dto.getDataJson());
        dataLog.setCollectedAt(dto.getCollectedAt() != null ? dto.getCollectedAt() : LocalDateTime.now());
        dataLog.setCreatedAt(LocalDateTime.now());

        save(dataLog);

        // 所有有效数据均触发在线状态更新（设备在线则刷新心跳，设备离线则恢复在线）
        DeviceOnlineStatusUpdateDTO statusDto = new DeviceOnlineStatusUpdateDTO();
        statusDto.setOnlineStatus("online");
        statusDto.setSourceType("system");
        statusDto.setReason("数据上报");
        deviceInfoService.updateOnlineStatus(dto.getDeviceId(), statusDto);

        // 温湿度数据触发阈值告警检查
        if (("temperature".equals(dto.getDataType()) || "humidity".equals(dto.getDataType()))
                && dto.getDataValue() != null) {
            try {
                thresholdAlertEngine.check(dto.getDeviceId(), dto.getDataType(), dto.getDataValue());
            } catch (Exception e) {
                log.warn("阈值检查异常: deviceId={}, error={}", dto.getDeviceId(), e.getMessage());
            }
        }

        log.info("接收设备数据: deviceId={}, dataType={}, value={}", dto.getDeviceId(), dto.getDataType(), dto.getDataValue());
        return dataLog.getId();
    }

    private DataLogVO convertToVO(DeviceDataLog dataLog) {
        DataLogVO vo = new DataLogVO();
        BeanUtils.copyProperties(dataLog, vo);
        vo.setDataTypeName(DATA_TYPE_MAP.getOrDefault(dataLog.getDataType(), dataLog.getDataType()));
        return vo;
    }
}
