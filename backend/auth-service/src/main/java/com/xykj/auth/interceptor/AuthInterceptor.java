package com.xykj.auth.interceptor;

import com.xykj.auth.constant.AuthConstants;
import com.xykj.auth.service.TokenService;
import com.xykj.common.context.RequestContext;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.ResultCode;
import com.xykj.common.exception.BizException;
import com.xykj.common.service.ForceLogoutHelper;
import com.xykj.common.util.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;
    private final ForceLogoutHelper forceLogoutHelper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        String accessToken = authHeader.substring(7);

        // 检查Token是否已被撤销
        if (tokenService.isTokenRevoked(accessToken)) {
            // 进一步检查是否为强制下线，返回更友好的提示
            Long revokedUserId = extractUserIdFromToken(accessToken);
            if (revokedUserId != null) {
                String reason = forceLogoutHelper.getForceLogoutReason(revokedUserId);
                if (reason != null) {
                    throw new BizException(ResultCode.FORCE_LOGOUT, forceLogoutHelper.getReasonMessage(reason));
                }
            }
            throw new BizException(ResultCode.TOKEN_INVALID);
        }

        // 解析Token
        Claims claims;
        try {
            claims = jwtTokenProvider.parseToken(accessToken);
        } catch (ExpiredJwtException e) {
            // Token过期 — 前端可用refreshToken刷新
            throw new BizException(ResultCode.TOKEN_EXPIRED);
        } catch (Exception e) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }

        // 验证Token类型
        String type = claims.get("type", String.class);
        if (!"access".equals(type)) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }

        // 将用户信息放入请求属性
        Long userId = claims.get("userId", Long.class);
        String username = claims.get("username", String.class);
        if (userId == null || username == null || username.isBlank()) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        request.setAttribute(AuthConstants.CURRENT_USER_ID, userId);
        request.setAttribute(AuthConstants.CURRENT_USERNAME, username);

        // 填充RequestContext（IP、User-Agent）
        RequestContext reqCtx = new RequestContext();
        reqCtx.setIpAddress(getClientIp(request));
        reqCtx.setUserAgent(request.getHeader("User-Agent"));
        RequestContext.set(reqCtx);

        // 填充UserContext
        UserContext ctx = new UserContext();
        ctx.setUserId(userId);
        ctx.setUsername(username);
        ctx.setRealName(claims.get("realName", String.class));
        ctx.setOrgId(claims.get("orgId", Long.class));
        ctx.setTenantId(claims.get("tenantId", Long.class));
        UserContext.set(ctx);

        // 检查强制下线标记（token 未被撤销但用户已被禁用/锁定等情况）
        String forceReason = forceLogoutHelper.getForceLogoutReason(userId);
        if (forceReason != null) {
            throw new BizException(ResultCode.FORCE_LOGOUT, forceLogoutHelper.getReasonMessage(forceReason));
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
        RequestContext.clear();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /** 从 token 中提取 userId（用于 token 被撤销后仍需获取 userId 的场景） */
    private Long extractUserIdFromToken(String token) {
        try {
            Claims claims = jwtTokenProvider.parseToken(token);
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            // token 解析失败，尝试从 payload 直接解码
            try {
                String[] parts = token.split("\\.");
                if (parts.length == 3) {
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    var node = mapper.readTree(payload);
                    if (node.has("userId")) {
                        return node.get("userId").asLong();
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }
    }
}
