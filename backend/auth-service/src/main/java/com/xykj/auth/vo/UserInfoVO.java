package com.xykj.auth.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserInfoVO {

    private Long userId;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String avatarUrl;
    private Integer gender;
    private Long orgId;
    private String orgName;
    private Long tenantId;
    private String status;
    private List<RoleVO> roles;
    private List<PermissionVO> permissions;
    private LocalDateTime lastLoginAt;
}
