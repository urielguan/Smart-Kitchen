package com.xykj.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("auth_token")
public class AuthToken implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String accessToken;

    private String refreshToken;

    private LocalDateTime accessTokenExpiresAt;

    private LocalDateTime refreshTokenExpiresAt;

    private String deviceType;

    private String deviceId;

    private String ipAddress;

    private String userAgent;

    private Integer isRevoked;

    private LocalDateTime revokedAt;

    private String revokedReason;

    private LocalDateTime createdAt;
}
