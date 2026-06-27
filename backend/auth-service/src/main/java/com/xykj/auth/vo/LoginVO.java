package com.xykj.auth.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    /** 是否需要强制修改密码（首次登录） */
    private Boolean mustChangePassword;
}
