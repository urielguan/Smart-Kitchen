package com.xykj.recipe;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 菜谱营养服务启动类
 * 端口: 8084
 */
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan({"com.xykj.recipe.mapper", "com.xykj.common.mapper", "com.xykj.common.ai.mapper"})
@ComponentScan(basePackages = {"com.xykj.recipe", "com.xykj.common"})
public class RecipeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecipeApplication.class, args);
        System.out.println("========================================");
        System.out.println("  Recipe Service Started Successfully!");
        System.out.println("  Port: 8084");
        System.out.println("========================================");
    }
}
