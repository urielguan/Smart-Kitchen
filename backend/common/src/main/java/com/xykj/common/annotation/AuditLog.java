package com.xykj.common.annotation;

import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 * <p>
 * 支持的 SpEL 变量：
 * - #result：方法返回值（Long 直接作为 ID；Map 可用 #result['key']）
 * - #参数名：方法参数（如 #dto、#userId、#request）
 * - #entity：查询到的实体（UPDATE/DELETE 为修改前状态，CREATE 为修改后状态）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * 审计模块
     */
    AuditModule module();

    /**
     * 操作类型
     */
    AuditOperationType operationType();

    /**
     * 目标ID的SpEL表达式
     * 示例："#result"、"#result['id']"、"#userId"
     */
    String targetId() default "";

    /**
     * 目标编号的SpEL表达式
     * 示例："#dto.materialCode"、"#entity.materialCode"
     */
    String targetNo() default "";

    /**
     * 操作描述的SpEL表达式
     * 示例："'新增物料：' + #dto.materialName + '（' + #dto.materialCode + '）'"
     */
    String desc() default "";

    /**
     * Mapper类，用于自动查询实体前后数据
     * 设置后切面会在方法执行前后通过 selectById 查询实体，
     * 自动填充 beforeData 和 afterData
     * 默认 void.class 表示不自动捕获前后数据
     */
    Class<?> mapper() default void.class;
}
