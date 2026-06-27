package com.xykj.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRefreshRequest {

    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
