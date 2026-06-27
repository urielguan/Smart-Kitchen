package com.xykj.cook;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 烹饪记录服务启动类
 * 端口: 8085
 */
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan({"com.xykj.cook.mapper", "com.xykj.common.mapper", "com.xykj.common.ai.mapper"})
@ComponentScan(basePackages = {"com.xykj.cook", "com.xykj.common"})
public class CookServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CookServiceApplication.class, args);
        System.out.println("========================================");
        System.out.println("  Cook Service Started Successfully!");
        System.out.println("  Port: 8085");
        System.out.println("========================================");
    }
}
