package com.xykj.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.service.DataScopeService;
import com.xykj.sys.dto.RoleGroupCreateDTO;
import com.xykj.sys.dto.RoleGroupUpdateDTO;
import com.xykj.sys.entity.RoleGroup;
import com.xykj.sys.mapper.RoleGroupMapper;
import com.xykj.sys.service.RoleGroupService;
import com.xykj.sys.vo.RoleGroupVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 角色分组服务实现
 */
@Service
@RequiredArgsConstructor
public class RoleGroupServiceImpl implements RoleGroupService {

    private final RoleGroupMapper roleGroupMapper;
    private final DataScopeService dataScopeService;

    @Override
    public List<RoleGroupVO> list() {
        Long tenantId = UserContext.getTenantId();

        List<RoleGroup> groups;
        if (tenantId != null) {
            groups = roleGroupMapper.selectAllByTenantId(tenantId);
        } else {
            groups = roleGroupMapper.selectAll();
        }

        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllAccess()) {
            groups = groups.stream()
                    .filter(group -> scope.getOrgIds().contains(group.getOrgId()))
                    .collect(Collectors.toList());
        }

        return groups.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public RoleGroupVO getDetail(Long id) {
        RoleGroup group = getAccessibleGroup(id);
        return convertToVO(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ROLE_GROUP,
        operationType = AuditOperationType.CREATE,
        targetId = "#result",
        targetNo = "",
        desc = "'新增角色分组：' + #dto.groupName",
        mapper = RoleGroupMapper.class
    )
    public Long create(RoleGroupCreateDTO dto) {
        ensureOrgAllowed(dto.getOrgId());

        // 校验分组名称是否重复
        Long tenantId = UserContext.getTenantId();
        LambdaQueryWrapper<RoleGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleGroup::getGroupName, dto.getGroupName())
               .eq(RoleGroup::getDeleted, 0);
        if (tenantId != null) {
            wrapper.eq(RoleGroup::getTenantId, tenantId);
        }
        if (roleGroupMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("分组名称已存在");
        }

        RoleGroup group = new RoleGroup();
        group.setGroupName(dto.getGroupName());
        group.setOrgId(dto.getOrgId() != null ? dto.getOrgId() : UserContext.getOrgId());
        group.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        group.setRemark(dto.getRemark());
        group.setTenantId(UserContext.getTenantId());

        roleGroupMapper.insert(group);

        return group.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ROLE_GROUP,
        operationType = AuditOperationType.UPDATE,
        targetId = "#dto.id",
        targetNo = "",
        desc = "'编辑角色分组：' + #entity.groupName",
        mapper = RoleGroupMapper.class
    )
    public void update(RoleGroupUpdateDTO dto) {
        RoleGroup group = getAccessibleGroup(dto.getId());

        // 校验分组名称是否重复
        if (dto.getGroupName() != null && !dto.getGroupName().equals(group.getGroupName())) {
            LambdaQueryWrapper<RoleGroup> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RoleGroup::getGroupName, dto.getGroupName())
                   .eq(RoleGroup::getTenantId, group.getTenantId())
                   .eq(RoleGroup::getDeleted, 0)
                   .ne(RoleGroup::getId, dto.getId());
            if (roleGroupMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("分组名称已存在");
            }
            group.setGroupName(dto.getGroupName());
        }

        if (dto.getOrgId() != null) {
            ensureOrgAllowed(dto.getOrgId());
            group.setOrgId(dto.getOrgId());
        }
        if (dto.getSortOrder() != null) {
            group.setSortOrder(dto.getSortOrder());
        }
        if (dto.getRemark() != null) {
            group.setRemark(dto.getRemark());
        }

        roleGroupMapper.updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ROLE_GROUP,
        operationType = AuditOperationType.DELETE,
        targetId = "#id",
        targetNo = "",
        desc = "'删除角色分组：' + #entity.groupName",
        mapper = RoleGroupMapper.class
    )
    public void delete(Long id) {
        RoleGroup group = getAccessibleGroup(id);

        // 检查分组下是否有角色
        Integer roleCount = roleGroupMapper.countRolesByGroupId(id);
        if (roleCount > 0) {
            throw new RuntimeException("该分组下存在角色，请先移除或删除角色");
        }

        roleGroupMapper.deleteById(id);
    }

    private RoleGroup getAccessibleGroup(Long id) {
        RoleGroup group = roleGroupMapper.selectById(id);
        if (group == null || group.getDeleted() == 1) {
            throw new RuntimeException("分组不存在");
        }
        ensureGroupAccessible(group);
        return group;
    }

    private void ensureGroupAccessible(RoleGroup group) {
        if (group == null || dataScopeService.isAdminUser()) {
            return;
        }

        if (!Objects.equals(group.getTenantId(), UserContext.getTenantId())) {
            throw new RuntimeException("无权访问该分组");
        }

        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(group.getOrgId())) {
            throw new RuntimeException("无权访问该分组");
        }
    }

    private void ensureOrgAllowed(Long orgId) {
        if (orgId == null || dataScopeService.isAdminUser()) {
            return;
        }

        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(orgId)) {
            throw new RuntimeException("无权选择该组织");
        }
    }

    /**
     * 转换为VO
     */
    private RoleGroupVO convertToVO(RoleGroup group) {
        RoleGroupVO vo = new RoleGroupVO();
        vo.setId(group.getId());
        vo.setGroupName(group.getGroupName());
        vo.setOrgId(group.getOrgId());
        vo.setSortOrder(group.getSortOrder());
        vo.setRemark(group.getRemark());
        vo.setCreatedAt(group.getCreatedAt());
        vo.setUpdatedAt(group.getUpdatedAt());

        // 查询角色数量
        vo.setRoleCount(roleGroupMapper.countRolesByGroupId(group.getId()));

        return vo;
    }
}
