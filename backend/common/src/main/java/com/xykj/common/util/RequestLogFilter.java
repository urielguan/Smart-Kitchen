package com.xykj.common.util;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * 请求日志过滤器
 */
@Slf4j
@Component
public class RequestLogFilter implements Filter {

    /** 静态资源路径前缀，仅异常时记录日志 */
    private static final Set<String> SKIP_LOG_PREFIXES = Set.of(
            "/hls/", "/recordings/", "/clips/", "/screenshots/"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        httpResponse.setHeader("X-Request-Id", requestId);

        boolean skipLog = shouldSkipLog(httpRequest.getRequestURI());

        long startTime = System.currentTimeMillis();

        try {
            if (!skipLog) {
                log.info("[{}] {} {} from {}",
                        requestId,
                        httpRequest.getMethod(),
                        httpRequest.getRequestURI(),
                        httpRequest.getRemoteAddr());
            }

            chain.doFilter(request, response);
        } finally {
            int status = httpResponse.getStatus();
            boolean isError = status >= 400;

            if (skipLog) {
                if (isError) {
                    log.warn("[{}] {} {} - {} - {}ms",
                            requestId,
                            httpRequest.getMethod(),
                            httpRequest.getRequestURI(),
                            status,
                            System.currentTimeMillis() - startTime);
                }
            } else {
                long duration = System.currentTimeMillis() - startTime;
                log.info("[{}] {} {} - {} - {}ms",
                        requestId,
                        httpRequest.getMethod(),
                        httpRequest.getRequestURI(),
                        status,
                        duration);
            }
        }
    }

    private boolean shouldSkipLog(String uri) {
        if (uri == null) return false;
        for (String prefix : SKIP_LOG_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        return false;
    }
}
