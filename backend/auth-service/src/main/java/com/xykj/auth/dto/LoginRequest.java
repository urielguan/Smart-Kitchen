package com.xykj.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 登录方式：password/sso/qrcode */
    private String loginType;

    /** 设备唯一标识 */
    private String deviceId;

    /** 设备类型：web/mobile/tablet/terminal */
    private String deviceType;
}
