package com.xykj.sys;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 系统管理服务启动类
 * 端口: 8089
 * 功能: 组织管理、员工管理、角色管理、权限管理
 */
@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication
@MapperScan({"com.xykj.sys.mapper", "com.xykj.common.mapper", "com.xykj.common.ai.mapper"})
@ComponentScan(basePackages = {"com.xykj.sys", "com.xykj.common"})
public class SysApplication {

    public static void main(String[] args) {
        SpringApplication.run(SysApplication.class, args);
        System.out.println("========================================");
        System.out.println("  系统管理服务启动成功！");
        System.out.println("  端口: 8089");
        System.out.println("  接口文档: http://localhost:8089/doc.html");
        System.out.println("========================================");
    }
}
