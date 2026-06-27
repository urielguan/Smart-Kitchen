package com.xykj.common.aspect;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 审计日志AOP切面
 * <p>
 * 拦截带有 @AuditLog 注解的方法，在方法正常返回后记录审计日志。
 * 支持通过 mapper 属性自动捕获操作前后数据（beforeData/afterData）。
 * <p>
 * SpEL 变量：
 * - #result：方法返回值
 * - #参数名：方法参数（如 #dto、#userId）
 * - #entity：查询到的实体（UPDATE/DELETE=修改前状态，CREATE=修改后状态）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ApplicationContext applicationContext;

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        // 构建 SpEL 上下文
        EvaluationContext context = new StandardEvaluationContext();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        // 获取 Mapper（如果配置了）
        BaseMapper<?> mapper = null;
        if (auditLog.mapper() != void.class) {
            try {
                mapper = (BaseMapper<?>) applicationContext.getBean(auditLog.mapper());
            } catch (Exception e) {
                log.warn("审计日志：获取Mapper失败: {}", e.getMessage());
            }
        }

        // 捕获 beforeData（UPDATE/DELETE/STATUS_CHANGE）
        String beforeData = null;
        Long targetId = null;
        AuditOperationType opType = auditLog.operationType();
        boolean isCreate = opType == AuditOperationType.CREATE;
        boolean isDelete = opType == AuditOperationType.DELETE;

        // 尝试在方法执行前解析 targetId（适用于 UPDATE/DELETE，targetId 来自参数如 #id）
        if (mapper != null && !isCreate) {
            targetId = tryParseTargetId(auditLog.targetId(), context);
            if (targetId != null) {
                Object beforeEntity = mapper.selectById(targetId);
                if (beforeEntity != null) {
                    beforeData = JSONUtil.toJsonStr(beforeEntity);
                    context.setVariable("entity", beforeEntity);
                }
            }
        }

        // 执行业务方法（try-catch-finally 确保失败也记录日志）
        Object result = null;
        String operationResult = "success";
        String errorMsg = null;

        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            operationResult = "failure";
            errorMsg = ex.getMessage();
            throw ex;
        } finally {
            // 设置返回值（成功时）
            if (result != null) {
                context.setVariable("result", result);
            }

            // 捕获 afterData（仅成功时）
            String afterData = null;
            if ("success".equals(operationResult) && mapper != null) {
                if (isCreate) {
                    targetId = tryParseTargetId(auditLog.targetId(), context);
                }
                if (targetId != null && !isDelete) {
                    Object afterEntity = mapper.selectById(targetId);
                    if (afterEntity != null) {
                        afterData = JSONUtil.toJsonStr(afterEntity);
                        if (isCreate) {
                            context.setVariable("entity", afterEntity);
                        }
                    }
                }
            }

            // 尝试解析 targetId（失败时从参数解析）
            if (targetId == null) {
                targetId = tryParseTargetId(auditLog.targetId(), context);
            }

            // 解析其他 SpEL 表达式
            String targetNo = null;
            String desc = null;
            try {
                targetNo = parseString(auditLog.targetNo(), context);
                desc = parseString(auditLog.desc(), context);
            } catch (Exception e) {
                log.warn("审计日志：解析SpEL失败: {}", e.getMessage());
            }

            // 记录审计日志
            try {
                auditLogService.log(auditLog.module(), opType, targetId, targetNo, desc,
                    beforeData, afterData, operationResult, errorMsg);
            } catch (Exception logEx) {
                log.error("审计日志记录失败: {}", logEx.getMessage());
            }
        }

        return result;
    }

    private Long tryParseTargetId(String expression, EvaluationContext context) {
        try {
            return parseTargetId(expression, context);
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseTargetId(String expression, EvaluationContext context) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        Object value = PARSER.parseExpression(expression).getValue(context);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String parseString(String expression, EvaluationContext context) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        Object value = PARSER.parseExpression(expression).getValue(context);
        return value != null ? value.toString() : null;
    }
}
