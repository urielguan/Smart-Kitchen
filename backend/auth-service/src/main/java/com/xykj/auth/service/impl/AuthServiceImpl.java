package com.xykj.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xykj.auth.constant.AuthConstants;
import com.xykj.auth.dto.ChangePasswordRequest;
import com.xykj.auth.dto.ForceChangePasswordRequest;
import com.xykj.auth.dto.LoginRequest;
import com.xykj.auth.dto.TokenRefreshRequest;
import com.xykj.auth.dto.UpdateProfileRequest;
import com.xykj.auth.entity.AuthPermission;
import com.xykj.auth.entity.AuthRole;
import com.xykj.auth.entity.AuthUser;
import com.xykj.auth.mapper.AuthPermissionMapper;
import com.xykj.auth.mapper.AuthRoleMapper;
import com.xykj.auth.mapper.AuthUserMapper;
import com.xykj.auth.service.AuthService;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.enums.ResultCode;
import com.xykj.common.exception.BizException;
import com.xykj.common.service.ForceLogoutHelper;
import com.xykj.auth.service.LoginLogService;
import com.xykj.auth.service.PasswordService;
import com.xykj.auth.service.TokenService;
import com.xykj.auth.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthUserMapper authUserMapper;
    private final AuthRoleMapper authRoleMapper;
    private final AuthPermissionMapper authPermissionMapper;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final LoginLogService loginLogService;
    private final StringRedisTemplate redisTemplate;
    private final ForceLogoutHelper forceLogoutHelper;

    @Override
    public LoginVO login(LoginRequest request, String ip, String userAgent) {
        // 1. 查询用户
        AuthUser user = authUserMapper.selectByUsername(request.getUsername());
        if (user == null) {
            loginLogService.recordLoginFailure(null, request.getUsername(), null, null, null, ip, userAgent, "用户不存在");
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 2. 检查账号状态
        if ("inactive".equals(user.getStatus())) {
            loginLogService.recordLoginFailure(user.getId(), user.getUsername(), user.getRealName(), user.getOrgId(), user.getTenantId(), ip, userAgent, "账号已禁用");
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        // 3. 检查锁定状态
        if ("locked".equals(user.getStatus())) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                loginLogService.recordLoginFailure(user.getId(), user.getUsername(), user.getRealName(), user.getOrgId(), user.getTenantId(), ip, userAgent, "账号已锁定");
                throw new BizException(ResultCode.ACCOUNT_LOCKED);
            } else {
                // 锁定已过期，自动解锁
                unlockUser(user);
            }
        }

        // 4. 验证密码
        if (!passwordService.matches(request.getPassword(), user.getPassword())) {
            boolean locked = handleLoginFailure(user, ip, userAgent);
            if (locked) {
                throw new BizException(ResultCode.ACCOUNT_LOCKED);
            }
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 5. 登录成功：清除失败计数
        clearLoginFailCount(user.getId());
        resetUserLockFields(user);

        // 更新登录信息
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        authUserMapper.updateById(user);

        // 6. 创建Token
        TokenVO tokenVO = tokenService.createTokens(user,
                request.getDeviceType(), request.getDeviceId(), ip, userAgent);

        // 7. 查询角色和权限
        List<AuthRole> roles = authRoleMapper.selectByUserId(user.getId());
        List<AuthPermission> permissions = authPermissionMapper.selectByUserId(user.getId());

        List<String> roleCodes = roles.stream().map(AuthRole::getRoleCode).collect(Collectors.toList());
        List<String> permissionCodes = permissions.stream().map(AuthPermission::getPermissionCode).collect(Collectors.toList());

        // 8. 记录登录成功日志
        loginLogService.recordLoginSuccess(user.getId(), user.getUsername(), user.getRealName(), user.getOrgId(), user.getTenantId(), ip, userAgent);

        // 9. 构建响应（只返回token）
        LoginVO vo = new LoginVO();
        vo.setAccessToken(tokenVO.getAccessToken());
        vo.setRefreshToken(tokenVO.getRefreshToken());
        vo.setExpiresIn(tokenVO.getExpiresIn());
        // 首次登录强制改密（admin 除外）
        boolean mustChange = !"admin".equals(user.getUsername())
                && (user.getPasswordChanged() == null || user.getPasswordChanged() == 0);
        vo.setMustChangePassword(mustChange);
        return vo;
    }

    @Override
    public void logout(String accessToken) {
        tokenService.revokeToken(accessToken);
        log.info("用户登出成功");
    }

    @Override
    public TokenVO refreshToken(TokenRefreshRequest request) {
        return tokenService.refreshTokens(request.getRefreshToken());
    }

    @Override
    public UserInfoVO getCurrentUser(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        List<AuthRole> roles = authRoleMapper.selectByUserId(userId);
        if (roles == null) {
            roles = Collections.emptyList();
        }

        List<AuthPermission> permissions = authPermissionMapper.selectByUserId(userId);
        if (permissions == null) {
            permissions = Collections.emptyList();
        }

        // 查询组织名称
        String orgName = user.getOrgId() == null || user.getOrgId() <= 0
                ? null
                : authUserMapper.selectOrgNameById(user.getOrgId());

        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setGender(user.getGender());
        vo.setOrgId(user.getOrgId());
        vo.setOrgName(orgName);
        vo.setTenantId(user.getTenantId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());

        vo.setRoles(roles.stream()
                .filter(Objects::nonNull)
                .map(r -> {
            RoleVO rv = new RoleVO();
            rv.setRoleId(r.getId());
            rv.setRoleCode(r.getRoleCode());
            rv.setRoleName(r.getRoleName());
            rv.setRoleDesc(r.getRoleDesc());
            return rv;
        }).collect(Collectors.toList()));

        vo.setPermissions(permissions.stream()
                .filter(Objects::nonNull)
                .map(p -> {
            PermissionVO pv = new PermissionVO();
            pv.setPermissionId(p.getId());
            pv.setPermissionCode(p.getPermissionCode());
            pv.setPermissionName(p.getPermissionName());
            pv.setPermissionType(p.getPermissionType());
            pv.setResourcePath(p.getResourcePath());
            return pv;
        }).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.AUTH_PASSWORD,
        operationType = AuditOperationType.PASSWORD_CHANGE,
        targetId = "#userId",
        targetNo = "",
        desc = "'修改密码'"
    )
    public void changePassword(Long userId, ChangePasswordRequest request) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        // 验证原密码
        if (!passwordService.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }

        // 校验新密码复杂度
        passwordService.validatePasswordComplexity(request.getNewPassword());

        // 新旧密码不能相同
        if (passwordService.matches(request.getNewPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_SAME_AS_OLD);
        }

        // 更新密码
        user.setPassword(passwordService.encode(request.getNewPassword()));
        user.setPasswordChanged(1);
        authUserMapper.updateById(user);

        // 撤销所有Token，强制重新登录
        tokenService.revokeAllUserTokens(userId);

        log.info("用户修改密码成功: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.AUTH_PASSWORD,
        operationType = AuditOperationType.PASSWORD_CHANGE,
        targetId = "#userId",
        targetNo = "",
        desc = "'首次登录强制修改密码'"
    )
    public void forceChangePassword(Long userId, ForceChangePasswordRequest request) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        // 校验新密码复杂度
        passwordService.validatePasswordComplexity(request.getNewPassword());

        // 新密码不能与原密码相同
        if (passwordService.matches(request.getNewPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_SAME_AS_OLD);
        }

        // 更新密码并标记已修改
        user.setPassword(passwordService.encode(request.getNewPassword()));
        user.setPasswordChanged(1);
        authUserMapper.updateById(user);

        // 撤销所有Token，强制重新登录
        tokenService.revokeAllUserTokens(userId);

        log.info("首次登录强制修改密码成功: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.AUTH_PROFILE,
        operationType = AuditOperationType.PROFILE_UPDATE,
        targetId = "#userId",
        targetNo = "",
        desc = "'修改个人信息'"
    )
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        String normalizedEmail = request.getEmail() == null
                ? null
                : (StrUtil.isBlank(request.getEmail()) ? null : request.getEmail().trim());

        // 更新 auth_user 表
        if (StrUtil.isNotBlank(request.getPhone())) {
            Long phoneCount = authUserMapper.countByPhoneExcludeId(request.getPhone(), userId);
            if (phoneCount != null && phoneCount > 0) {
                throw BizException.conflict("手机号已存在");
            }
        }
        if (StrUtil.isNotBlank(normalizedEmail)) {
            Long emailCount = authUserMapper.countByEmailExcludeId(normalizedEmail, userId);
            if (emailCount != null && emailCount > 0) {
                throw BizException.conflict("邮箱已存在");
            }
        }

        // 邮箱单独更新：显式 set，确保清空邮箱（null）也能落库
        if (request.getEmail() != null) {
            try {
                authUserMapper.update(null, new LambdaUpdateWrapper<AuthUser>()
                        .eq(AuthUser::getId, userId)
                        .eq(AuthUser::getDeleted, 0)
                        .set(AuthUser::getEmail, normalizedEmail)
                        .set(AuthUser::getUpdatedBy, UserContext.getUserId())
                        .setSql("updated_at = NOW()"));
                // 避免后续 updateById 把查询时的旧邮箱值再写回去
                user.setEmail(normalizedEmail);
            } catch (DuplicateKeyException e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("uk_user_email")) {
                    throw BizException.conflict("邮箱已存在");
                }
                throw BizException.conflict("邮箱已存在");
            }
        }

        // 更新 auth_user 其余字段
        boolean changed = false;
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
            changed = true;
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
            changed = true;
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
            changed = true;
        }
        if (changed) {
            try {
                authUserMapper.updateById(user);
            } catch (DuplicateKeyException e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("uk_user_phone")) {
                    throw BizException.conflict("手机号已存在");
                }
                throw BizException.conflict("手机号已存在");
            }
        }

        // 同步更新 sys_employee 表（admin 等无员工档案时跳过，不影响个人信息修改）
        int affected = authUserMapper.updateEmployeeByUserId(userId,
                normalizedEmail, request.getPhone(),
                request.getGender(), request.getAvatarUrl(),
                UserContext.getUserId());
        if (affected == 0) {
            log.info("用户未关联员工档案，跳过员工信息同步: userId={}", userId);
        }

        log.info("用户修改个人信息成功: userId={}", userId);
    }

    // ==================== 登录失败处理 ====================

    private boolean handleLoginFailure(AuthUser user, String ip, String userAgent) {
        String key = AuthConstants.LOGIN_FAIL_KEY_PREFIX + user.getId();

        // Redis递增失败计数
        Long failCount = incrementLoginFailCount(key, user);

        loginLogService.recordLoginFailure(user.getId(), user.getUsername(), user.getRealName(), user.getOrgId(), user.getTenantId(), ip, userAgent,
                "密码错误，第" + failCount + "次失败");

        // 达到5次，锁定账号
        boolean locked = false;
        if (failCount != null && failCount >= AuthConstants.LOGIN_FAIL_MAX_COUNT) {
            lockUser(user);
            locked = true;
        }

        // 更新数据库的失败次数
        user.setLoginFailCount(failCount != null ? failCount.intValue() : 1);
        authUserMapper.updateById(user);

        return locked;
    }

    private void lockUser(AuthUser user) {
        user.setStatus("locked");
        user.setLockedUntil(LocalDateTime.now().plusMinutes(AuthConstants.ACCOUNT_LOCK_DURATION_MINUTES));
        user.setLoginFailCount(AuthConstants.LOGIN_FAIL_MAX_COUNT);
        authUserMapper.updateById(user);
        log.warn("账号已锁定: userId={}, username={}, 锁定{}分钟", user.getId(), user.getUsername(),
                AuthConstants.ACCOUNT_LOCK_DURATION_MINUTES);

        // 强制下线：撤销所有 token 并设置标记
        try {
            tokenService.revokeAllUserTokens(user.getId());
            forceLogoutHelper.forceLogout(user.getId(), "locked");
        } catch (Exception e) {
            log.error("锁定账号后强制下线失败: userId={}", user.getId(), e);
        }
    }

    private void unlockUser(AuthUser user) {
        user.setStatus("active");
        user.setLockedUntil(null);
        user.setLoginFailCount(0);
        authUserMapper.updateById(user);
        forceLogoutHelper.clearForceLogout(user.getId());
        log.info("账号自动解锁: userId={}, username={}", user.getId(), user.getUsername());
    }

    private void resetUserLockFields(AuthUser user) {
        if (user.getLoginFailCount() > 0 || "locked".equals(user.getStatus())) {
            user.setLoginFailCount(0);
            user.setLockedUntil(null);
            if ("locked".equals(user.getStatus())) {
                user.setStatus("active");
            }
            authUserMapper.updateById(user);
        }
    }

    private void clearLoginFailCount(Long userId) {
        String key = AuthConstants.LOGIN_FAIL_KEY_PREFIX + userId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("登录失败计数清理失败，忽略并继续登录成功流程: userId={}, error={}", userId, e.getMessage());
        }
    }

    // ==================== 数据脱敏 ====================

    private Long incrementLoginFailCount(String key, AuthUser user) {
        try {
            Long failCount = redisTemplate.opsForValue().increment(key);
            if (failCount != null && failCount == 1) {
                redisTemplate.expire(key, AuthConstants.LOGIN_FAIL_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            return failCount != null ? failCount : 1L;
        } catch (Exception e) {
            long fallbackCount = user.getLoginFailCount() != null
                    ? user.getLoginFailCount() + 1L
                    : 1L;
            log.warn("登录失败计数写入Redis失败，降级使用数据库计数: userId={}, error={}",
                    user.getId(), e.getMessage());
            return fallbackCount;
        }
    }

    private String maskEmail(String email) {
        if (StrUtil.isBlank(email)) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
