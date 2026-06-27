package com.xykj.auth.service;

import com.xykj.auth.entity.AuthUser;
import com.xykj.auth.vo.TokenVO;

public interface TokenService {

    /**
     * 创建Token对（access + refresh），持久化到数据库
     */
    TokenVO createTokens(AuthUser user, String deviceType, String deviceId, String ip, String userAgent);

    /**
     * 刷新Token：校验refreshToken，撤销旧的，返回新的
     */
    TokenVO refreshTokens(String refreshToken);

    /**
     * 撤销指定accessToken
     */
    void revokeToken(String accessToken);

    /**
     * 撤销用户所有有效Token
     */
    void revokeAllUserTokens(Long userId);

    /**
     * 检查Token是否已被撤销
     */
    boolean isTokenRevoked(String accessToken);

    /**
     * 清理过期的token记录
     * @return 清理的记录数
     */
    int cleanExpiredTokens();
}
