package com.xykj.scm.scheduler;

import com.xykj.scm.service.SupplierQualificationAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 供应商资质临期提醒定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierQualificationAlertScheduler {

    private final SupplierQualificationAlertService supplierQualificationAlertService;

    /**
     * 每日凌晨 00:00 自动巡检供应商关键资质临期状态并记录站内提醒留痕
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void scanDailyAlerts() {
        log.info("===== 开始执行供应商资质临期巡检任务 =====");
        supplierQualificationAlertService.scanAndRecordDailyAlerts();
    }
}
