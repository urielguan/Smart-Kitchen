package com.xykj.auth.service;

import com.xykj.auth.dto.ChangePasswordRequest;
import com.xykj.auth.dto.ForceChangePasswordRequest;
import com.xykj.auth.dto.LoginRequest;
import com.xykj.auth.dto.TokenRefreshRequest;
import com.xykj.auth.dto.UpdateProfileRequest;
import com.xykj.auth.vo.LoginVO;
import com.xykj.auth.vo.TokenVO;
import com.xykj.auth.vo.UserInfoVO;

public interface AuthService {

    /**
     * 用户登录
     */
    LoginVO login(LoginRequest request, String ip, String userAgent);

    /**
     * 用户登出
     */
    void logout(String accessToken);

    /**
     * 刷新Token
     */
    TokenVO refreshToken(TokenRefreshRequest request);

    /**
     * 获取当前用户信息
     */
    UserInfoVO getCurrentUser(Long userId);

    /**
     * 修改密码
     */
    void changePassword(Long userId, ChangePasswordRequest request);

    /**
     * 强制修改密码（首次登录，不需要原密码）
     */
    void forceChangePassword(Long userId, ForceChangePasswordRequest request);

    /**
     * 修改用户信息（同步更新 auth_user 和 sys_employee）
     */
    void updateProfile(Long userId, UpdateProfileRequest request);
}
