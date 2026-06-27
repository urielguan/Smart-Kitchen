package com.xykj.scm.scheduler;

import com.xykj.scm.service.SupplierAiScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 供应商 AI 综合评分定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierAiScoreScheduler {

    private final SupplierAiScoreService supplierAiScoreService;

    /**
     * 服务启动后立即补齐一次评分，保证前端首次进入即可查看真实结果
     */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        log.info("===== 服务启动，开始初始化供应商 AI 综合评分 =====");
        supplierAiScoreService.refreshAllSupplierScores();
    }

    /**
     * 每日凌晨 00:00 自动刷新全部供应商 AI 综合评分
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void refreshDaily() {
        log.info("===== 开始执行供应商 AI 综合评分日更任务 =====");
        supplierAiScoreService.refreshAllSupplierScores();
    }
}
