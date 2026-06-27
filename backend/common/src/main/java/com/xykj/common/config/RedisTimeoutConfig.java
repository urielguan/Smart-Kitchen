package com.xykj.common.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Redis 超时配置。
 *
 * <p>本地联调环境下如果 Redis 未启动，默认连接超时会阻塞接口请求。
 * 这里统一收敛为较短超时，配合 ForceLogoutHelper 的降级逻辑快速失败。
 */
@Configuration
public class RedisTimeoutConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceClientConfigurationBuilderCustomizer(
            @Value("${smartfood.redis.connect-timeout-ms:500}") long connectTimeoutMs,
            @Value("${smartfood.redis.command-timeout-ms:500}") long commandTimeoutMs
    ) {
        return builder -> builder
                .commandTimeout(Duration.ofMillis(commandTimeoutMs))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                                .build())
                        .build());
    }
}
