package com.xykj.auth.constant;

public class AuthConstants {

    private AuthConstants() {}

    /** Redis key: 登录失败计数 login_fail:{userId} */
    public static final String LOGIN_FAIL_KEY_PREFIX = "login_fail:";

    /** Redis key: Token黑名单 token_blacklist:{tokenHash} */
    public static final String TOKEN_BLACKLIST_KEY_PREFIX = "token_blacklist:";

    /** 登录失败最大次数 */
    public static final int LOGIN_FAIL_MAX_COUNT = 5;

    /** 登录失败计数窗口（秒）- 5分钟 */
    public static final int LOGIN_FAIL_WINDOW_SECONDS = 300;

    /** 账号锁定时长（分钟）- 15分钟 */
    public static final int ACCOUNT_LOCK_DURATION_MINUTES = 15;

    /** 请求属性：当前用户ID */
    public static final String CURRENT_USER_ID = "currentUserId";

    /** 请求属性：当前用户名 */
    public static final String CURRENT_USERNAME = "currentUsername";
}
