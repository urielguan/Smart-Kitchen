package com.xykj.wms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 仓储管理服务启动类
 * 端口: 8083
 */
@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication
@MapperScan({"com.xykj.wms.mapper", "com.xykj.common.mapper", "com.xykj.common.ai.mapper"})
@ComponentScan(basePackages = {"com.xykj.wms", "com.xykj.common"})
public class WmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
        System.out.println("========================================");
        System.out.println("  WMS Service Started Successfully!");
        System.out.println("  Port: 8083");
        System.out.println("========================================");
    }
}