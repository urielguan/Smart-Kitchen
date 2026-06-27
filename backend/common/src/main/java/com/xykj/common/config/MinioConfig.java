package com.xykj.common.config;

import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * MinIO 文件存储配置
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    private String endpoint = "http://172.31.25.155:9000";
    private String accessKey = "tAi9pT1wuGLsu976lo0H";
    private String secretKey = "rJ46R3u0LPFB5MJmuUytCMKjso6x4uKs9tShtA33";
    private String bucketName = "smartcook";

    @Bean
    public MinioClient minioClient() {
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        log.info("Initializing MinIO client with endpoint: {}", normalizedEndpoint);
        return MinioClient.builder()
                .endpoint(normalizedEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    private String normalizeEndpoint(String rawEndpoint) {
        if (rawEndpoint == null || rawEndpoint.isBlank()) {
            return "http://127.0.0.1:9000";
        }

        String trimmed = rawEndpoint.trim();
        String withScheme = trimmed.contains("://") ? trimmed : "http://" + trimmed;
        URI uri = URI.create(withScheme);

        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Invalid MinIO endpoint: " + rawEndpoint);
        }

        return port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
    }
}
