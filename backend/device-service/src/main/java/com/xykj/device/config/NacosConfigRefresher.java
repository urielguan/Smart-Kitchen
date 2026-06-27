package com.xykj.device.config;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Nacos 配置变更监听器
 * spring.cloud.refresh.enabled=false 时，ContextRefresher 不执行（保护 DataSource），
 * 但 NacosContextRefresher 仍会发布 RefreshEvent。
 * 本监听器捕获该事件，通过 ConfigService 获取最新配置并刷新目标 Bean。
 */
@Slf4j
@Component
public class NacosConfigRefresher {

    private final CloudMonitorConfig cloudMonitorConfig;
    private final NacosConfigProperties nacosConfigProperties;

    @Value("${spring.cloud.nacos.config.group:DEFAULT_GROUP}")
    private String group;

    @Value("${spring.cloud.nacos.config.prefix:smartfood_device_service}")
    private String prefix;

    @Value("${spring.cloud.nacos.config.file-extension:yaml}")
    private String fileExtension;

    private ConfigService configService;

    public NacosConfigRefresher(CloudMonitorConfig cloudMonitorConfig,
                                NacosConfigProperties nacosConfigProperties) {
        this.cloudMonitorConfig = cloudMonitorConfig;
        this.nacosConfigProperties = nacosConfigProperties;
    }

    @PostConstruct
    public void init() {
        try {
            Properties properties = new Properties();
            properties.put("serverAddr", nacosConfigProperties.getServerAddr());
            if (nacosConfigProperties.getNamespace() != null && !nacosConfigProperties.getNamespace().isBlank()) {
                properties.put("namespace", nacosConfigProperties.getNamespace());
            }
            if (nacosConfigProperties.getUsername() != null && !nacosConfigProperties.getUsername().isBlank()) {
                properties.put("username", nacosConfigProperties.getUsername());
            }
            if (nacosConfigProperties.getPassword() != null && !nacosConfigProperties.getPassword().isBlank()) {
                properties.put("password", nacosConfigProperties.getPassword());
            }
            configService = NacosFactory.createConfigService(properties);
            log.info("NacosConfigRefresher 初始化完成");
        } catch (NacosException e) {
            log.warn("NacosConfigRefresher 初始化失败，动态配置刷新不可用", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (configService != null) {
            try {
                configService.shutDown();
            } catch (NacosException e) {
                log.warn("NacosConfigService关闭失败", e);
            }
        }
    }

    @EventListener(RefreshEvent.class)
    public void onRefresh(RefreshEvent event) {
        if (configService == null) return;
        try {
            String dataId = prefix + "." + fileExtension;
            String content = configService.getConfig(dataId, group, 3000);
            if (content == null || content.isBlank()) return;

            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Map<String, Object> yamlMap = yaml.load(content);
            if (yamlMap == null) return;

            Map<String, Object> flatMap = new HashMap<>();
            flattenMap("", yamlMap, flatMap);

            long oldPollRate = cloudMonitorConfig.getPollRateMs();
            int oldStaleThreshold = cloudMonitorConfig.getStaleDataThresholdMinutes();

            Object pollRate = flatMap.get("cloud-monitor.poll-rate-ms");
            if (pollRate instanceof Number) {
                cloudMonitorConfig.setPollRateMs(((Number) pollRate).longValue());
            }
            Object staleThreshold = flatMap.get("cloud-monitor.stale-data-threshold-minutes");
            if (staleThreshold instanceof Number) {
                cloudMonitorConfig.setStaleDataThresholdMinutes(((Number) staleThreshold).intValue());
            }
            Object enabled = flatMap.get("cloud-monitor.enabled");
            if (enabled instanceof Boolean) {
                cloudMonitorConfig.setEnabled((Boolean) enabled);
            }

            log.info("Nacos配置已动态刷新: poll-rate-ms {} -> {}, stale-data-threshold-minutes {} -> {}",
                    oldPollRate, cloudMonitorConfig.getPollRateMs(),
                    oldStaleThreshold, cloudMonitorConfig.getStaleDataThresholdMinutes());
        } catch (NacosException e) {
            log.warn("获取Nacos配置失败", e);
        } catch (Exception e) {
            log.warn("Nacos配置动态刷新失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void flattenMap(String prefix, Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenMap(key, (Map<String, Object>) value, target);
            } else {
                target.put(key, value);
            }
        }
    }
}
