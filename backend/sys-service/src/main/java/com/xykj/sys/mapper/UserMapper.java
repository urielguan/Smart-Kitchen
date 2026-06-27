package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 统计指定用户名数量（排除指定ID）
     *
     * @param username 用户名
     * @param excludeId 排除的用户ID
     * @return 数量
     */
    @Select("SELECT COUNT(*) FROM auth_user WHERE username = #{username} AND deleted = 0 AND (#{excludeId} IS NULL OR id != #{excludeId})")
    Long countByUsernameExcludeId(@Param("username") String username, @Param("excludeId") Long excludeId);

    /**
     * 统计指定手机号数量（排除指定ID）
     */
    @Select("SELECT COUNT(*) FROM auth_user WHERE phone = #{phone} AND deleted = 0 AND (#{excludeId} IS NULL OR id != #{excludeId})")
    Long countByPhoneExcludeId(@Param("phone") String phone, @Param("excludeId") Long excludeId);

    /**
     * 统计指定邮箱数量（排除指定ID）
     */
    @Select("SELECT COUNT(*) FROM auth_user WHERE email = #{email} AND deleted = 0 AND (#{excludeId} IS NULL OR id != #{excludeId})")
    Long countByEmailExcludeId(@Param("email") String email, @Param("excludeId") Long excludeId);

    /**
     * 根据账号状态查询用户ID列表
     */
    @Select("SELECT id FROM auth_user WHERE status = #{status} AND deleted = 0")
    List<Long> selectIdsByStatus(@Param("status") String status);
}
