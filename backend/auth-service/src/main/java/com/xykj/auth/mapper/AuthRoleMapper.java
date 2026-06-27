package com.xykj.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.auth.entity.AuthRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthRoleMapper extends BaseMapper<AuthRole> {

    /**
     * 根据用户ID查询角色列表
     */
    List<AuthRole> selectByUserId(@Param("userId") Long userId);
}
