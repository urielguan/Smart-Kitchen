package com.xykj.sys.service;

import com.xykj.common.result.PageResult;
import com.xykj.sys.dto.RoleCreateDTO;
import com.xykj.sys.dto.RoleMemberDTO;
import com.xykj.sys.dto.RoleUpdateDTO;
import com.xykj.sys.vo.RoleDetailVO;
import com.xykj.sys.vo.RoleMemberVO;
import com.xykj.sys.vo.RoleVO;

import java.util.List;
import java.util.Map;

/**
 * 角色服务接口
 */
public interface RoleService {

    /**
     * 获取角色列表
     *
     * @param keyword 关键字（角色名称/编码）
     * @param groupId 分组ID
     * @param status 状态
     * @return 角色列表
     */
    List<RoleVO> list(String keyword, Long groupId, String status);

    /**
     * 获取角色详情
     *
     * @param id 角色ID
     * @return 角色详情
     */
    RoleDetailVO getDetail(Long id);

    /**
     * 新增角色
     *
     * @param dto 创建DTO
     * @return 角色ID
     */
    Long create(RoleCreateDTO dto);

    /**
     * 更新角色
     *
     * @param dto 更新DTO
     */
    void update(RoleUpdateDTO dto);

    /**
     * 删除角色
     *
     * @param id 角色ID
     */
    void delete(Long id);

    /**
     * 获取角色成员列表
     *
     * @param roleId 角色ID
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 成员分页列表
     */
    PageResult<RoleMemberVO> getMembers(Long roleId, Integer pageNum, Integer pageSize);

    /**
     * 添加角色成员
     *
     * @param roleId 角色ID
     * @param dto 成员DTO
     * @return 添加成功数量
     */
    Integer addMembers(Long roleId, RoleMemberDTO dto);

    /**
     * 移除角色成员
     *
     * @param roleId 角色ID
     * @param employeeId 员工ID
     */
    void removeMember(Long roleId, Long employeeId);

    /**
     * 批量移除角色成员
     *
     * @param roleId 角色ID
     * @param dto 包含要移除的员工ID列表
     * @return 实际移除数量
     */
    Integer batchRemoveMembers(Long roleId, RoleMemberDTO dto);

    /**
     * 获取当前用户可授权限树
     */
    List<Map<String, Object>> getAssignablePermissionTree();

}
