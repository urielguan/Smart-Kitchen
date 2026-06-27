package com.xykj.auth.service;

public interface PasswordService {

    /**
     * BCrypt加密密码
     */
    String encode(String rawPassword);

    /**
     * 验证密码是否匹配
     */
    boolean matches(String rawPassword, String encodedPassword);

    /**
     * 校验密码复杂度，不通过抛出BusinessException
     */
    void validatePasswordComplexity(String password);
}
