package com.xykj.wms.scheduler;

import com.xykj.wms.service.alert.MaterialAlertEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 物料告警定时任务
 * 每日凌晨2点全量扫描物料效期与库存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialAlertScheduler {

    private final MaterialAlertEngine materialAlertEngine;

    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyMaterialAlertScan() {
        log.info("===== 开始执行物料告警每日巡检任务 =====");
        try {
            materialAlertEngine.checkAllMaterials();
        } catch (Exception e) {
            log.error("物料告警每日巡检任务异常", e);
        }
        log.info("===== 物料告警每日巡检任务完成 =====");
    }
}
