package com.xykj.common.enums;

import lombok.Getter;

/**
 * 响应码枚举
 * 对应API文档中的业务状态码
 */
@Getter
public enum ResultCode {

    // 成功
    SUCCESS("SUCCESS", "操作成功"),

    // 客户端错误 4xx
    BAD_REQUEST("BAD_REQUEST", "参数错误"),
    UNAUTHORIZED("UNAUTHORIZED", "未登录或Token无效"),
    FORBIDDEN("FORBIDDEN", "无权限"),
    NOT_FOUND("NOT_FOUND", "资源不存在"),
    CONFLICT("CONFLICT", "状态冲突"),
    VALIDATION_FAILED("VALIDATION_FAILED", "业务校验失败"),

    // 服务端错误 5xx
    INTERNAL_ERROR("INTERNAL_ERROR", "系统异常"),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "服务暂时不可用"),

    // 认证相关
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Token已过期"),
    TOKEN_INVALID("TOKEN_INVALID", "Token无效"),
    USERNAME_OR_PASSWORD_ERROR("USERNAME_OR_PASSWORD_ERROR", "用户名或密码错误"),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "账号已被锁定，请15分钟后重试"),
    ACCOUNT_DISABLED("ACCOUNT_DISABLED", "账号已被禁用，请联系管理员"),
    LOGIN_FREQUENT("LOGIN_FREQUENT", "登录频率过高，请稍后再试"),
    REFRESH_TOKEN_INVALID("REFRESH_TOKEN_INVALID", "刷新令牌无效或已过期"),

    // 权限相关
    DATA_SCOPE_DENIED("DATA_SCOPE_DENIED", "数据权限不足"),

    // 密码相关
    PASSWORD_ERROR("PASSWORD_ERROR", "原密码错误"),
    PASSWORD_SAME_AS_OLD("PASSWORD_SAME_AS_OLD", "新密码不能与原密码相同"),
    PASSWORD_NOT_MATCH("PASSWORD_NOT_MATCH", "两次输入的密码不一致"),

    // 组织相关
    ORGANIZATION_NOT_FOUND("ORGANIZATION_NOT_FOUND", "组织不存在"),
    ORGANIZATION_NAME_DUPLICATE("ORGANIZATION_NAME_DUPLICATE", "组织名称已存在"),
    ORGANIZATION_HAS_MEMBERS("ORGANIZATION_HAS_MEMBERS", "组织下存在成员，无法删除"),
    ORGANIZATION_HAS_CHILDREN("ORGANIZATION_HAS_CHILDREN", "组织下存在子组织，无法删除"),

    // 员工相关
    EMPLOYEE_NOT_FOUND("EMPLOYEE_NOT_FOUND", "员工不存在"),
    EMPLOYEE_NO_DUPLICATE("EMPLOYEE_NO_DUPLICATE", "员工工号已存在"),
    PHONE_USED("PHONE_USED", "手机号已被其他员工使用"),

    // 供应商相关
    SUPPLIER_NOT_FOUND("SUPPLIER_NOT_FOUND", "供应商不存在"),
    SUPPLIER_CODE_DUPLICATE("SUPPLIER_CODE_DUPLICATE", "供应商编码已存在"),
    SUPPLIER_CANNOT_AUDIT("SUPPLIER_CANNOT_AUDIT", "该供应商不在待审核状态"),
    SUPPLIER_HAS_ORDERS("SUPPLIER_HAS_ORDERS", "供应商存在采购订单数据，无法注销"),

    // 采购相关
    PLAN_NOT_FOUND("PLAN_NOT_FOUND", "采购计划不存在"),
    PLAN_CANNOT_SUBMIT("PLAN_CANNOT_SUBMIT", "只有草稿状态的计划可以提交"),
    PLAN_CANNOT_AUDIT("PLAN_CANNOT_AUDIT", "该计划不在待审核状态"),
    PLAN_CANNOT_DELETE("PLAN_CANNOT_DELETE", "只能删除草稿状态的采购计划"),
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "订单不存在"),

    // 强制下线
    FORCE_LOGOUT("FORCE_LOGOUT", "您已被强制下线");

    private final String code;
    private final String message;

    ResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}