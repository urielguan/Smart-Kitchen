package com.xykj.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.auth.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUser> {

    /**
     * 根据用户名查询用户（含软删除过滤）
     */
    AuthUser selectByUsername(@Param("username") String username);

    /**
     * 根据组织ID查询组织名称
     */
    String selectOrgNameById(@Param("orgId") Long orgId);

    /**
     * 根据用户ID更新 sys_employee 表的个人信息字段
     */
    int updateEmployeeByUserId(@Param("userId") Long userId, @Param("email") String email,
                               @Param("phone") String phone, @Param("gender") Integer gender,
                               @Param("avatarUrl") String avatarUrl,
                               @Param("updatedBy") Long updatedBy);

    @Select("SELECT COUNT(*) FROM auth_user WHERE phone = #{phone} AND deleted = 0 AND id != #{excludeId}")
    Long countByPhoneExcludeId(@Param("phone") String phone, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM auth_user WHERE email = #{email} AND deleted = 0 AND id != #{excludeId}")
    Long countByEmailExcludeId(@Param("email") String email, @Param("excludeId") Long excludeId);
}
