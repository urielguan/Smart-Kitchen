package com.xykj.device;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 设备管理服务启动类
 * 端口: 8088
 */
@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication
@MapperScan({"com.xykj.device.mapper", "com.xykj.common.mapper", "com.xykj.common.ai.mapper"})
@ComponentScan(basePackages = {"com.xykj.device", "com.xykj.common"})
public class DeviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceApplication.class, args);
        System.out.println("========================================");
        System.out.println("  Device Service Started Successfully!");
        System.out.println("  Port: 8088");
        System.out.println("========================================");
    }
}
