package com.xykj.health.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 健康证看板VO
 */
@Data
@Builder
public class HealthCertificateDashboardVO {

    /** 健康证总数 */
    private Integer totalCount;

    /** 有效数量 */
    private Integer validCount;

    /** 即将过期数量（30天内） */
    private Integer expiringCount;

    /** 已过期数量 */
    private Integer expiredCount;

    /** 未办理数量 */
    private Integer unregisteredCount;

    /** 紧急预警信息列表 */
    private List<UrgentWarning> urgentWarnings;

    @Data
    @Builder
    public static class UrgentWarning {
        private Long employeeId;
        private String employeeName;
        private LocalDate expiryDate;
        private Integer remainDays;
        private String warningType; // expired / expiring
    }
}
