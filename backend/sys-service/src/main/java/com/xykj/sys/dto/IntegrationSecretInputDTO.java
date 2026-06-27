package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IntegrationSecretInputDTO {

    @NotBlank(message = "密钥参数名不能为空")
    private String secretKey;

    private String secretValue;
}
