package com.xykj.sys.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xykj.common.context.UserContext;
import com.xykj.common.exception.BizException;
import com.xykj.sys.entity.User;
import com.xykj.sys.mapper.UserMapper;
import com.xykj.sys.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    /**
     * 默认密码
     */
    private static final String DEFAULT_PASSWORD = "123456";

    /**
     * BCrypt密码加密器
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createForEmployee(String username, String password, String realName,
                                   String phone, String email, Long orgId, Long tenantId, Integer gender, String accountStatus) {
        // 检查用户名是否已存在
        Long count = userMapper.countByUsernameExcludeId(username, null);
        if (count > 0) {
            throw BizException.conflict("用户名已存在: " + username);
        }

        String normalizedEmail = StrUtil.isBlank(email) ? null : email.trim();

        if (StrUtil.isNotBlank(phone)) {
            Long phoneCount = userMapper.countByPhoneExcludeId(phone, null);
            if (phoneCount != null && phoneCount > 0) {
                throw BizException.conflict("手机号已存在");
            }
        }
        if (StrUtil.isNotBlank(normalizedEmail)) {
            Long emailCount = userMapper.countByEmailExcludeId(normalizedEmail, null);
            if (emailCount != null && emailCount > 0) {
                throw BizException.conflict("邮箱已存在");
            }
        }

        User user = new User();
        user.setUsername(username);
        // 密码加密：为空则使用默认密码
        String pwd = StrUtil.isBlank(password) ? DEFAULT_PASSWORD : password;
        user.setPassword(passwordEncoder.encode(pwd));
        user.setRealName(realName);
        user.setPhone(phone);
        user.setEmail(normalizedEmail);
        user.setOrgId(orgId != null ? orgId : 1L);
        user.setTenantId(tenantId != null ? tenantId : 1L);
        user.setStatus(StrUtil.isNotBlank(accountStatus) ? accountStatus : "active");
        user.setGender(gender != null ? gender : 0);
        user.setLoginFailCount(0);

        try {
            userMapper.insert(user);
        } catch (Exception ex) {
            if (containsKeyInCauseChain(ex, "uk_user_phone")) {
                throw BizException.conflict("手机号已存在");
            }
            if (containsKeyInCauseChain(ex, "uk_user_email")) {
                throw BizException.conflict("邮箱已存在");
            }
            if (containsKeyInCauseChain(ex, "uk_user_username")) {
                throw BizException.conflict("用户名已存在: " + username);
            }
            throw ex;
        }
        log.info("创建用户账号成功: username={}, userId={}", username, user.getId());

        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(Long userId, String realName, String phone, String email, Long orgId, Integer gender) {
        User user = getById(userId);
        if (user == null) {
            return;
        }

        String normalizedEmail = email == null ? null : (StrUtil.isBlank(email) ? null : email.trim());

        if (realName != null) {
            user.setRealName(realName);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        if (email != null) {
            user.setEmail(normalizedEmail);
        }
        if (orgId != null) {
            user.setOrgId(orgId);
        }
        if (gender != null) {
            user.setGender(gender);
        }

        // 用更新后的最终值做唯一性校验，避免直接落库触发约束异常。
        String effectivePhone = phone != null ? phone : user.getPhone();
        if (StrUtil.isNotBlank(effectivePhone)) {
            Long phoneCount = userMapper.countByPhoneExcludeId(effectivePhone, userId);
            if (phoneCount != null && phoneCount > 0) {
                throw BizException.conflict("手机号已存在");
            }
        }

        String effectiveEmail = normalizedEmail != null ? normalizedEmail : user.getEmail();
        if (StrUtil.isNotBlank(effectiveEmail)) {
            Long emailCount = userMapper.countByEmailExcludeId(effectiveEmail, userId);
            if (emailCount != null && emailCount > 0) {
                throw BizException.conflict("邮箱已存在");
            }
        }

        try {
            userMapper.updateById(user);
        } catch (Exception ex) {
            if (containsKeyInCauseChain(ex, "uk_user_phone")) {
                throw BizException.conflict("手机号已存在");
            }
            if (containsKeyInCauseChain(ex, "uk_user_email")) {
                throw BizException.conflict("邮箱已存在");
            }
            throw ex;
        }
        log.info("更新用户信息: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, String status) {
        User user = getById(userId);
        if (user == null) {
            return;
        }

        // 不允许手动设置为locked状态（locked由登录失败机制触发）
        if ("locked".equals(status)) {
            log.warn("不允许手动设置用户为锁定状态: userId={}", userId);
            return;
        }

        user.setStatus(status);
        userMapper.updateById(user);
        log.info("更新用户状态: userId={}, status={}", userId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long userId) {
        if (userId == null) {
            return;
        }
        userMapper.deleteById(userId);
        log.info("删除用户账号: userId={}", userId);
    }

    @Override
    public User getById(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectById(userId);
    }

    @Override
    public User getByUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return null;
        }
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .eq(User::getDeleted, 0)
        );
    }

    @Override
    public Long countByPhoneExcludeId(String phone, Long excludeUserId) {
        if (StrUtil.isBlank(phone)) {
            return 0L;
        }
        return userMapper.countByPhoneExcludeId(phone, excludeUserId);
    }

    @Override
    public Long countByEmailExcludeId(String email, Long excludeUserId) {
        if (StrUtil.isBlank(email)) {
            return 0L;
        }
        return userMapper.countByEmailExcludeId(email, excludeUserId);
    }

    /**
     * 检查异常及其 cause 链中是否包含指定唯一索引关键字。
     */
    private boolean containsKeyInCauseChain(Throwable throwable, String keyName) {
        Throwable current = throwable;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.contains(keyName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
