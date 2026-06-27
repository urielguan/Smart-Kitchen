package com.xykj.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT Token 工具类
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:yingzi-smart-kitchen-secret-key-for-jwt-token-generation-2024}")
    private String secret;

    @Value("${jwt.access-token-validity:7200}")
    private Long accessTokenValidity; // 秒

    @Value("${jwt.refresh-token-validity:604800}")
    private Long refreshTokenValidity; // 秒 (7天)

    /**
     * 生成访问令牌
     */
    public String generateAccessToken(Long userId, String username, String... roles) {
        return generateAccessToken(userId, username, null, null, null, roles);
    }

    /**
     * 生成访问令牌（携带完整用户上下文）
     */
    public String generateAccessToken(Long userId, String username, String realName, Long orgId, Long tenantId, String... roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", "access");

        if (realName != null) {
            claims.put("realName", realName);
        }
        if (orgId != null) {
            claims.put("orgId", orgId);
        }
        if (tenantId != null) {
            claims.put("tenantId", tenantId);
        }

        if (roles != null && roles.length > 0) {
            claims.put("roles", String.join(",", roles));
        }

        return generateToken(claims, accessTokenValidity);
    }

    /**
     * 生成刷新令牌
     */
    public String generateRefreshToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");

        return generateToken(claims, refreshTokenValidity);
    }

    /**
     * 生成 Token
     */
    private String generateToken(Map<String, Object> claims, long validity) {
        Date now = new Date();
        Date validityDate = new Date(now.getTime() + validity * 1000);

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(validityDate)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期");
            throw e;
        } catch (JwtException e) {
            log.warn("Token解析失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Token 中获取用户ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从 Token 中获取用户名
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 从 Token 中获取角色
     */
    public String getRoles(String token) {
        Claims claims = parseToken(token);
        return claims.get("roles", String.class);
    }

    /**
     * 获取 Token 类型
     */
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("type", String.class);
    }

    /**
     * 判断是否为访问令牌
     */
    public boolean isAccessToken(String token) {
        return "access".equals(getTokenType(token));
    }

    /**
     * 判断是否为刷新令牌
     */
    public boolean isRefreshToken(String token) {
        return "refresh".equals(getTokenType(token));
    }

    /**
     * 获取访问令牌有效期（秒）
     */
    public Long getAccessTokenValidity() {
        return accessTokenValidity;
    }

    /**
     * 获取刷新令牌有效期（秒）
     */
    public Long getRefreshTokenValidity() {
        return refreshTokenValidity;
    }
}
