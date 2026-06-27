package com.xykj.auth.service;

public interface LoginLogService {

    /**
     * 记录登录成功日志
     *
     * @param userId    用户ID
     * @param username  用户名
     * @param realName  真实姓名
     * @param orgId     组织ID
     * @param tenantId  租户ID
     * @param ip        IP地址
     * @param userAgent 用户代理
     */
    void recordLoginSuccess(Long userId, String username, String realName, Long orgId, Long tenantId, String ip, String userAgent);

    /**
     * 记录登录失败日志
     *
     * @param userId    用户ID（用户不存在时为null）
     * @param username  用户名
     * @param realName  真实姓名（用户不存在时为null）
     * @param orgId     组织ID（用户不存在时为null）
     * @param tenantId  租户ID（用户不存在时为null）
     * @param ip        IP地址
     * @param userAgent 用户代理
     * @param reason    失败原因
     */
    void recordLoginFailure(Long userId, String username, String realName, Long orgId, Long tenantId, String ip, String userAgent, String reason);
}
