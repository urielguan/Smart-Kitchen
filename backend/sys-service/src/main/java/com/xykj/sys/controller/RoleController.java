package com.xykj.sys.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sys.dto.*;
import com.xykj.sys.service.RoleGroupService;
import com.xykj.sys.service.RoleService;
import com.xykj.sys.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理控制器
 * 包含角色分组和角色管理接口
 */
@RestController
@RequestMapping("/api/v1/sys")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final RoleGroupService roleGroupService;

    // ==================== 角色分组接口 ====================

    /**
     * 获取角色分组列表
     */
    @GetMapping("/role-groups")
    public R<List<RoleGroupVO>> listGroups() {
        List<RoleGroupVO> groups = roleGroupService.list();
        return R.ok(groups);
    }

    /**
     * 获取分组详情
     */
    @GetMapping("/role-groups/{id}")
    public R<RoleGroupVO> getGroupDetail(@PathVariable Long id) {
        RoleGroupVO group = roleGroupService.getDetail(id);
        return R.ok(group);
    }

    /**
     * 新增角色分组
     */
    @PostMapping("/role-groups")
    public R<Map<String, Object>> createGroup(@Valid @RequestBody RoleGroupCreateDTO dto) {
        Long id = roleGroupService.create(dto);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return R.ok(result);
    }

    /**
     * 编辑角色分组
     */
    @PutMapping("/role-groups/{id}")
    public R<Void> updateGroup(@PathVariable Long id, @Valid @RequestBody RoleGroupUpdateDTO dto) {
        dto.setId(id);
        roleGroupService.update(dto);
        return R.ok();
    }

    /**
     * 删除角色分组
     */
    @DeleteMapping("/role-groups/{id}")
    public R<Void> deleteGroup(@PathVariable Long id) {
        roleGroupService.delete(id);
        return R.ok();
    }

    // ==================== 角色接口 ====================

    /**
     * 获取角色列表
     */
    @GetMapping("/roles")
    public R<List<RoleVO>> listRoles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String status) {
        List<RoleVO> roles = roleService.list(keyword, groupId, status);
        return R.ok(roles);
    }

    /**
     * 获取角色详情
     */
    @GetMapping("/roles/{id}")
    public R<RoleDetailVO> getRoleDetail(@PathVariable Long id) {
        RoleDetailVO role = roleService.getDetail(id);
        return R.ok(role);
    }

    /**
     * 获取当前用户可授权限树（菜单+按钮）
     */
    @GetMapping("/roles/permission-tree")
    public R<List<Map<String, Object>>> getAssignablePermissionTree() {
        return R.ok(roleService.getAssignablePermissionTree());
    }

    /**
     * 新增角色
     */
    @PostMapping("/roles")
    public R<Map<String, Object>> createRole(@Valid @RequestBody RoleCreateDTO dto) {
        Long id = roleService.create(dto);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return R.ok(result);
    }

    /**
     * 编辑角色
     */
    @PutMapping("/roles/{id}")
    public R<Void> updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateDTO dto) {
        dto.setId(id);
        roleService.update(dto);
        return R.ok();
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/roles/{id}")
    public R<Void> deleteRole(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    // ==================== 角色成员接口 ====================

    /**
     * 获取角色成员列表
     */
    @GetMapping("/roles/{roleId}/members")
    public R<PageResult<RoleMemberVO>> getRoleMembers(
            @PathVariable Long roleId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<RoleMemberVO> result = roleService.getMembers(roleId, pageNum, pageSize);
        return R.ok(result);
    }

    /**
     * 添加角色成员
     */
    @PostMapping("/roles/{roleId}/members")
    public R<Map<String, Object>> addRoleMembers(
            @PathVariable Long roleId,
            @Valid @RequestBody RoleMemberDTO dto) {
        Integer addedCount = roleService.addMembers(roleId, dto);

        // 获取当前成员总数
        PageResult<RoleMemberVO> members = roleService.getMembers(roleId, 1, 1);

        Map<String, Object> result = new HashMap<>();
        result.put("roleId", roleId);
        result.put("addedCount", addedCount);
        result.put("memberCount", members.getTotal());
        return R.ok(result);
    }

    /**
     * 批量移除角色成员
     */
    @DeleteMapping("/roles/{roleId}/members/batch")
    public R<Map<String, Object>> batchRemoveRoleMembers(
            @PathVariable Long roleId,
            @Valid @RequestBody RoleMemberDTO dto) {
        Integer removedCount = roleService.batchRemoveMembers(roleId, dto);

        PageResult<RoleMemberVO> members = roleService.getMembers(roleId, 1, 1);

        Map<String, Object> result = new HashMap<>();
        result.put("roleId", roleId);
        result.put("removedCount", removedCount);
        result.put("memberCount", members.getTotal());
        return R.ok(result);
    }

    /**
     * 移除角色成员
     */
    @DeleteMapping("/roles/{roleId}/members/{employeeId}")
    public R<Map<String, Object>> removeRoleMember(
            @PathVariable Long roleId,
            @PathVariable Long employeeId) {
        roleService.removeMember(roleId, employeeId);

        // 获取当前成员总数
        PageResult<RoleMemberVO> members = roleService.getMembers(roleId, 1, 1);

        Map<String, Object> result = new HashMap<>();
        result.put("roleId", roleId);
        result.put("employeeId", employeeId);
        result.put("memberCount", members.getTotal());
        return R.ok(result);
    }
}
