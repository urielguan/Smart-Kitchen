package com.xykj.scm.scheduler;

import com.xykj.scm.service.PurchaseDemandForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 采购需求预测二期闭环/优化调度任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseDemandForecastPhaseTwoScheduler {

    private final PurchaseDemandForecastService purchaseDemandForecastService;

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        log.info("===== 服务启动，开始初始化采购需求预测二期分析数据 =====");
        try {
            purchaseDemandForecastService.refreshAnalytics(null);
        } catch (Exception ex) {
            log.warn("采购需求预测二期启动初始化失败: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 每天凌晨 00:20 自动刷新供应商履约统计、预测回算与自动优化配置。
     */
    @Scheduled(cron = "0 20 0 * * ?")
    public void refreshDaily() {
        log.info("===== 开始执行采购需求预测二期日更任务 =====");
        try {
            purchaseDemandForecastService.refreshAnalytics(null);
        } catch (Exception ex) {
            log.warn("采购需求预测二期日更任务失败: {}", ex.getMessage(), ex);
        }
    }
}
