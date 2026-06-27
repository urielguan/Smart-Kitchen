package com.xykj.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.context.RequestContext;
import com.xykj.common.context.UserContext;
import com.xykj.common.service.ForceLogoutHelper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 用户上下文拦截器 - 从JWT Token中提取用户信息并存入ThreadLocal
 * 适用于非auth-service的各业务服务
 */
@Slf4j
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Value("${jwt.secret:yingzi-smart-kitchen-secret-key-for-jwt-token-generation-2024}")
    private String jwtSecret;

    @Autowired(required = false)
    private ForceLogoutHelper forceLogoutHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");

        // 填充RequestContext（IP、User-Agent、来源终端）
        RequestContext reqCtx = new RequestContext();
        reqCtx.setIpAddress(getClientIp(request));
        reqCtx.setUserAgent(request.getHeader("User-Agent"));
        reqCtx.setSourceTerminal(request.getHeader("X-Source-Terminal"));
        RequestContext.set(reqCtx);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // 验证token类型为access
                String type = claims.get("type", String.class);
                if (!"access".equals(type)) {
                    return true;
                }

                Long userId = claims.get("userId", Long.class);

                // 检查强制下线标记
                if (forceLogoutHelper != null && userId != null) {
                    String reason = forceLogoutHelper.getForceLogoutReason(userId);
                    if (reason != null) {
                        UserContext.clear();
                        RequestContext.clear();
                        String message = forceLogoutHelper.getReasonMessage(reason);
                        writeForceLogoutResponse(response, message);
                        return false;
                    }
                }

                UserContext ctx = new UserContext();
                ctx.setUserId(userId);
                ctx.setUsername(claims.get("username", String.class));
                ctx.setRealName(claims.get("realName", String.class));
                ctx.setOrgId(claims.get("orgId", Long.class));
                ctx.setTenantId(claims.get("tenantId", Long.class));
                UserContext.set(ctx);
            } catch (Exception e) {
                log.debug("解析JWT失败，不填充UserContext: {}", e.getMessage());
            }
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

    private void writeForceLogoutResponse(HttpServletResponse response, String message) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            Map<String, Object> body = Map.of(
                    "code", "FORCE_LOGOUT",
                    "message", message,
                    "data", ""
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
            response.getWriter().flush();
        } catch (IOException e) {
            log.error("写入强制下线响应失败", e);
        }
    }
}
