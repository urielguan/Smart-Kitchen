package com.xykj.sys.vo;

import lombok.Data;

@Data
public class IntegrationSecretMaskedVO {

    private String secretKey;
    private String secretMask;
    private Integer encryptedFlag;
}
