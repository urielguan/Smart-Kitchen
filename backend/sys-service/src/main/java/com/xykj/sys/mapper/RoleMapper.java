package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.sys.entity.Role;
import com.xykj.sys.vo.RoleMemberVO;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色Mapper接口
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 统计角色下的成员数量
     *
     * @param roleId 角色ID
     * @return 成员数量
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM auth_user_role ur " +
            "INNER JOIN sys_employee e ON e.user_id = ur.user_id " +
            "WHERE ur.role_id = #{roleId} AND e.deleted = 0 " +
            "<if test='!allAccess'>" +
            "<choose>" +
            "<when test='orgIds != null and orgIds.size() > 0'>" +
            "AND e.org_id IN " +
            "<foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>" +
            "#{orgId}" +
            "</foreach>" +
            "</when>" +
            "<otherwise>" +
            "AND 1 = 0 " +
            "</otherwise>" +
            "</choose>" +
            "</if>" +
            "</script>")
    Integer countMembersByRoleId(@Param("roleId") Long roleId,
                                 @Param("allAccess") boolean allAccess,
                                 @Param("orgIds") Set<Long> orgIds);

    /**
     * 检查用户是否拥有该角色
     *
     * @param roleId 角色ID
     * @param userId 用户ID
     * @return 数量
     */
    @Select("SELECT COUNT(*) FROM auth_user_role WHERE role_id = #{roleId} AND user_id = #{userId}")
    Integer checkUserHasRole(@Param("roleId") Long roleId, @Param("userId") Long userId);

    /**
     * 添加角色成员
     *
     * @param roleId 角色ID
     * @param userId 用户ID
     */
    @Insert("INSERT INTO auth_user_role (role_id, user_id, created_at) VALUES (#{roleId}, #{userId}, NOW())")
    void insertRoleMember(@Param("roleId") Long roleId, @Param("userId") Long userId);

    /**
     * 删除角色成员
     *
     * @param roleId 角色ID
     * @param userId 用户ID
     */
    @Delete("DELETE FROM auth_user_role WHERE role_id = #{roleId} AND user_id = #{userId}")
    void deleteRoleMember(@Param("roleId") Long roleId, @Param("userId") Long userId);

    /**
     * 查询角色成员列表
     *
     * @param roleId 角色ID
     * @param page 分页参数
     * @return 成员列表
     */
    @Select("<script>" +
            "SELECT e.id, e.employee_no AS employeeNo, e.real_name AS realName, " +
            "o.org_name AS orgName, e.position, e.phone, e.status AS employeeStatus, u.status, " +
            "ur.created_at AS joinedAt " +
            "FROM auth_user_role ur " +
            "INNER JOIN auth_user u ON ur.user_id = u.id " +
            "INNER JOIN sys_employee e ON e.user_id = u.id " +
            "LEFT JOIN sys_organization o ON e.org_id = o.id " +
            "WHERE ur.role_id = #{roleId} AND e.deleted = 0 " +
            "<if test='!allAccess'>" +
            "<choose>" +
            "<when test='orgIds != null and orgIds.size() > 0'>" +
            "AND e.org_id IN " +
            "<foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>" +
            "#{orgId}" +
            "</foreach>" +
            "</when>" +
            "<otherwise>" +
            "AND 1 = 0 " +
            "</otherwise>" +
            "</choose>" +
            "</if>" +
            "ORDER BY ur.created_at DESC" +
            "</script>")
    List<RoleMemberVO> selectMembersByRoleId(@Param("roleId") Long roleId,
                                              @Param("allAccess") boolean allAccess,
                                              @Param("orgIds") Set<Long> orgIds,
                                              Page<RoleMemberVO> page);

    /**
     * 查询用户的所有角色ID
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    @Select("SELECT role_id FROM auth_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的所有角色信息
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Select("SELECT r.id, r.role_code, r.role_name, r.status " +
            "FROM auth_role r " +
            "INNER JOIN auth_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 'active' AND r.deleted = 0 " +
            "ORDER BY r.sort_order")
    List<Role> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 删除用户的所有角色关联
     *
     * @param userId 用户ID
     */
    @Delete("DELETE FROM auth_user_role WHERE user_id = #{userId}")
    void deleteUserRolesByUserId(@Param("userId") Long userId);

    /**
     * 查询角色已授权的权限编码
     */
    @Select("SELECT p.permission_code FROM auth_permission p " +
            "INNER JOIN auth_role_permission rp ON p.id = rp.permission_id " +
            "WHERE rp.role_id = #{roleId} AND p.status = 'active' " +
            "ORDER BY p.sort_order")
    List<String> selectPermissionCodesByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色已授权的权限ID
     */
    @Select("SELECT permission_id FROM auth_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除角色已有权限关联
     */
    @Delete("DELETE FROM auth_role_permission WHERE role_id = #{roleId}")
    void deleteRolePermissionsByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量插入角色权限关联
     */
    @Insert("<script>" +
            "INSERT INTO auth_role_permission (role_id, permission_id, created_by, created_at) VALUES " +
            "<foreach collection='permissionIds' item='permissionId' separator=','>" +
            "(#{roleId}, #{permissionId}, #{createdBy}, NOW())" +
            "</foreach>" +
            "</script>")
    void batchInsertRolePermissions(@Param("roleId") Long roleId,
                                    @Param("permissionIds") List<Long> permissionIds,
                                    @Param("createdBy") Long createdBy);

    /**
     * 批量查询权限编码与名称映射
     */
    @Select("<script>" +
            "SELECT permission_code AS permissionCode, permission_name AS permissionName " +
            "FROM auth_permission WHERE permission_code IN " +
            "<foreach collection='permissionCodes' item='code' open='(' separator=',' close=')'>" +
            "#{code}" +
            "</foreach>" +
            "</script>")
    List<Map<String, Object>> selectPermissionNameMapByCodes(@Param("permissionCodes") List<String> permissionCodes);

    /**
     * 根据权限编码查询权限ID
     */
    @Select("<script>" +
            "SELECT id FROM auth_permission WHERE permission_code IN " +
            "<foreach collection='permissionCodes' item='code' open='(' separator=',' close=')'>" +
            "#{code}" +
            "</foreach>" +
            " AND status = 'active'" +
            "</script>")
    List<Long> selectPermissionIdsByCodes(@Param("permissionCodes") List<String> permissionCodes);

    @Select("SELECT DISTINCT p.permission_code FROM auth_permission p " +
            "INNER JOIN auth_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN auth_user_role ur ON rp.role_id = ur.role_id " +
            "INNER JOIN auth_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.status = 'active' AND r.status = 'active' AND r.deleted = 0")
    List<String> selectAssignablePermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询当前用户可授的模块+菜单+按钮权限节点（按排序）
     */
    @Select("SELECT DISTINCT p.* FROM auth_permission p " +
            "INNER JOIN auth_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN auth_user_role ur ON rp.role_id = ur.role_id " +
            "INNER JOIN auth_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.status = 'active' AND r.status = 'active' AND r.deleted = 0 " +
            "AND p.permission_type IN ('module','menu','button') " +
            "ORDER BY p.sort_order, p.id")
    List<Map<String, Object>> selectAssignablePermissionNodesByUserId(@Param("userId") Long userId);

    /**
     * 查询全部模块+菜单+按钮权限节点（admin使用）
     */
    @Select("SELECT * FROM auth_permission WHERE status = 'active' AND permission_type IN ('module','menu','button') ORDER BY sort_order, id")
    List<Map<String, Object>> selectAllPermissionNodes();

    /**
     * 查询角色的数据权限组织ID列表
     */
    @Select("SELECT org_id FROM auth_role_data_scope_org WHERE role_id = #{roleId}")
    List<Long> selectDataScopeOrgIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除角色的数据权限组织关联
     */
    @Delete("DELETE FROM auth_role_data_scope_org WHERE role_id = #{roleId}")
    void deleteRoleDataScopeOrgs(@Param("roleId") Long roleId);

    /**
     * 批量新增角色的数据权限组织关联
     */
    @Insert("<script>" +
            "INSERT INTO auth_role_data_scope_org (role_id, org_id, created_by, created_at) VALUES " +
            "<foreach collection='orgIds' item='orgId' separator=','>" +
            "(#{roleId}, #{orgId}, #{createdBy}, NOW())" +
            "</foreach>" +
            "</script>")
    void batchInsertRoleDataScopeOrgs(@Param("roleId") Long roleId,
                                      @Param("orgIds") List<Long> orgIds,
                                      @Param("createdBy") Long createdBy);

    /**
     * 查询当前用户可管理组织ID（本组织及下级）
     */
    @Select("SELECT id FROM sys_organization WHERE deleted = 0 AND status = 'active' AND " +
            "(id = #{orgId} OR path LIKE CONCAT(#{orgPathPrefix}, '%'))")
    List<Long> selectManageableOrgIdsByOrg(@Param("orgId") Long orgId, @Param("orgPathPrefix") String orgPathPrefix);

    /**
     * 批量查询多个用户的角色名称（逗号分隔）
     *
     * @param userIds 用户ID列表
     * @return userId -> roleNames 映射
     */
    @Select("<script>" +
            "SELECT ur.user_id AS userId, GROUP_CONCAT(r.role_name) AS roleNames " +
            "FROM auth_user_role ur " +
            "INNER JOIN auth_role r ON ur.role_id = r.id " +
            "WHERE r.deleted = 0 AND ur.user_id IN " +
            "<foreach collection='userIds' item='uid' open='(' separator=',' close=')'>" +
            "#{uid}" +
            "</foreach>" +
            " GROUP BY ur.user_id" +
            "</script>")
    List<Map<String, Object>> selectRoleNamesByUserIds(@Param("userIds") List<Long> userIds);
}
