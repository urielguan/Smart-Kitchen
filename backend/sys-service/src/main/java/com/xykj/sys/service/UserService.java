package com.xykj.sys.service;

import com.xykj.sys.entity.User;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 为员工创建用户账号
     *
     * @param username 用户名（员工编号）
     * @param password 明文密码（为空则使用默认密码123456）
     * @param realName 真实姓名
     * @param phone 手机号
     * @param email 邮箱
     * @param orgId 组织ID
     * @param tenantId 租户ID
     * @param gender 性别（0=未知，1=男，2=女）
     * @param accountStatus 账号状态（active/inactive/locked）
     * @return 用户实体
     */
    User createForEmployee(String username, String password, String realName,
                           String phone, String email, Long orgId, Long tenantId, Integer gender, String accountStatus);

    /**
     * 更新用户基本信息
     *
     * @param userId 用户ID
     * @param realName 真实姓名
     * @param phone 手机号
     * @param email 邮箱
     * @param orgId 组织ID
     * @param gender 性别（0=未知，1=男，2=女）
     */
    void updateUserInfo(Long userId, String realName, String phone, String email, Long orgId, Integer gender);

    /**
     * 更新用户状态
     *
     * @param userId 用户ID
     * @param status 状态（active/inactive/locked）
     */
    void updateStatus(Long userId, String status);

    /**
     * 逻辑删除用户
     *
     * @param userId 用户ID
     */
    void deleteById(Long userId);

    /**
     * 根据ID查询用户
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    User getById(Long userId);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    User getByUsername(String username);

    /**
     * 统计指定手机号数量（排除指定用户ID）
     */
    Long countByPhoneExcludeId(String phone, Long excludeUserId);

    /**
     * 统计指定邮箱数量（排除指定用户ID）
     */
    Long countByEmailExcludeId(String email, Long excludeUserId);
}
