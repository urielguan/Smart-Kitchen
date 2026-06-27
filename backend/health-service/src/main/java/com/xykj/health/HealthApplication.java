package com.xykj.health;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 健康服务启动类
 */
@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(basePackages = {"com.xykj.health", "com.xykj.common"})
@EnableScheduling
@MapperScan({"com.xykj.health.mapper", "com.xykj.common.mapper", "com.xykj.common.ai.mapper"})
public class HealthApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthApplication.class, args);
        System.out.println("========================================");
        System.out.println("  Health Service Started Successfully!");
        System.out.println("  Port: 8087");
        System.out.println("========================================");
    }
}
