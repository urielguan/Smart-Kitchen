package com.xykj.common.ai.service;

public interface AiConfigCryptoService {

    String encrypt(String plainText);

    String decrypt(String cipherText);

    String mask(String plainText);
}
