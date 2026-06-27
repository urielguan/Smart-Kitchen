package com.xykj.device.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.exception.BizException;
import com.xykj.device.config.VisionStreamConfig;
import com.xykj.device.dto.MonitorQueryDTO;
import com.xykj.device.entity.DeviceInfo;
import com.xykj.device.mapper.DeviceInfoMapper;
import com.xykj.device.service.MonitorService;
import com.xykj.device.vo.MonitorCameraVO;
import com.xykj.device.vo.MonitorStatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 视频监控服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    private final DeviceInfoMapper deviceInfoMapper;
    private final ObjectMapper objectMapper;
    private final VisionStreamConfig visionStreamConfig;

    @Override
    @DataScope
    public Page<MonitorCameraVO> getRealtimeMonitors(MonitorQueryDTO query) {
        Page<DeviceInfo> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        // 只查询摄像头类型
        wrapper.eq(DeviceInfo::getDeviceType, "camera");
        // 在线状态筛选
        wrapper.eq(StrUtil.isNotBlank(query.getOnlineStatus()),
                   DeviceInfo::getOnlineStatus, query.getOnlineStatus());
        // 位置模糊搜索
        wrapper.like(StrUtil.isNotBlank(query.getLocation()),
                     DeviceInfo::getLocationDesc, query.getLocation());
        // 组织筛选
        wrapper.eq(query.getOrgId() != null, DeviceInfo::getOrgId, query.getOrgId());
        wrapper.in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), DeviceInfo::getOrgId, query.getOrgIds());
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(DeviceInfo::getId);
        }
        // 排序
        wrapper.orderByDesc(DeviceInfo::getUpdatedAt);

        Page<DeviceInfo> result = deviceInfoMapper.selectPage(page, wrapper);

        List<MonitorCameraVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        Page<MonitorCameraVO> resultPage = new Page<>(query.getPageNum(), query.getPageSize());
        resultPage.setRecords(voList);
        resultPage.setTotal(result.getTotal());
        return resultPage;
    }

    @Override
    @DataScope
    public MonitorStatisticsVO getMonitorStatistics(MonitorQueryDTO query) {
        MonitorStatisticsVO vo = new MonitorStatisticsVO();

        LambdaQueryWrapper<DeviceInfo> baseWrapper = new LambdaQueryWrapper<DeviceInfo>()
                .eq(DeviceInfo::getDeviceType, "camera")
                .eq(query.getOrgId() != null, DeviceInfo::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), DeviceInfo::getOrgId, query.getOrgIds());
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            baseWrapper.isNull(DeviceInfo::getId);
        }

        Long total = deviceInfoMapper.selectCount(baseWrapper);
        vo.setTotalCameras(total.intValue());

        Long online = deviceInfoMapper.selectCount(new LambdaQueryWrapper<DeviceInfo>()
                .eq(DeviceInfo::getDeviceType, "camera")
                .eq(DeviceInfo::getStatus, "active")
                .eq(DeviceInfo::getOnlineStatus, "online")
                .eq(query.getOrgId() != null, DeviceInfo::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), DeviceInfo::getOrgId, query.getOrgIds())
                .isNull(query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty(), DeviceInfo::getId));
        vo.setOnlineCameras(online.intValue());

        Long offline = deviceInfoMapper.selectCount(new LambdaQueryWrapper<DeviceInfo>()
                .eq(DeviceInfo::getDeviceType, "camera")
                .eq(DeviceInfo::getStatus, "active")
                .eq(DeviceInfo::getOnlineStatus, "offline")
                .eq(query.getOrgId() != null, DeviceInfo::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), DeviceInfo::getOrgId, query.getOrgIds())
                .isNull(query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty(), DeviceInfo::getId));
        vo.setOfflineCameras(offline.intValue());

        Long alert = deviceInfoMapper.selectCount(new LambdaQueryWrapper<DeviceInfo>()
                .eq(DeviceInfo::getDeviceType, "camera")
                .eq(DeviceInfo::getStatus, "active")
                .eq(DeviceInfo::getOnlineStatus, "fault")
                .eq(query.getOrgId() != null, DeviceInfo::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), DeviceInfo::getOrgId, query.getOrgIds())
                .isNull(query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty(), DeviceInfo::getId));
        vo.setAlertCameras(alert.intValue());

        return vo;
    }

    @Override
    public MonitorCameraVO getCameraDetail(Long id) {
        DeviceInfo device = deviceInfoMapper.selectById(id);
        if (device == null) {
            throw BizException.notFound("摄像头不存在");
        }
        if (!"camera".equals(device.getDeviceType())) {
            throw BizException.badRequest("该设备不是摄像头类型");
        }
        return convertToVO(device);
    }

    @Override
    public boolean ptzControl(Long deviceId, String direction, Integer speed) {
        DeviceInfo device = deviceInfoMapper.selectById(deviceId);
        if (device == null) {
            throw BizException.notFound("摄像头不存在");
        }

        // 检查是否支持云台控制
        Map<String, Object> config = parseJsonToMap(device.getConfigParams());
        Boolean ptzSupport = config != null ? (Boolean) config.get("ptzSupport") : false;
        if (!Boolean.TRUE.equals(ptzSupport)) {
            throw BizException.badRequest("该摄像头不支持云台控制");
        }

        // 检查摄像头是否在线
        if (!"online".equals(device.getOnlineStatus())) {
            throw BizException.badRequest("摄像头离线，无法执行云台控制");
        }
        if (!"active".equals(device.getStatus())) {
            throw BizException.badRequest("当前设备未处于启用状态，无法执行云台控制");
        }

        // TODO: 调用实际的云台控制接口（需要对接流媒体服务器或摄像头SDK）
        log.info("云台控制: deviceId={}, direction={}, speed={}", deviceId, direction, speed);

        // 模拟成功
        return true;
    }

    // ========== 转换方法 ==========

    private MonitorCameraVO convertToVO(DeviceInfo device) {
        MonitorCameraVO vo = new MonitorCameraVO();
        vo.setId(device.getId());
        vo.setDeviceCode(device.getDeviceCode());
        vo.setDeviceName(device.getDeviceName());
        vo.setLocation(device.getLocationDesc());
        vo.setOnlineStatus(device.getOnlineStatus());
        vo.setStatus(device.getStatus());
        vo.setLastUpdatedAt(device.getUpdatedAt() != null ?
                device.getUpdatedAt().toString() : null);

        // 从configParams中提取摄像头特有字段
        Map<String, Object> config = parseJsonToMap(device.getConfigParams());
        if (config != null) {
            vo.setResolution((String) config.get("resolution"));
            vo.setFrameRate(toInt(config.get("frameRate"), 25));
            vo.setStreamUrl((String) config.get("streamUrl"));
            vo.setAnalysisStreamUrl(resolveAnalysisStreamUrl(device.getId(), config));
            vo.setAnalysisStreamType(resolveAnalysisStreamType(config));
            vo.setPreferAnalysisStream(resolvePreferAnalysisStream(config));
            vo.setThumbnailUrl((String) config.get("thumbnailUrl"));
            vo.setPtzSupport(Boolean.TRUE.equals(config.get("ptzSupport")));
            vo.setAlertCount(toInt(config.get("alertCount"), 0));
        } else {
            // 默认值
            vo.setResolution("1920x1080");
            vo.setFrameRate(25);
            vo.setAnalysisStreamUrl(resolveDefaultAnalysisStreamUrl(device.getId()));
            vo.setAnalysisStreamType(visionStreamConfig.getStreamType());
            vo.setPreferAnalysisStream(visionStreamConfig.isPreferAnalysisStream());
            vo.setPtzSupport(false);
            vo.setAlertCount(0);
        }

        return vo;
    }

    private String resolveAnalysisStreamUrl(Long deviceId, Map<String, Object> config) {
        Object configured = config.get("analysisStreamUrl");
        if (configured instanceof String value && StrUtil.isNotBlank(value)) {
            return value;
        }
        return resolveDefaultAnalysisStreamUrl(deviceId);
    }

    private String resolveDefaultAnalysisStreamUrl(Long deviceId) {
        if (!visionStreamConfig.isEnabled() || StrUtil.isBlank(visionStreamConfig.getBaseUrl())) {
            return null;
        }
        String baseUrl = StrUtil.removeSuffix(visionStreamConfig.getBaseUrl(), "/");
        String annotatedPath = visionStreamConfig.getAnnotatedPath();
        if (StrUtil.isBlank(annotatedPath)) {
            return null;
        }
        String separator = annotatedPath.contains("?") ? "&" : "?";
        return baseUrl + annotatedPath + separator + "deviceId=" + deviceId;
    }

    private String resolveAnalysisStreamType(Map<String, Object> config) {
        Object configured = config.get("analysisStreamType");
        if (configured instanceof String value && StrUtil.isNotBlank(value)) {
            return value;
        }
        return visionStreamConfig.getStreamType();
    }

    private Boolean resolvePreferAnalysisStream(Map<String, Object> config) {
        Object configured = config.get("preferAnalysisStream");
        if (configured instanceof Boolean value) {
            return value;
        }
        if (configured instanceof String value && StrUtil.isNotBlank(value)) {
            return Boolean.parseBoolean(value);
        }
        return visionStreamConfig.isPreferAnalysisStream();
    }

    private Map<String, Object> parseJsonToMap(String json) {
        if (StrUtil.isBlank(json)) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析JSON失败: {}", json, e);
            return null;
        }
    }

    private Integer toInt(Object val, Integer defaultVal) {
        if (val == null) return defaultVal;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
