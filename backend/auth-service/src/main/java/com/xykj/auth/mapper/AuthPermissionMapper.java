package com.xykj.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.auth.entity.AuthPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthPermissionMapper extends BaseMapper<AuthPermission> {

    /**
     * 根据用户ID查询权限列表（通过角色关联）
     */
    List<AuthPermission> selectByUserId(@Param("userId") Long userId);
}
