package com.xykj.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("auth_user")
public class AuthUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String email;

    private String phone;

    private String realName;

    private String avatarUrl;

    private Integer gender;

    private String status;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private Integer loginFailCount;

    private LocalDateTime lockedUntil;

    private Long orgId;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 是否已修改过密码 0-否 1-是 */
    private Integer passwordChanged;

    @TableLogic
    private Integer deleted;
}
