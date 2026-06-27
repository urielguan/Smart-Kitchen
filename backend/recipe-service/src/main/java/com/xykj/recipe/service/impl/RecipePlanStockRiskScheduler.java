package com.xykj.recipe.service.impl;

import com.xykj.recipe.service.RecipePlanService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 菜谱计划库存风险自动复检调度器。
 * 仅作用于菜谱计划模块，按 T-7 / T-3 / T-1 节奏自动刷新风险快照。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipePlanStockRiskScheduler {

    private final RecipePlanService recipePlanService;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "recipe-plan-stock-risk-recheck");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::safeRun, 5, 360, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void safeRun() {
        try {
            int count = recipePlanService.autoRecheckStockRiskByCadence();
            if (count > 0) {
                log.info("菜谱计划库存风险自动复检已执行，本轮处理{}条计划", count);
            }
        } catch (Exception e) {
            log.error("菜谱计划库存风险自动复检执行失败", e);
        }
    }
}
