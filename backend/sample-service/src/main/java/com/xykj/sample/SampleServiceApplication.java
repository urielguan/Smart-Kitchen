package com.xykj.sample;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 留样服务启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan({"com.xykj.sample.mapper", "com.xykj.common.mapper", "com.xykj.common.ai.mapper"})
@ComponentScan(basePackages = {"com.xykj.common", "com.xykj.sample"})
public class SampleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SampleServiceApplication.class, args);
        System.out.println("========================================");
        System.out.println("  Sample Service Started Successfully!");
        System.out.println("  Port: 8086");
        System.out.println("========================================");
    }
}
