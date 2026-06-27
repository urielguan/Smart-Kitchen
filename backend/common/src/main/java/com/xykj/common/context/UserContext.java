package com.xykj.common.context;

/**
 * 用户上下文 - 基于ThreadLocal存储当前登录用户信息
 * 在拦截器中设置，在MyMetaObjectHandler和Service中使用
 */
public class UserContext {

    private static final ThreadLocal<UserContext> THREAD_LOCAL = new ThreadLocal<>();

    private Long userId;
    private String username;
    private String realName;
    private Long orgId;
    private Long tenantId;

    public static void set(UserContext context) {
        THREAD_LOCAL.set(context);
    }

    public static UserContext get() {
        return THREAD_LOCAL.get();
    }

    public static void clear() {
        THREAD_LOCAL.remove();
    }

    public static Long getUserId() {
        UserContext ctx = get();
        return ctx != null ? ctx.userId : null;
    }

    public static String getUsername() {
        UserContext ctx = get();
        return ctx != null ? ctx.username : null;
    }

    public static String getRealName() {
        UserContext ctx = get();
        return ctx != null ? ctx.realName : null;
    }

    public static Long getOrgId() {
        UserContext ctx = get();
        return ctx != null ? ctx.orgId : null;
    }

    public static Long getTenantId() {
        UserContext ctx = get();
        return ctx != null ? ctx.tenantId : null;
    }

    // ==================== Setter ====================

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
