package com.xykj.device.service.impl;

import com.xykj.device.config.CloudMonitorConfig;
import com.xykj.device.service.CloudMetric;
import com.xykj.device.service.CloudMonitorClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 云监控平台客户端实现
 * 负责令牌管理、网关ID缓存和API调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudMonitorClientServiceImpl implements CloudMonitorClientService {

    private final RestTemplate cloudMonitorRestTemplate;
    private final CloudMonitorConfig config;

    private static final String TOKEN_URL = "https://www.fromnet.cn/iot/app/api/oauth/token";
    private static final String GATEWAY_ID_URL = "https://wulink.tech/iot/gateway/api/findGatewayId";
    private static final String METRICS_LAST_URL = "https://iot.wulink.tech/api/v1/gateways/{id}/metrics/last";

    /**
     * 判断云监控平台返回码是否为成功
     * Jackson 反序列化数字可能是 Integer 或 Long，统一转为 Number 比较
     */
    private boolean isSuccessCode(Object code) {
        if (code instanceof Number) {
            return ((Number) code).intValue() == 20000;
        }
        return false;
    }
    private static final long TOKEN_REFRESH_AHEAD_MS = 5 * 60 * 1000L; // 提前 5 分钟刷新

    /** 缓存的 access_token */
    private volatile String cachedToken;
    /** token 过期时间（毫秒时间戳） */
    private volatile long tokenExpiresAt;
    /** 网关序列号 → 网关ID 缓存 */
    private final Map<String, String> gatewayIdCache = new ConcurrentHashMap<>();

    @Override
    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && tokenExpiresAt > (now + TOKEN_REFRESH_AHEAD_MS)) {
            return cachedToken;
        }
        return refreshToken();
    }

    private String refreshToken() {
        log.info("刷新云监控平台令牌...");
        try {
            String url = TOKEN_URL + "?name=" + config.getName()
                    + "&pwd=" + config.getPassword()
                    + "&secretId=" + config.getSecretId();

            @SuppressWarnings("unchecked")
            Map<String, Object> body = cloudMonitorRestTemplate.getForObject(url, Map.class);

            if (body == null || !isSuccessCode(body.get("code"))) {
                String msg = body != null ? String.valueOf(body.get("message")) : "响应为空";
                log.error("云监控平台令牌获取失败: {}", msg);
                cachedToken = null;
                return null;
            }

            cachedToken = (String) body.get("access_token");
            Object expiresObj = body.get("expires");
            tokenExpiresAt = expiresObj != null ? ((Number) expiresObj).longValue() : 0;

            log.info("云监控平台令牌刷新成功, 过期时间戳: {}", tokenExpiresAt);
            return cachedToken;
        } catch (RestClientException e) {
            log.error("云监控平台令牌请求异常: {}", e.getMessage());
            cachedToken = null;
            return null;
        }
    }

    @Override
    public String findGatewayId(String serialNumber) {
        return gatewayIdCache.computeIfAbsent(serialNumber, sn -> {
            String token = getAccessToken();
            if (token == null) {
                log.warn("获取令牌失败，无法查询网关ID: serialNumber={}", sn);
                return null;
            }

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(token);
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                String url = GATEWAY_ID_URL + "?serialNumber=" + sn;
                @SuppressWarnings("unchecked")
                ResponseEntity<Map> response = cloudMonitorRestTemplate.exchange(
                        url, HttpMethod.GET, entity, Map.class);

                Map<String, Object> body = response.getBody();
                if (body != null && isSuccessCode(body.get("code"))) {
                    Object data = body.get("data");
                    String gatewayId = data != null ? String.valueOf(data) : null;
                    log.info("网关ID查询成功: serialNumber={}, gatewayId={}", sn, gatewayId);
                    return gatewayId;
                }

                String msg = body != null ? String.valueOf(body.get("message")) : "响应为空";
                log.warn("网关ID查询失败: serialNumber={}, message={}", sn, msg);
                return null;
            } catch (RestClientException e) {
                log.error("网关ID查询异常: serialNumber={}, error={}", sn, e.getMessage());
                return null;
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CloudMetric> fetchLatestMetrics(String gatewayId) {
        String token = getAccessToken();
        if (token == null) {
            log.warn("获取令牌失败，无法查询最新指标: gatewayId={}", gatewayId);
            return Collections.emptyList();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = cloudMonitorRestTemplate.exchange(
                    METRICS_LAST_URL, HttpMethod.GET, entity, Map.class, gatewayId);

            Map<String, Object> body = response.getBody();
            if (body == null || !isSuccessCode(body.get("code"))) {
                String msg = body != null ? String.valueOf(body.get("message")) : "响应为空";
                log.warn("最新指标查询失败: gatewayId={}, message={}", gatewayId, msg);
                return Collections.emptyList();
            }

            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> metricsList = (List<Map<String, Object>>) data.get("metrics");
            if (metricsList == null || metricsList.isEmpty()) {
                return Collections.emptyList();
            }

            List<CloudMetric> result = new ArrayList<>(metricsList.size());
            for (Map<String, Object> m : metricsList) {
                long time = m.get("time") != null ? ((Number) m.get("time")).longValue() : 0;
                String name = (String) m.get("name");
                Object valueObj = m.get("value");
                double value = 0;
                if (valueObj instanceof Number) {
                    value = ((Number) valueObj).doubleValue();
                } else if (valueObj instanceof Boolean) {
                    value = ((Boolean) valueObj) ? 1.0 : 0.0;
                }
                result.add(new CloudMetric(time, name, value));
            }

            log.debug("最新指标查询成功: gatewayId={}, 指标数={}", gatewayId, result.size());
            return result;
        } catch (RestClientException e) {
            // 可能是 token 过期导致的 401，清除缓存以便下次重试
            log.warn("最新指标查询异常, 清除令牌缓存: gatewayId={}, error={}", gatewayId, e.getMessage());
            cachedToken = null;
            return Collections.emptyList();
        }
    }
}
