package com.xykj.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 强制下线工具类
 * <p>
 * 通过 Redis 标记实现用户强制下线。当管理员禁用/锁定/删除员工或员工离职时，
 * 在 Redis 中设置标记，所有服务的拦截器检查此标记并拦截请求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForceLogoutHelper {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "force_logout:";
    /** 与 access token 有效期一致（秒） */
    private static final long DEFAULT_TTL_SECONDS = 7200;
    /**
     * Redis 不可用时，临时跳过强制下线读检查的时间窗口，避免每个请求都阻塞在连接超时上。
     */
    private static final long READ_DEGRADE_WINDOW_MILLIS = 60_000L;

    /** 原因码 → 提示信息映射 */
    private static final Map<String, String> REASON_MESSAGES = Map.of(
            "disabled", "当前账号已被停用，您已被强制下线，请联系管理员处理",
            "locked", "当前账号已被锁定，您已被强制下线，请稍后重试或联系管理员",
            "left", "当前账号已被停用，您已被强制下线，请联系管理员处理",
            "deleted", "当前账号已被删除，您已被强制下线，请联系管理员处理"
    );

    private final AtomicLong readDegradedUntil = new AtomicLong(0L);
    private final AtomicBoolean degradeLogEmitted = new AtomicBoolean(false);

    /** 标记用户强制下线 */
    public void forceLogout(Long userId, String reason) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, reason, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("用户已被标记强制下线: userId={}, reason={}", userId, reason);
        } catch (Exception e) {
            log.error("标记强制下线失败（Redis 异常）: userId={}, reason={}", userId, reason, e);
        }
    }

    /** 获取强制下线原因码，null 表示未被强制下线 */
    public String getForceLogoutReason(Long userId) {
        long now = System.currentTimeMillis();
        if (readDegradedUntil.get() > now) {
            return null;
        }
        try {
            String reason = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
            resetReadDegradeIfNecessary();
            return reason;
        } catch (Exception e) {
            activateReadDegrade(now);
            if (degradeLogEmitted.compareAndSet(false, true)) {
                log.error(
                        "查询强制下线标记失败（Redis 异常），已降级跳过 {}ms 内的后续检查: userId={}",
                        READ_DEGRADE_WINDOW_MILLIS,
                        userId,
                        e
                );
            } else {
                log.debug("Redis 强制下线检查仍处于降级窗口内: userId={}", userId, e);
            }
            return null;
        }
    }

    /** 根据原因码获取提示信息 */
    public String getReasonMessage(String reason) {
        return REASON_MESSAGES.getOrDefault(reason, "您已被强制下线");
    }

    /** 清除强制下线标记（重新启用时调用） */
    public void clearForceLogout(Long userId) {
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.error("清除强制下线标记失败（Redis 异常）: userId={}", userId, e);
        }
    }

    private void activateReadDegrade(long now) {
        long degradedUntil = now + READ_DEGRADE_WINDOW_MILLIS;
        readDegradedUntil.updateAndGet(current -> Math.max(current, degradedUntil));
    }

    private void resetReadDegradeIfNecessary() {
        long degradedUntil = readDegradedUntil.getAndSet(0L);
        if (degradedUntil > 0 && degradeLogEmitted.compareAndSet(true, false)) {
            log.info("Redis 强制下线检查已恢复正常");
            return;
        }
        degradeLogEmitted.set(false);
    }
}
