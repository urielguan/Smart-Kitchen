package com.xykj.common.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "smartfood.ai")
public class AiProperties {

    private String cryptoKey = "SmartKitchenAI#1";
    private Integer connectTimeoutMs = 10000;
    private Integer readTimeoutMs = 120000;
    private Double violationThreshold = 0.8D;
    private boolean nacosFallbackEnabled = true;
    private List<DefaultService> defaultServices = new ArrayList<>();

    @Data
    public static class DefaultService {
        private String serviceName;
        private String serviceType;
        private String baseUrl;
        private String apiKey;
        private String apiKeyEncrypted;
        private String modelName;
        private List<String> applicableModules = new ArrayList<>();
        private String status = "inactive";
        private String remark;
    }
}
