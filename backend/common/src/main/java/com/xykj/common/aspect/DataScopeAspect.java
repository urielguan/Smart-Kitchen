package com.xykj.common.aspect;

import com.xykj.common.annotation.DataScope;
import com.xykj.common.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据权限切面。
 *
 * 对标注 @DataScope 的查询方法，在执行前将当前用户可访问组织范围注入查询DTO。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final DataScopeService dataScopeService;

    @Before("@annotation(dataScope)")
    public void injectDataScope(JoinPoint joinPoint, DataScope dataScope) {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        for (Object arg : joinPoint.getArgs()) {
            if (arg == null) {
                continue;
            }
            applyScope(arg, scope);
        }
    }

    private void applyScope(Object target, DataScopeService.DataScopeResult scope) {
        Method getOrgId = findMethod(target.getClass(), "getOrgId");
        Method setOrgId = findMethod(target.getClass(), "setOrgId", Long.class);
        Method setOrgIds = findMethod(target.getClass(), "setOrgIds", List.class);
        if (getOrgId == null || setOrgId == null || setOrgIds == null) {
            return;
        }

        try {
            Long requestedOrgId = (Long) getOrgId.invoke(target);
            if (scope.isAllAccess()) {
                setOrgIds.invoke(target, (Object) null);
                return;
            }

            if (requestedOrgId != null) {
                if (scope.isAllowed(requestedOrgId)) {
                    setOrgIds.invoke(target, (Object) null);
                } else {
                    setOrgId.invoke(target, (Object) null);
                    setOrgIds.invoke(target, (Object) new ArrayList<Long>());
                }
                return;
            }

            setOrgIds.invoke(target, new ArrayList<>(scope.getOrgIds()));
        } catch (Exception ex) {
            log.warn("注入数据权限失败: targetClass={}", target.getClass().getName(), ex);
        }
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }
}
