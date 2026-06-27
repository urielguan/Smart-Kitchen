package com.xykj.scm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 供应链管理服务启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan({"com.xykj.scm.mapper", "com.xykj.common.mapper", "com.xykj.common.ai.mapper"})
@ComponentScan(basePackages = {"com.xykj.scm", "com.xykj.common"})
public class ScmApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScmApplication.class, args);
        System.out.println("========================================");
        System.out.println("  SCM Service Started Successfully!");
        System.out.println("  Port: 8082");
        System.out.println("========================================");
    }
}
