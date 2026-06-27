package com.xykj.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.DataScopeService;
import com.xykj.sys.dto.RoleCreateDTO;
import com.xykj.sys.dto.RoleMemberDTO;
import com.xykj.sys.dto.RoleUpdateDTO;
import com.xykj.sys.entity.Employee;
import com.xykj.sys.entity.Organization;
import com.xykj.sys.entity.Role;
import com.xykj.sys.entity.RoleGroup;
import com.xykj.sys.mapper.EmployeeMapper;
import com.xykj.sys.mapper.OrganizationMapper;
import com.xykj.sys.mapper.RoleGroupMapper;
import com.xykj.sys.mapper.RoleMapper;
import com.xykj.sys.service.RoleService;
import com.xykj.sys.vo.RoleDetailVO;
import com.xykj.sys.vo.RoleMemberVO;
import com.xykj.sys.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色服务实现类
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private static final String DATA_SCOPE_ALL = "all";
    private static final String DATA_SCOPE_CUSTOM = "custom";
    private static final String UNAUTHORIZED_GROUP_NAME = "未授权分组";
    private static final Set<String> DATA_SCOPE_ALLOWED = Set.of(
            DATA_SCOPE_ALL,
            DATA_SCOPE_CUSTOM,
            "dept",
            "dept_and_child",
            "self"
    );

    private final RoleMapper roleMapper;
    private final RoleGroupMapper roleGroupMapper;
    private final EmployeeMapper employeeMapper;
    private final OrganizationMapper organizationMapper;
    private final DataScopeService dataScopeService;

    @Override
    public List<RoleVO> list(String keyword, Long groupId, String status) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getDeleted, 0);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Role::getRoleName, keyword)
                    .or().like(Role::getRoleCode, keyword));
        }
        if (groupId != null) {
            wrapper.eq(Role::getGroupId, groupId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Role::getStatus, status);
        }

        wrapper.orderByAsc(Role::getSortOrder).orderByDesc(Role::getCreatedAt);

        List<Role> roles = roleMapper.selectList(wrapper);
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllAccess()) {
            Set<Long> allowedOrgIds = scope.getOrgIds();
            roles = roles.stream()
                    .filter(role -> allowedOrgIds.contains(role.getOrgId()))
                    .collect(Collectors.toList());
        }

        Set<Long> visibleGroupIds = roleGroupMapper.selectVisibleGroupIdsByScope(
                isAdminUser(),
                scope.isAllAccess(),
                scope.getOrgIds(),
                UserContext.getTenantId()
        );
        return roles.stream().map(role -> convertToVO(role, visibleGroupIds, scope)).collect(Collectors.toList());
    }

    @Override
    public RoleDetailVO getDetail(Long id) {
        Role role = getAccessibleRole(id);
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        Set<Long> visibleGroupIds = roleGroupMapper.selectVisibleGroupIdsByScope(
                isAdminUser(),
                scope.isAllAccess(),
                scope.getOrgIds(),
                UserContext.getTenantId()
        );
        return convertToDetailVO(role, visibleGroupIds, scope);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ROLE,
        operationType = AuditOperationType.CREATE,
        targetId = "#result",
        targetNo = "#dto.roleCode",
        desc = "'新增角色：' + #dto.roleName + '（' + #dto.roleCode + '）'",
        mapper = RoleMapper.class
    )
    public Long create(RoleCreateDTO dto) {
        // 校验角色编码唯一性
        String roleCode = dto.getRoleCode() != null ? dto.getRoleCode().trim() : null;
        Long tenantId = UserContext.getTenantId();
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, roleCode)
                .eq(Role::getDeleted, 0);
        if (tenantId != null) {
            wrapper.eq(Role::getTenantId, tenantId);
        }
        if (roleMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("角色编码已存在");
        }

        RoleGroup group = requireAccessibleRoleGroup(dto.getGroupId());

        String dataScope = normalizeDataScope(dto.getDataScope());
        validateAssignablePermissions(dto.getFuncPermissions());
        validateAssignableOrgIds(dataScope, dto.getDataScopeOrgIds());

        Role role = new Role();
        BeanUtils.copyProperties(dto, role);
        if (dto.getRemark() != null) {
            role.setRoleDesc(dto.getRemark());
        }
        role.setDataScope(dataScope);
        role.setTenantId(UserContext.getTenantId());
        role.setGroupId(group.getId());
        if (role.getOrgId() == null) {
            Long orgId = UserContext.getOrgId();
            role.setOrgId(orgId != null ? orgId : 0L);
        }

        roleMapper.insert(role);
        saveRolePermissions(role.getId(), dto.getFuncPermissions());
        saveRoleDataScopeOrgs(role.getId(), dataScope, dto.getDataScopeOrgIds());
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ROLE,
        operationType = AuditOperationType.UPDATE,
        targetId = "#dto.id",
        targetNo = "#entity.roleCode",
        desc = "'编辑角色：' + #entity.roleName + '（' + #entity.roleCode + '）'",
        mapper = RoleMapper.class
    )
    public void update(RoleUpdateDTO dto) {
        Role role = getAccessibleRole(dto.getId());

        String newRoleCode = dto.getRoleCode() != null ? dto.getRoleCode().trim() : null;
        String oldRoleCode = role.getRoleCode() != null ? role.getRoleCode().trim() : null;

        if (StringUtils.hasText(newRoleCode) && !newRoleCode.equals(oldRoleCode)) {
            LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Role::getRoleCode, newRoleCode)
                    .eq(Role::getTenantId, role.getTenantId())
                    .eq(Role::getDeleted, 0)
                    .ne(Role::getId, dto.getId());
            if (roleMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("角色编码已存在");
            }
        }

        String dataScope = dto.getDataScope() == null ? normalizeDataScope(role.getDataScope()) : normalizeDataScope(dto.getDataScope());
        if (dto.getFuncPermissions() != null) {
            validateAssignablePermissions(dto.getFuncPermissions());
        }
        validateAssignableOrgIds(dataScope, dto.getDataScopeOrgIds());

        Long requestedGroupId = dto.getGroupId();
        if (requestedGroupId != null) {
            requireAccessibleRoleGroup(requestedGroupId);
        }

        BeanUtils.copyProperties(dto, role);
        if (requestedGroupId != null) {
            role.setGroupId(requestedGroupId);
        }
        if (dto.getRemark() != null) {
            role.setRoleDesc(dto.getRemark());
        }
        role.setDataScope(dataScope);
        roleMapper.updateById(role);

        if (dto.getFuncPermissions() != null) {
            saveRolePermissions(role.getId(), dto.getFuncPermissions());
        }
        saveRoleDataScopeOrgs(role.getId(), dataScope, dto.getDataScopeOrgIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ROLE,
        operationType = AuditOperationType.DELETE,
        targetId = "#id",
        targetNo = "#entity.roleCode",
        desc = "'删除角色：' + #entity.roleName + '（' + #entity.roleCode + '）'",
        mapper = RoleMapper.class
    )
    public void delete(Long id) {
        Role role = getAccessibleRole(id);

        if ("system".equals(role.getRoleType())) {
            throw new RuntimeException("系统内置角色不能删除");
        }

        Integer memberCount = roleMapper.countMembersByRoleId(id,
                true,
                Collections.emptySet());
        if (memberCount > 0) {
            throw new RuntimeException("该角色下存在关联成员，请先移除成员");
        }

        roleMapper.deleteRolePermissionsByRoleId(id);
        roleMapper.deleteRoleDataScopeOrgs(id);
        roleMapper.deleteById(id);
    }

    @Override
    public PageResult<RoleMemberVO> getMembers(Long roleId, Integer pageNum, Integer pageSize) {
        getAccessibleRole(roleId);

        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        Page<RoleMemberVO> page = new Page<>(pageNum, pageSize);
        List<RoleMemberVO> members = roleMapper.selectMembersByRoleId(
                roleId,
                scope.isAllAccess(),
                scope.getOrgIds(),
                page
        );
        return PageResult.of(members, (long) pageNum, (long) pageSize, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ROLE,
        operationType = AuditOperationType.UPDATE,
        targetId = "#roleId",
        desc = "'添加角色成员：角色ID=' + #roleId + '，员工ID=' + #dto.employeeIds"
    )
    public Integer addMembers(Long roleId, RoleMemberDTO dto) {
        getAccessibleRole(roleId);

        if (CollectionUtils.isEmpty(dto.getEmployeeIds())) {
            throw new RuntimeException("请选择要添加的员工");
        }

        int addedCount = 0;
        for (Long employeeId : dto.getEmployeeIds()) {
            Employee employee = employeeMapper.selectById(employeeId);
            if (employee == null || employee.getDeleted() == 1) {
                continue;
            }
            if (employee.getUserId() == null) {
                continue;
            }
            if (roleMapper.checkUserHasRole(roleId, employee.getUserId()) > 0) {
                continue;
            }
            roleMapper.insertRoleMember(roleId, employee.getUserId());
            addedCount++;
        }

        return addedCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ROLE,
        operationType = AuditOperationType.UPDATE,
        targetId = "#roleId",
        desc = "'移除角色成员：角色ID=' + #roleId + '，员工ID=' + #employeeId"
    )
    public void removeMember(Long roleId, Long employeeId) {
        getAccessibleRole(roleId);

        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null || employee.getUserId() == null) {
            throw new RuntimeException("员工不存在或未关联用户账号");
        }

        if (roleMapper.checkUserHasRole(roleId, employee.getUserId()) == 0) {
            throw new RuntimeException("该员工不在此角色中");
        }

        roleMapper.deleteRoleMember(roleId, employee.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ROLE,
        operationType = AuditOperationType.UPDATE,
        targetId = "#roleId",
        desc = "'移除角色成员：角色ID=' + #roleId + '，员工ID=' + #dto.employeeIds"
    )
    public Integer batchRemoveMembers(Long roleId, RoleMemberDTO dto) {
        getAccessibleRole(roleId);

        if (CollectionUtils.isEmpty(dto.getEmployeeIds())) {
            throw new RuntimeException("请选择要移除的员工");
        }

        int removedCount = 0;
        for (Long employeeId : dto.getEmployeeIds()) {
            Employee employee = employeeMapper.selectById(employeeId);
            if (employee == null || employee.getUserId() == null) {
                continue;
            }
            if (roleMapper.checkUserHasRole(roleId, employee.getUserId()) == 0) {
                continue;
            }
            roleMapper.deleteRoleMember(roleId, employee.getUserId());
            removedCount++;
        }

        return removedCount;
    }

    @Override
    public List<Map<String, Object>> getAssignablePermissionTree() {
        List<Map<String, Object>> nodes;
        if (isAdminUser()) {
            nodes = roleMapper.selectAllPermissionNodes();
        } else {
            Long userId = UserContext.getUserId();
            if (userId == null) {
                return Collections.emptyList();
            }
            nodes = roleMapper.selectAssignablePermissionNodesByUserId(userId);
            nodes = attachAncestorNodes(nodes);
        }

        List<Map<String, Object>> filtered = nodes.stream()
                .filter(node -> !isReadActionButton(node))
                .collect(Collectors.toList());

        return buildPermissionTree(filtered);
    }

    private RoleVO convertToVO(Role role, Set<Long> visibleGroupIds, DataScopeService.DataScopeResult scope) {
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(role, vo);
        vo.setRemark(role.getRoleDesc());
        vo.setDataScope(normalizeDataScope(role.getDataScope()));

        if (role.getGroupId() != null) {
            if (visibleGroupIds.contains(role.getGroupId())) {
                RoleGroup group = roleGroupMapper.selectById(role.getGroupId());
                if (group != null) {
                    vo.setGroupName(group.getGroupName());
                } else {
                    vo.setGroupName(UNAUTHORIZED_GROUP_NAME);
                }
            } else {
                vo.setGroupName(UNAUTHORIZED_GROUP_NAME);
            }
        }

        vo.setGroupVisible(role.getGroupId() != null && visibleGroupIds.contains(role.getGroupId()));
        vo.setMemberCount(roleMapper.countMembersByRoleId(role.getId(), scope.isAllAccess(), scope.getOrgIds()));
        vo.setFuncPermissions(roleMapper.selectPermissionCodesByRoleId(role.getId()));
        vo.setDataScopeOrgIds(roleMapper.selectDataScopeOrgIdsByRoleId(role.getId()));
        return vo;
    }

    private RoleDetailVO convertToDetailVO(Role role, Set<Long> visibleGroupIds, DataScopeService.DataScopeResult scope) {
        RoleDetailVO vo = new RoleDetailVO();
        BeanUtils.copyProperties(role, vo);
        vo.setRemark(role.getRoleDesc());
        vo.setDataScope(normalizeDataScope(role.getDataScope()));

        if (role.getGroupId() != null) {
            if (visibleGroupIds.contains(role.getGroupId())) {
                RoleGroup group = roleGroupMapper.selectById(role.getGroupId());
                if (group != null) {
                    vo.setGroupName(group.getGroupName());
                } else {
                    vo.setGroupName(UNAUTHORIZED_GROUP_NAME);
                }
            } else {
                vo.setGroupName(UNAUTHORIZED_GROUP_NAME);
            }
        }
        vo.setGroupVisible(role.getGroupId() != null && visibleGroupIds.contains(role.getGroupId()));

        vo.setMemberCount(roleMapper.countMembersByRoleId(role.getId(), scope.isAllAccess(), scope.getOrgIds()));
        List<String> permissionCodes = roleMapper.selectPermissionCodesByRoleId(role.getId());
        vo.setFuncPermissions(permissionCodes);
        vo.setFuncPermissionNameMap(buildPermissionNameMap(permissionCodes));
        vo.setDataScopeOrgIds(roleMapper.selectDataScopeOrgIdsByRoleId(role.getId()));
        return vo;
    }

    private Role getAccessibleRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null || role.getDeleted() == 1) {
            throw new RuntimeException("角色不存在");
        }
        ensureRoleAccessible(role);
        return role;
    }

    private void ensureRoleAccessible(Role role) {
        if (role == null || isAdminUser()) {
            return;
        }

        if (!Objects.equals(role.getTenantId(), UserContext.getTenantId())) {
            throw new RuntimeException("无权访问该角色");
        }

        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(role.getOrgId())) {
            throw new RuntimeException("无权访问该角色");
        }
    }

    private RoleGroup requireAccessibleRoleGroup(Long groupId) {
        RoleGroup group = roleGroupMapper.selectById(groupId);
        if (group == null || group.getDeleted() == 1) {
            throw new RuntimeException("分组不存在");
        }

        if (isAdminUser()) {
            return group;
        }

        if (!Objects.equals(group.getTenantId(), UserContext.getTenantId())) {
            throw new RuntimeException("无权选择该分组");
        }

        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(group.getOrgId())) {
            throw new RuntimeException("无权选择该分组");
        }
        return group;
    }

    private Map<String, String> buildPermissionNameMap(List<String> permissionCodes) {
        if (CollectionUtils.isEmpty(permissionCodes)) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> rows = roleMapper.selectPermissionNameMapByCodes(permissionCodes);
        Map<String, String> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String code = Objects.toString(row.get("permissionCode"), null);
            String name = Objects.toString(row.get("permissionName"), null);
            if (StringUtils.hasText(code) && StringUtils.hasText(name)) {
                map.put(code, name);
            }
        }
        return map;
    }

    private String normalizeDataScope(String dataScope) {
        String normalized = StringUtils.hasText(dataScope) ? dataScope.trim().toLowerCase() : DATA_SCOPE_ALL;
        if (!DATA_SCOPE_ALLOWED.contains(normalized)) {
            throw new RuntimeException("数据权限范围不合法");
        }
        return normalized;
    }

    private void validateAssignablePermissions(List<String> permissionCodes) {
        if (CollectionUtils.isEmpty(permissionCodes) || isAdminUser()) {
            return;
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("未获取到当前登录用户");
        }

        Set<String> assignable = new LinkedHashSet<>(roleMapper.selectAssignablePermissionCodesByUserId(userId));
        List<String> denied = permissionCodes.stream()
                .filter(code -> !assignable.contains(code))
                .collect(Collectors.toList());
        if (!denied.isEmpty()) {
            throw new RuntimeException("存在越权分配的功能权限");
        }
    }

    private void validateAssignableOrgIds(String dataScope, List<Long> orgIds) {
        if (!DATA_SCOPE_CUSTOM.equals(dataScope)) {
            return;
        }

        if (CollectionUtils.isEmpty(orgIds)) {
            throw new RuntimeException("自定数据权限必须选择组织范围");
        }

        if (isAdminUser()) {
            return;
        }

        Set<Long> assignableOrgIds = getCurrentUserAssignableOrgIds();
        List<Long> denied = orgIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !assignableOrgIds.contains(id))
                .collect(Collectors.toList());
        if (!denied.isEmpty()) {
            throw new RuntimeException("存在越权分配的数据权限组织范围");
        }
    }

    private Set<Long> getCurrentUserAssignableOrgIds() {
        Long orgId = UserContext.getOrgId();
        if (orgId == null) {
            return Collections.emptySet();
        }
        Organization currentOrg = organizationMapper.selectById(orgId);
        if (currentOrg == null || !StringUtils.hasText(currentOrg.getPath())) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(roleMapper.selectManageableOrgIdsByOrg(orgId, currentOrg.getPath()));
    }

    private void saveRolePermissions(Long roleId, List<String> permissionCodes) {
        roleMapper.deleteRolePermissionsByRoleId(roleId);
        if (CollectionUtils.isEmpty(permissionCodes)) {
            return;
        }

        List<String> uniqueCodes = permissionCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (uniqueCodes.isEmpty()) {
            return;
        }

        List<Long> permissionIds = roleMapper.selectPermissionIdsByCodes(uniqueCodes);
        if (permissionIds.size() != uniqueCodes.size()) {
            throw new RuntimeException("提交了无效的权限编码");
        }

        roleMapper.batchInsertRolePermissions(roleId, permissionIds, UserContext.getUserId());
    }

    private void saveRoleDataScopeOrgs(Long roleId, String dataScope, List<Long> orgIds) {
        roleMapper.deleteRoleDataScopeOrgs(roleId);
        if (!DATA_SCOPE_CUSTOM.equals(dataScope) || CollectionUtils.isEmpty(orgIds)) {
            return;
        }

        List<Long> uniqueOrgIds = orgIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (uniqueOrgIds.isEmpty()) {
            return;
        }

        roleMapper.batchInsertRoleDataScopeOrgs(roleId, uniqueOrgIds, UserContext.getUserId());
    }

    private boolean isAdminUser() {
        return "admin".equalsIgnoreCase(UserContext.getUsername());
    }

    private List<Map<String, Object>> attachAncestorNodes(List<Map<String, Object>> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> allNodes = roleMapper.selectAllPermissionNodes();
        Map<Long, Map<String, Object>> allNodeMap = new LinkedHashMap<>();
        for (Map<String, Object> node : allNodes) {
            Long id = getLong(node, "id");
            if (id != null) {
                allNodeMap.put(id, node);
            }
        }

        Set<Long> includedIds = new LinkedHashSet<>();
        for (Map<String, Object> node : nodes) {
            Long id = getLong(node, "id");
            while (id != null && id != 0 && includedIds.add(id)) {
                Map<String, Object> current = allNodeMap.get(id);
                id = current == null ? null : getLong(current, "parentId", "parent_id");
            }
        }

        return allNodes.stream()
                .filter(node -> {
                    Long id = getLong(node, "id");
                    return id != null && includedIds.contains(id);
                })
                .collect(Collectors.toList());
    }

    private boolean isReadActionButton(Map<String, Object> node) {
        String permissionType = getString(node, "permissionType", "permission_type");
        if (!"button".equals(permissionType)) {
            return false;
        }
        String code = getString(node, "permissionCode", "permission_code");
        String name = getString(node, "permissionName", "permission_name");
        if (StringUtils.hasText(code)) {
            String lowerCode = code.toLowerCase();
            if (lowerCode.endsWith(":query") || lowerCode.endsWith(":search")
                    || lowerCode.endsWith(":view") || lowerCode.endsWith(":detail")) {
                return true;
            }
        }
        return "查询".equals(name) || "搜索".equals(name) || "查看".equals(name) || "详情".equals(name);
    }

    private List<Map<String, Object>> buildPermissionTree(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> index = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = getLong(row, "id");
            if (id == null) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", id);
            node.put("permissionCode", getString(row, "permissionCode", "permission_code"));
            node.put("permissionName", getString(row, "permissionName", "permission_name"));
            node.put("permissionType", getString(row, "permissionType", "permission_type"));
            node.put("parentId", getLong(row, "parentId", "parent_id"));
            node.put("moduleCode", getString(row, "moduleCode", "module_code"));
            node.put("resourcePath", getString(row, "resourcePath", "resource_path"));
            node.put("sortOrder", getInteger(row, "sortOrder", "sort_order"));
            node.put("children", new ArrayList<Map<String, Object>>());
            index.put(id, node);
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> node : index.values()) {
            Long parentId = getLong(node, "parentId");
            if (parentId == null || parentId == 0 || !index.containsKey(parentId)) {
                roots.add(node);
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) index.get(parentId).get("children");
            children.add(node);
        }

        sortPermissionTree(roots);
        return roots;
    }

    private void sortPermissionTree(List<Map<String, Object>> nodes) {
        nodes.sort(Comparator
                .comparing((Map<String, Object> node) -> getInteger(node, "sortOrder"), Comparator.nullsLast(Integer::compareTo))
                .thenComparing(node -> getLong(node, "id"), Comparator.nullsLast(Long::compareTo)));
        for (Map<String, Object> node : nodes) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            if (!CollectionUtils.isEmpty(children)) {
                sortPermissionTree(children);
            }
        }
    }

    private String getString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String str) {
                return str;
            }
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private Long getLong(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String str && StringUtils.hasText(str)) {
                return Long.parseLong(str);
            }
        }
        return null;
    }

    private Integer getInteger(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String str && StringUtils.hasText(str)) {
                return Integer.parseInt(str);
            }
        }
        return null;
    }
}
