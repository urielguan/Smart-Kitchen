package com.xykj.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    @Pattern(regexp = "^$|1[3-9]\\d{9}", message = "手机号格式不正确")
    private String phone;

    private Integer gender;

    @Size(max = 500, message = "头像URL长度不能超过500个字符")
    private String avatarUrl;
}
