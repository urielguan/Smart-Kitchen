package com.xykj.auth.service.impl;

import com.xykj.auth.constant.AuthConstants;
import com.xykj.auth.entity.AuthToken;
import com.xykj.auth.entity.AuthUser;
import com.xykj.auth.mapper.AuthTokenMapper;
import com.xykj.auth.mapper.AuthUserMapper;
import com.xykj.auth.service.TokenService;
import com.xykj.auth.vo.TokenVO;
import com.xykj.common.enums.ResultCode;
import com.xykj.common.exception.BizException;
import com.xykj.common.util.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final AuthTokenMapper authTokenMapper;
    private final AuthUserMapper authUserMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    /** Redis key: 用户token缓存 user_tokens:{userId} */
    private static final String USER_TOKENS_KEY_PREFIX = "user_tokens:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenVO createTokens(AuthUser user, String deviceType, String deviceId, String ip, String userAgent) {
        // 生成JWT（携带用户上下文信息）
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRealName(), user.getOrgId(), user.getTenantId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime accessExpires = now.plusSeconds(jwtTokenProvider.getAccessTokenValidity());
        LocalDateTime refreshExpires = now.plusSeconds(jwtTokenProvider.getRefreshTokenValidity());

        // 持久化到数据库
        AuthToken token = new AuthToken();
        token.setUserId(user.getId());
        token.setAccessToken(accessToken);
        token.setRefreshToken(refreshToken);
        token.setAccessTokenExpiresAt(accessExpires);
        token.setRefreshTokenExpiresAt(refreshExpires);
        token.setDeviceType(deviceType);
        token.setDeviceId(deviceId);
        token.setIpAddress(ip);
        token.setUserAgent(userAgent);
        token.setIsRevoked(0);
        token.setCreatedAt(now);
        authTokenMapper.insert(token);

        // 缓存到Redis
        cacheTokenInRedis(user.getId(), accessToken, jwtTokenProvider.getAccessTokenValidity());

        // 构建响应
        TokenVO vo = new TokenVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setExpiresIn(jwtTokenProvider.getAccessTokenValidity());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenVO refreshTokens(String refreshToken) {
        // 解析refreshToken
        Claims claims;
        try {
            claims = jwtTokenProvider.parseToken(refreshToken);
        } catch (Exception e) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        // 验证类型
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = claims.get("userId", Long.class);

        // 查询数据库中的token记录
        AuthToken existingToken = authTokenMapper.selectByRefreshToken(refreshToken);
        if (existingToken == null || existingToken.getIsRevoked() == 1) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        // 重新加载最新用户信息，避免refresh token中的旧claims导致上下文缺失
        AuthUser authUser = authUserMapper.selectById(userId);
        if (authUser == null || authUser.getDeleted() != null && authUser.getDeleted() == 1) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        if (!"active".equals(authUser.getStatus())) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        // 撤销旧token
        revokeTokenInDb(existingToken, "token_refreshed");

        // 生成新token（携带用户上下文信息）
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                userId,
                authUser.getUsername(),
                authUser.getRealName(),
                authUser.getOrgId(),
                authUser.getTenantId());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime accessExpires = now.plusSeconds(jwtTokenProvider.getAccessTokenValidity());
        LocalDateTime refreshExpires = now.plusSeconds(jwtTokenProvider.getRefreshTokenValidity());

        // 保存新token到数据库
        AuthToken newToken = new AuthToken();
        newToken.setUserId(userId);
        newToken.setAccessToken(newAccessToken);
        newToken.setRefreshToken(newRefreshToken);
        newToken.setAccessTokenExpiresAt(accessExpires);
        newToken.setRefreshTokenExpiresAt(refreshExpires);
        newToken.setDeviceType(existingToken.getDeviceType());
        newToken.setDeviceId(existingToken.getDeviceId());
        newToken.setIpAddress(existingToken.getIpAddress());
        newToken.setUserAgent(existingToken.getUserAgent());
        newToken.setIsRevoked(0);
        newToken.setCreatedAt(now);
        authTokenMapper.insert(newToken);

        // 缓存新token到Redis
        cacheTokenInRedis(userId, newAccessToken, jwtTokenProvider.getAccessTokenValidity());

        TokenVO vo = new TokenVO();
        vo.setAccessToken(newAccessToken);
        vo.setRefreshToken(newRefreshToken);
        vo.setExpiresIn(jwtTokenProvider.getAccessTokenValidity());
        return vo;
    }

    @Override
    public void revokeToken(String accessToken) {
        // 加入Redis黑名单
        addToBlacklist(accessToken);

        // 更新数据库
        AuthToken token = new AuthToken();
        token.setIsRevoked(1);
        token.setRevokedAt(LocalDateTime.now());
        token.setRevokedReason("user_logout");

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthToken> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(AuthToken::getAccessToken, accessToken).eq(AuthToken::getIsRevoked, 0);
        authTokenMapper.update(token, wrapper);

        // 清除Redis中的用户token缓存
        Long userId = getUserIdFromToken(accessToken);
        if (userId != null) {
            deleteTokenCacheInRedis(userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeAllUserTokens(Long userId) {
        // 查询该用户所有有效token，加入黑名单
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthToken> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(AuthToken::getUserId, userId).eq(AuthToken::getIsRevoked, 0);
        List<AuthToken> activeTokens = authTokenMapper.selectList(wrapper);
        for (AuthToken t : activeTokens) {
            addToBlacklist(t.getAccessToken());
        }

        // 删除Redis中的token缓存
        deleteTokenCacheInRedis(userId);

        // 批量撤销数据库
        authTokenMapper.revokeAllByUserId(userId);
    }

    @Override
    public boolean isTokenRevoked(String accessToken) {
        // 优先查Redis黑名单（快速路径）
        String key = AuthConstants.TOKEN_BLACKLIST_KEY_PREFIX + accessToken;
        Boolean blacklisted = hasBlacklistKey(key, accessToken);
        if (Boolean.TRUE.equals(blacklisted)) {
            return true;
        }

        // Redis未命中时，回查MySQL兜底（防止Redis重启后黑名单丢失）
        AuthToken token = authTokenMapper.selectByAccessToken(accessToken);
        if (token != null && token.getIsRevoked() == 1) {
            // 回写Redis黑名单，后续请求直接走Redis
            addToBlacklist(accessToken);
            return true;
        }

        return false;
    }

    /**
     * 定时清理过期的auth_token记录（每天凌晨0点执行一次）
     * 删除refreshToken已过期超过7天的记录，避免表数据无限增长
     */
    @Override
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public int cleanExpiredTokens() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthToken> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.lt(AuthToken::getRefreshTokenExpiresAt, threshold);
        int deleted = authTokenMapper.delete(wrapper);
        if (deleted > 0) {
            log.info("清理过期token记录: {}条", deleted);
        }
        return deleted;
    }

    // ==================== 私有方法 ====================

    private void revokeTokenInDb(AuthToken token, String reason) {
        token.setIsRevoked(1);
        token.setRevokedAt(LocalDateTime.now());
        token.setRevokedReason(reason);
        authTokenMapper.updateById(token);

        // 同时加入Redis黑名单
        addToBlacklist(token.getAccessToken());
    }

    private void addToBlacklist(String accessToken) {
        try {
            Claims claims = jwtTokenProvider.parseToken(accessToken);
            Date expiration = claims.getExpiration();
            long ttl = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (ttl > 0) {
                String key = AuthConstants.TOKEN_BLACKLIST_KEY_PREFIX + accessToken;
                try {
                    redisTemplate.opsForValue().set(key, "1", ttl, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Token黑名单写入Redis失败，继续保留数据库撤销状态兜底: error={}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Token已过期，无需加入黑名单");
        }
    }

    private void cacheTokenInRedis(Long userId, String accessToken, long ttlSeconds) {
        try {
            String key = USER_TOKENS_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, accessToken, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Token已缓存到Redis: userId={}, ttl={}s", userId, ttlSeconds);
        } catch (Exception e) {
            log.warn("Token缓存到Redis失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    private Long getUserIdFromToken(String accessToken) {
        try {
            Claims claims = jwtTokenProvider.parseToken(accessToken);
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean hasBlacklistKey(String key, String accessToken) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.warn("Redis黑名单校验失败，降级为数据库校验: error={}", e.getMessage());
            return null;
        }
    }

    private void deleteTokenCacheInRedis(Long userId) {
        try {
            redisTemplate.delete(USER_TOKENS_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("用户Token缓存删除失败，忽略并继续流程: userId={}, error={}", userId, e.getMessage());
        }
    }
}
