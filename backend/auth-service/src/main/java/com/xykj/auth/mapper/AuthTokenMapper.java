package com.xykj.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.auth.entity.AuthToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthTokenMapper extends BaseMapper<AuthToken> {

    /**
     * 根据refreshToken查询有效令牌
     */
    AuthToken selectByRefreshToken(@Param("refreshToken") String refreshToken);

    /**
     * 根据accessToken查询令牌（含已撤销的，用于校验）
     */
    AuthToken selectByAccessToken(@Param("accessToken") String accessToken);

    /**
     * 撤销用户所有有效令牌
     */
    int revokeAllByUserId(@Param("userId") Long userId);
}
