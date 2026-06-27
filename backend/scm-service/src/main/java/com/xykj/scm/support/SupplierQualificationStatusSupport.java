package com.xykj.scm.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 供应商资质状态计算支持类
 */
public final class SupplierQualificationStatusSupport {

    public static final int NEAR_EXPIRE_THRESHOLD_DAYS = 30;
    public static final String STATUS_VALID = "valid";
    public static final String STATUS_NEAR_EXPIRE = "near_expire";
    public static final String STATUS_EXPIRED = "expired";

    private SupplierQualificationStatusSupport() {
    }

    public static String resolveStatus(LocalDateTime expiresAt) {
        return resolveStatus(expiresAt, LocalDate.now());
    }

    public static String resolveStatus(LocalDateTime expiresAt, LocalDate today) {
        if (expiresAt == null) {
            return null;
        }
        long daysRemaining = ChronoUnit.DAYS.between(today, expiresAt.toLocalDate());
        if (daysRemaining < 0) {
            return STATUS_EXPIRED;
        }
        if (daysRemaining <= NEAR_EXPIRE_THRESHOLD_DAYS) {
            return STATUS_NEAR_EXPIRE;
        }
        return STATUS_VALID;
    }

    public static Integer resolveRemainingDays(LocalDateTime expiresAt) {
        return resolveRemainingDays(expiresAt, LocalDate.now());
    }

    public static Integer resolveRemainingDays(LocalDateTime expiresAt, LocalDate today) {
        if (expiresAt == null) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(today, expiresAt.toLocalDate()));
    }

    public static boolean isNearExpire(LocalDateTime expiresAt, LocalDate today) {
        return STATUS_NEAR_EXPIRE.equals(resolveStatus(expiresAt, today));
    }
}
