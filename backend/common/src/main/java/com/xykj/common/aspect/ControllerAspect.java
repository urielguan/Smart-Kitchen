package com.xykj.common.aspect;

import com.xykj.common.exception.BizException;
import com.xykj.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller 响应处理切面
 */
@Slf4j
@Aspect
@Component
public class ControllerAspect {

    /**
     * 切入点：所有 Controller 的方法（排除返回SseEmitter的方法）
     */
    @Pointcut("execution(* com.xykj..controller..*.*(..)) && !execution(org.springframework.web.servlet.mvc.method.annotation.SseEmitter com.xykj..controller..*(..))")
    public void controllerPointcut() {
    }

    /**
     * 环绕通知：处理 Controller 返回值
     */
    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            // 如果返回已经是 R，直接返回
            if (result instanceof R) {
                return result;
            }

            // SSE/流式响应不包装
            if (result instanceof SseEmitter) {
                return result;
            }

            // 包装为成功响应
            return R.ok(result);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Controller 异常: {}.{}",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            if (duration > 1000) {
                log.warn("慢请求: {}.{} - {}ms",
                        joinPoint.getTarget().getClass().getSimpleName(),
                        joinPoint.getSignature().getName(),
                        duration);
            }
        }
    }
}
