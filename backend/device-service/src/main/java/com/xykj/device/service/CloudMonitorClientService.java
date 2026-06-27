package com.xykj.device.service;

import java.util.List;

/**
 * 云监控平台客户端服务
 * 负责与过程云监控平台 (www.fromnet.cn) 的 API 对接
 */
public interface CloudMonitorClientService {

    /**
     * 获取有效的访问令牌（自动刷新）
     */
    String getAccessToken();

    /**
     * 通过网关序列号查询网关ID
     *
     * @param serialNumber 网关序列号
     * @return 网关ID，查询失败返回 null
     */
    String findGatewayId(String serialNumber);

    /**
     * 获取网关最新指标数据
     *
     * @param gatewayId 网关ID
     * @return 指标列表
     */
    List<CloudMetric> fetchLatestMetrics(String gatewayId);
}
