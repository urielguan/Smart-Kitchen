package com.xykj.auth.service.impl;

import com.xykj.auth.service.PasswordService;
import com.xykj.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
public class PasswordServiceImpl implements PasswordService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private static final Pattern UPPER_CASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWER_CASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    @Override
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 8 || password.length() > 20) {
            throw new BizException("密码长度必须为8-20位");
        }
        if (!UPPER_CASE.matcher(password).find()) {
            throw new BizException("密码必须包含至少一个大写字母");
        }
        if (!LOWER_CASE.matcher(password).find()) {
            throw new BizException("密码必须包含至少一个小写字母");
        }
        if (!DIGIT.matcher(password).find()) {
            throw new BizException("密码必须包含至少一个数字");
        }
        if (!SPECIAL_CHAR.matcher(password).find()) {
            throw new BizException("密码必须包含至少一个特殊符号");
        }
    }
}
