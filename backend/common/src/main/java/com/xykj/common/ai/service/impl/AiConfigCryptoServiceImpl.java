package com.xykj.common.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.xykj.common.ai.config.AiProperties;
import com.xykj.common.ai.service.AiConfigCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class AiConfigCryptoServiceImpl implements AiConfigCryptoService {

    private final AiProperties properties;

    private AES buildAes() {
        byte[] keyBytes = Arrays.copyOf(properties.getCryptoKey().getBytes(StandardCharsets.UTF_8), 16);
        return SecureUtil.aes(keyBytes);
    }

    @Override
    public String encrypt(String plainText) {
        if (StrUtil.isBlank(plainText)) {
            return plainText;
        }
        return buildAes().encryptBase64(plainText, StandardCharsets.UTF_8);
    }

    @Override
    public String decrypt(String cipherText) {
        if (StrUtil.isBlank(cipherText)) {
            return cipherText;
        }
        return buildAes().decryptStr(cipherText, StandardCharsets.UTF_8);
    }

    @Override
    public String mask(String plainText) {
        if (StrUtil.isBlank(plainText)) {
            return "";
        }
        if (plainText.length() <= 8) {
            return "****";
        }
        return plainText.substring(0, 4) + "****" + plainText.substring(plainText.length() - 4);
    }
}
