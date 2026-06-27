package com.xykj.auth.controller;

import com.xykj.auth.dto.LoginRequest;
import com.xykj.auth.dto.TokenRefreshRequest;
import com.xykj.auth.service.AuthService;
import com.xykj.auth.service.TokenService;
import com.xykj.auth.vo.LoginVO;
import com.xykj.auth.vo.TokenVO;
import com.xykj.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    /**
     * 用户登录
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginVO vo = authService.login(request, ip, userAgent);
        return R.ok(vo);
    }

    /**
     * 刷新Token
     * POST /api/v1/auth/token/refresh
     */
    @PostMapping("/token/refresh")
    public R<TokenVO> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        TokenVO vo = authService.refreshToken(request);
        return R.ok(vo);
    }

    /**
     * 用户登出
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        String accessToken = extractToken(request);
        authService.logout(accessToken);
        return R.ok();
    }

    /**
     * 手动清理过期token记录（测试用）
     * DELETE /api/v1/auth/tokens/expired
     */
    @DeleteMapping("/tokens/expired")
    public R<Integer> cleanExpiredTokens() {
        int count = tokenService.cleanExpiredTokens();
        return R.ok(count);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
