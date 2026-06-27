package com.xykj.device.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xykj.device.entity.AlertDispatch;
import com.xykj.device.entity.AlertWorkOrderRecord;
import com.xykj.device.mapper.AlertDispatchMapper;
import com.xykj.device.mapper.AlertWorkOrderRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警派单超时检测定时任务 (SC-18: 处理超时自动提醒)
 * - 每 5 分钟检查所有已过截止时间但尚未完成的派单
 * - 将超时工单优先级提升为 high
 * - 写入操作记录标记超时提醒
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDispatchOverdueScheduler {

    private final AlertDispatchMapper dispatchMapper;
    private final AlertWorkOrderRecordMapper workOrderRecordMapper;

    @Scheduled(fixedRate = 300000) // 每 5 分钟
    @Transactional(rollbackFor = Exception.class)
    public void checkOverdueDispatches() {
        LocalDateTime now = LocalDateTime.now();

        // 查询所有已过截止时间、仍为待处理/处理中状态的工单
        List<AlertDispatch> overdueList = dispatchMapper.selectList(
                new LambdaQueryWrapper<AlertDispatch>()
                        .lt(AlertDispatch::getDeadline, now)
                        .in(AlertDispatch::getStatus, "pending", "processing")
                        .isNotNull(AlertDispatch::getDeadline));

        if (overdueList.isEmpty()) {
            return;
        }

        log.info("检测到 {} 条超时未处理告警工单", overdueList.size());

        for (AlertDispatch dispatch : overdueList) {
            // 提升优先级为 high（如果还不是 high）
            if (!"high".equals(dispatch.getPriority())) {
                dispatchMapper.update(null, new LambdaUpdateWrapper<AlertDispatch>()
                        .eq(AlertDispatch::getId, dispatch.getId())
                        .set(AlertDispatch::getPriority, "high"));
            }

            // 写入超时提醒操作记录
            AlertWorkOrderRecord record = new AlertWorkOrderRecord();
            record.setDispatchId(dispatch.getId());
            record.setAlertId(dispatch.getAlertId());
            record.setAction("overdue_reminder");
            record.setActionName("超时提醒");
            record.setOperatorId(0L);
            record.setOperatorName("系统");
            record.setContent("工单已超过截止时间 " + dispatch.getDeadline()
                    + "，优先级已自动提升为高");
            workOrderRecordMapper.insert(record);

            log.info("告警工单超时提醒: dispatchNo={}, handler={}, deadline={}",
                    dispatch.getDispatchNo(), dispatch.getHandlerName(), dispatch.getDeadline());
        }
    }
}
