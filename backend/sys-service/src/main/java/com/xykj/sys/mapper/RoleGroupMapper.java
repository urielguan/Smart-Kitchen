package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.entity.RoleGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 角色分组Mapper
 */
@Mapper
public interface RoleGroupMapper extends BaseMapper<RoleGroup> {

    /**
     * 查询所有分组（按排序号排序）
     *
     * @param tenantId 租户ID
     * @return 分组列表
     */
    @Select("SELECT * FROM auth_role_group WHERE deleted = 0 ORDER BY sort_order ASC")
    List<RoleGroup> selectAll();

    @Select("SELECT * FROM auth_role_group WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY sort_order ASC")
    List<RoleGroup> selectAllByTenantId(@Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT id FROM auth_role_group WHERE deleted = 0 " +
            "<if test='tenantId != null'>AND tenant_id = #{tenantId} </if>" +
            "<if test='!admin'>" +
            "<if test='allAccess'>" +
            "AND 1 = 1 " +
            "</if>" +
            "<if test='!allAccess'>" +
            "<choose>" +
            "<when test='orgIds != null and orgIds.size() > 0'>" +
            "AND org_id IN " +
            "<foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>" +
            "#{orgId}" +
            "</foreach>" +
            "</when>" +
            "<otherwise>" +
            "AND 1 = 0 " +
            "</otherwise>" +
            "</choose>" +
            "</if>" +
            "</if>" +
            "</script>")
    Set<Long> selectVisibleGroupIdsByScope(@Param("admin") boolean admin,
                                           @Param("allAccess") boolean allAccess,
                                           @Param("orgIds") Set<Long> orgIds,
                                           @Param("tenantId") Long tenantId);

    /**
     * 查询分组下的角色数量
     *
     * @param groupId 分组ID
     * @return 角色数量
     */
    @Select("SELECT COUNT(*) FROM auth_role WHERE group_id = #{groupId} AND deleted = 0")
    Integer countRolesByGroupId(@Param("groupId") Long groupId);
}
