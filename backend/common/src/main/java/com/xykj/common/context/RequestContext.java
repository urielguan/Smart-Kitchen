package com.xykj.common.context;

/**
 * 请求上下文 - 基于ThreadLocal存储当前请求的IP和User-Agent
 */
public class RequestContext {

    private static final ThreadLocal<RequestContext> THREAD_LOCAL = new ThreadLocal<>();

    private String ipAddress;
    private String userAgent;
    private String sourceTerminal;

    public static void set(RequestContext context) {
        THREAD_LOCAL.set(context);
    }

    public static RequestContext get() {
        return THREAD_LOCAL.get();
    }

    public static void clear() {
        THREAD_LOCAL.remove();
    }

    public static String getIpAddress() {
        RequestContext ctx = get();
        return ctx != null ? ctx.ipAddress : null;
    }

    public static String getUserAgent() {
        RequestContext ctx = get();
        return ctx != null ? ctx.userAgent : null;
    }

    public static String getSourceTerminal() {
        RequestContext ctx = get();
        return ctx != null ? ctx.sourceTerminal : null;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public void setSourceTerminal(String sourceTerminal) {
        this.sourceTerminal = sourceTerminal;
    }
}
