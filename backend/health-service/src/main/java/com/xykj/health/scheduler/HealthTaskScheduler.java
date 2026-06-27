package com.xykj.health.scheduler;

import com.xykj.health.entity.HealthCheckRecord;
import com.xykj.health.mapper.HealthCheckRecordMapper;
import com.xykj.health.service.HealthTaskLinkageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 晨检任务定时调度器
 * <p>
 * 1. 每日00:00根据活跃员工自动生成晨检任务（pending_check状态）
 * 2. 每日01:00检查并更新健康证状态
 * 3. 每日02:00清理离职员工的人脸特征数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthTaskScheduler {

    private static final DateTimeFormatter CHECK_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String STATUS_PENDING_CHECK = "pending_check";
    private static final int DEFAULT_WARNING_DAYS = 30;

    private final JdbcTemplate jdbcTemplate;
    private final HealthCheckRecordMapper healthCheckRecordMapper;
    private final com.xykj.health.mapper.HealthCertificateMapper certificateMapper;
    private final HealthTaskLinkageService healthTaskLinkageService;

    /**
     * 服务启动时自动检查并生成今日待晨检任务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("===== 服务启动，检查今日晨检任务 =====");
        // 一次性修复：将待检状态记录的 checkTime 清空（历史数据错误设置了00:00:00）
        fixPendingCheckTime();
        autoGenerateHealthCheckTasks();
        healthTaskLinkageService.reconcileTodayTasks();
    }

    /**
     * 一次性修复：将 pending_check 状态记录的 checkTime 清空
     * 历史版本错误地为待检记录设置了 checkTime = 当日零点，实际应为 null
     */
    private void fixPendingCheckTime() {
        try {
            int fixed = jdbcTemplate.update(
                "UPDATE health_check_record SET check_time = NULL WHERE status = 'pending_check' AND check_time IS NOT NULL AND TIME(check_time) = '00:00:00'"
            );
            if (fixed > 0) {
                log.info("修复待检记录的 checkTime（清空了 {} 条错误设置为00:00:00的记录）", fixed);
            }
        } catch (Exception e) {
            log.warn("修复 pending_check checkTime 失败: {}", e.getMessage());
        }
    }

    /**
     * 每日00:00执行：根据活跃员工自动生成晨检任务
     * <p>
     * 查询sys_employee表中的在职员工（需跨服务查询）
     * 幂等策略：通过 NOT EXISTS 确保同一员工同一天不会重复生成晨检记录
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void autoGenerateHealthCheckTasks() {
        log.info("===== 开始自动生成晨检任务 =====");

        // 查询活跃的后厨员工
        // 注意：这里需要跨服务访问 sys_employee 表
        // 本地开发环境直接使用 JdbcTemplate 查询共享数据库
        String sql = """
            SELECT e.id, e.real_name, e.org_id, e.tenant_id
            FROM sys_employee e
            INNER JOIN auth_user u ON e.user_id = u.id AND u.deleted = 0
            WHERE e.status = 'active'
              AND e.deleted = 0
              AND u.status = 'active'
              AND NOT EXISTS (
                SELECT 1 FROM health_check_record
                WHERE employee_id = e.id
                  AND check_date = CURDATE()
              )
            """;

        var employees = jdbcTemplate.queryForList(sql);

        if (employees.isEmpty()) {
            log.info("无需自动生成晨检任务");
            return;
        }

        LocalDate today = LocalDate.now();
        int count = 0;

        for (var employee : employees) {
            Long employeeId = ((Number) employee.get("id")).longValue();
            String employeeName = (String) employee.get("real_name");
            Long orgId = ((Number) employee.get("org_id")).longValue();
            Object tenantIdObj = employee.get("tenant_id");
            Long tenantId = tenantIdObj != null ? ((Number) tenantIdObj).longValue() : 1L;

            HealthCheckRecord record = new HealthCheckRecord();

            // 生成 check_no: HC-yyyyMMdd-NNN
            String datePart = today.format(CHECK_NO_FMT);
            int seq = healthCheckRecordMapper.nextSeqForDate(today);
            record.setCheckNo("HC-" + datePart + String.format("%03d", seq));

            record.setEmployeeId(employeeId);
            record.setEmployeeName(employeeName);
            record.setCheckDate(today);
            // 待检记录不应设置 checkTime，只有实际执行晨检时才记录时间
            record.setStatus(STATUS_PENDING_CHECK);
            record.setCheckResult("pending");
            record.setOrgId(orgId);
            record.setTenantId(tenantId);

            healthCheckRecordMapper.insert(record);
            count++;

            log.debug("自动生成晨检任务: employeeId={}, checkNo={}", employeeId, record.getCheckNo());
        }

        log.info("===== 自动生成晨检任务完成，共生成 {} 条 =====", count);
        healthTaskLinkageService.reconcileTodayTasks();
    }

    /**
     * 每 3 秒执行：根据人员/账号/组织最新状态联动刷新今日晨检任务
     */
    @Scheduled(initialDelay = 3000, fixedDelay = 3000)
    public void reconcileRealtimeTaskLinkage() {
        healthTaskLinkageService.reconcileTodayTasks();
    }

    /**
     * 每日01:00执行：检查并更新健康证状态
     * <p>
     * 检查所有健康证，根据到期日期更新状态
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void updateCertificateStatus() {
        log.info("===== 开始更新健康证状态 =====");

        LocalDate today = LocalDate.now();

        // 先批量标记已过期（这些不需要按 warning_days 区分）
        int expiredCount = jdbcTemplate.update(
                "UPDATE health_certificate SET status = 'expired', updated_at = NOW() " +
                        "WHERE expiry_date IS NOT NULL AND expiry_date < ? AND status != 'expired' AND deleted = 0",
                today);

        // 查询所有未过期的健康证，逐条按各自的 warning_days 计算状态
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.xykj.health.entity.HealthCertificate> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.xykj.health.entity.HealthCertificate>()
                        .ne(com.xykj.health.entity.HealthCertificate::getStatus, "expired");

        java.util.List<com.xykj.health.entity.HealthCertificate> certs = certificateMapper.selectList(wrapper);
        int expiringCount = 0;
        int validCount = 0;

        for (com.xykj.health.entity.HealthCertificate cert : certs) {
            if (cert.getExpiryDate() == null) continue;
            int warningDays = cert.getWarningDays() != null ? cert.getWarningDays() : DEFAULT_WARNING_DAYS;
            String newStatus;
            if (cert.getExpiryDate().isBefore(today)) {
                newStatus = "expired";
            } else if (!cert.getExpiryDate().isAfter(today.plusDays(warningDays))) {
                newStatus = "expiring";
            } else {
                newStatus = "valid";
            }

            if (!newStatus.equals(cert.getStatus())) {
                cert.setStatus(newStatus);
                certificateMapper.updateById(cert);
                if ("expiring".equals(newStatus)) expiringCount++;
                else if ("valid".equals(newStatus)) validCount++;
            }
        }

        log.info("===== 更新健康证状态完成, 已过期={}, 即将过期={}, 恢复有效={} =====", expiredCount, expiringCount, validCount);
    }

    /**
     * 每日02:00执行：清理离职员工的人脸特征数据
     * <p>
     * 查询sys_employee表中最近30天内离职的员工（status = resigned/left/inactive），
     * 物理删除其health_face_feature记录，并重置face_enrolled标记。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupResignedEmployeeFaceData() {
        log.info("===== 开始清理离职员工人脸特征数据 =====");

        // 查询最近30天内离职且有录入人脸的员工
        String querySql = """
            SELECT id
            FROM sys_employee
            WHERE status IN ('resigned', 'left', 'inactive')
              AND face_enrolled = 1
              AND updated_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
              AND deleted = 0
            """;

        var resignedEmployees = jdbcTemplate.queryForList(querySql);

        if (resignedEmployees.isEmpty()) {
            log.info("无需清理离职员工人脸特征数据");
            return;
        }

        int faceDeletedCount = 0;
        int employeeUpdatedCount = 0;

        for (var employee : resignedEmployees) {
            Long employeeId = ((Number) employee.get("id")).longValue();

            // 物理删除人脸特征记录（绕过MyBatis Plus的逻辑删除）
            int deleted = jdbcTemplate.update(
                "DELETE FROM health_face_feature WHERE employee_id = ?",
                employeeId
            );
            faceDeletedCount += deleted;

            // 重置员工人脸录入标记
            jdbcTemplate.update(
                "UPDATE sys_employee SET face_enrolled = 0, updated_at = NOW() WHERE id = ?",
                employeeId
            );
            employeeUpdatedCount++;

            log.debug("清理离职员工人脸数据: employeeId={}, faceRecordsDeleted={}", employeeId, deleted);
        }

        log.info("===== 清理离职员工人脸特征数据完成, 清理员工数={}, 删除人脸记录数={} =====",
                employeeUpdatedCount, faceDeletedCount);
    }
}
