package com.xykj.device.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 云监控平台对接配置
 * 对接过程云监控平台 (www.fromnet.cn) 的温湿度传感器数据采集
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "cloud-monitor")
public class CloudMonitorConfig {

    /** 是否启用云监控数据采集 */
    private boolean enabled = false;

    /** 平台登录账号 */
    private String name;

    /** 平台登录密码 */
    private String password;

    /** 平台应用 secretId */
    private String secretId;

    /** 轮询间隔（毫秒），默认 5 分钟 */
    private long pollRateMs = 300000;

    /** 数据时效阈值（分钟），collectedAt 超过此时间视为设备离线；0 或未配置表示不判断 */
    private int staleDataThresholdMinutes = 0;

    /** 设备映射列表 */
    private List<DeviceMapping> devices = new ArrayList<>();

    @Data
    public static class DeviceMapping {
        /** 云监控平台网关序列号 */
        private String serialNumber;
        /** 本地设备编码（对应 device_info.device_code） */
        private String deviceCode;
        /** 指标映射列表 */
        private List<MetricMapping> metrics = new ArrayList<>();
    }

    @Data
    public static class MetricMapping {
        /** 云监控平台指标名（如 C00, C01） */
        private String metricName;
        /** 本地数据类型（如 temperature, humidity） */
        private String dataType;
        /** 数据单位（如 ℃, %RH） */
        private String dataUnit;
    }

    @Bean
    public RestTemplate cloudMonitorRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new RestTemplate(factory);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("task-scheduler-");
        return scheduler;
    }

    @PostConstruct
    public void logConfig() {
        if (enabled) {
            log.info("云监控平台集成已启用, 配置设备数: {}", devices.size());
            devices.forEach(d -> log.info("  - 序列号: {}, 设备编码: {}, 指标数: {}",
                    d.getSerialNumber(), d.getDeviceCode(), d.getMetrics().size()));
        } else {
            log.info("云监控平台集成未启用");
        }
    }
}
