package com.xykj.sys.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.sys.dto.*;
import com.xykj.sys.entity.Organization;
import com.xykj.sys.mapper.EmployeeMapper;
import com.xykj.sys.mapper.OrganizationMapper;
import com.xykj.sys.service.DictCategoryService;
import com.xykj.sys.service.OrganizationService;
import com.xykj.sys.vo.DictCategoryOptionVO;
import com.xykj.sys.vo.OrganizationStatisticsVO;
import com.xykj.sys.vo.OrganizationTreeVO;
import com.xykj.sys.vo.OrganizationVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 组织服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationMapper organizationMapper;
    private final EmployeeMapper employeeMapper;
    private final AuditLogService auditLogService;
    private final DataScopeService dataScopeService;
    private final JdbcTemplate jdbcTemplate;
    private final DictCategoryService dictCategoryService;

    @Override
    public PageResult<OrganizationVO> list(OrganizationQueryDTO query) {
        Set<Long> manageableOrgIds = resolveManageableOrgIds();
        if (manageableOrgIds != null && manageableOrgIds.isEmpty()) {
            return PageResult.empty((long) query.getPageNum(), (long) query.getPageSize());
        }

        // 构建查询条件
        LambdaQueryWrapper<Organization> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(manageableOrgIds != null, Organization::getId, manageableOrgIds);
        wrapper.and(StrUtil.isNotBlank(query.getKeyword()), w -> w.like(Organization::getOrgName, query.getKeyword()).or().like(Organization::getOrgCode, query.getKeyword()))
                .like(StrUtil.isNotBlank(query.getOrgName()), Organization::getOrgName, query.getOrgName())
                .like(StrUtil.isNotBlank(query.getOrgCode()), Organization::getOrgCode, query.getOrgCode())
                .eq(StrUtil.isNotBlank(query.getOrgType()), Organization::getOrgType, query.getOrgType())
                .eq(StrUtil.isNotBlank(query.getStatus()), Organization::getStatus, query.getStatus())
                .eq(query.getParentId() != null, Organization::getParentId, query.getParentId())
                .orderByAsc(Organization::getSortOrder)
                .orderByDesc(Organization::getCreatedAt);

        // 分页查询
        IPage<Organization> page = organizationMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        // 转换为VO
        List<OrganizationVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 填充父组织名称
        fillParentNames(voList);

        return PageResult.of(page, voList);
    }

    @Override
    public OrganizationVO getDetail(Long id) {
        ensureOrgManageable(id);
        Organization org = getOrganizationById(id);
        OrganizationVO vo = convertToVO(org);

        // 查询父组织名称
        if (org.getParentId() != null && org.getParentId() > 0) {
            Organization parent = organizationMapper.selectById(org.getParentId());
            if (parent != null) {
                vo.setParentName(parent.getOrgName());
            }
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ORGANIZATION,
        operationType = AuditOperationType.CREATE,
        targetId = "#result['id']",
        targetNo = "#result['orgCode']",
        desc = "'新增组织：' + #result['orgName'] + '（' + #result['orgCode'] + '）'",
        mapper = OrganizationMapper.class
    )
    public Map<String, Object> create(OrganizationCreateDTO dto) {
        // 校验组织编码唯一性
        checkOrgCodeUnique(dto.getOrgCode(), null);

        // 校验同父级下组织名称去空格后唯一
        Long parentId = dto.getParentId() != null ? dto.getParentId() : 0L;
        checkOrgNameUniqueInParent(dto.getOrgName(), parentId, null);

        // 校验父组织
        Organization parent = null;
        if (dto.getParentId() != null && dto.getParentId() > 0) {
            ensureOrgManageable(dto.getParentId());
            parent = getOrganizationById(dto.getParentId());
            if (!"active".equals(parent.getStatus())) {
                throw BizException.validationFailed("停用组织下不允许新增子组织");
            }
            // 校验层级深度
            validateLevelDepth(parent.getLevel() + 1);
        }

        // 创建组织实体
        Organization org = new Organization();
        org.setOrgCode(dto.getOrgCode());
        org.setOrgName(dto.getOrgName());
        org.setOrgType(dto.getOrgType());
        org.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        org.setLeaderName(dto.getLeaderName());
        org.setContactPhone(dto.getContactPhone());
        org.setAddress(dto.getAddress());
        org.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        org.setStatus(StrUtil.isNotBlank(dto.getStatus()) ? dto.getStatus() : "active");
        org.setTenantId(UserContext.getTenantId());

        // 先插入获取ID
        organizationMapper.insert(org);

        // 计算层级和路径（路径存储组织名称）
        if (parent != null) {
            org.setLevel(parent.getLevel() + 1);
            org.setPath(parent.getPath() + org.getOrgName() + "/");
        } else {
            org.setLevel(1);
            org.setPath("/" + org.getOrgName() + "/");
        }

        // 更新层级和路径
        organizationMapper.updateById(org);

        log.info("创建组织成功: id={}, code={}", org.getId(), org.getOrgCode());

        Map<String, Object> result = new HashMap<>();
        result.put("id", org.getId());
        result.put("orgCode", org.getOrgCode());
        result.put("orgName", org.getOrgName());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ORGANIZATION,
        operationType = AuditOperationType.UPDATE,
        targetId = "#id",
        targetNo = "#entity.orgCode",
        desc = "'编辑组织：' + #entity.orgName + '（' + #entity.orgCode + '）'",
        mapper = OrganizationMapper.class
    )
    public Map<String, Object> update(Long id, OrganizationUpdateDTO dto) {
        ensureOrgManageable(id);
        Organization org = getOrganizationById(id);

        // 标记是否需要重新计算层级和路径
        boolean needRecalculatePath = false;
        Organization newParent = null;

        // 确定目标名称和目标父级（用于校验同父级名称唯一）
        String targetOrgName = StrUtil.isNotBlank(dto.getOrgName()) ? dto.getOrgName() : org.getOrgName();
        Long targetParentId = dto.getParentId() != null ? (dto.getParentId() > 0 ? dto.getParentId() : 0L) : org.getParentId();
        boolean nameOrParentChanged = (StrUtil.isNotBlank(dto.getOrgName()) && !dto.getOrgName().equals(org.getOrgName()))
                || (dto.getParentId() != null && !dto.getParentId().equals(org.getParentId()));
        if (nameOrParentChanged) {
            checkOrgNameUniqueInParent(targetOrgName, targetParentId, id);
        }

        // 更新���级组织（需要重新计算层级和路径）
        if (dto.getParentId() != null && !dto.getParentId().equals(org.getParentId())) {
            // 不能将自己设为自己的上级
            if (dto.getParentId().equals(id)) {
                throw BizException.validationFailed("不能将自己设为上级组织");
            }

            // 校验新的父组织是否存在
            if (dto.getParentId() > 0) {
                newParent = getOrganizationById(dto.getParentId());
                ensureOrgManageable(newParent.getId());
                // 检查是否会形成循环（通过 parentId 链判断）
                if (isDescendant(dto.getParentId(), id)) {
                    throw BizException.validationFailed("不能将子组织设为上级组织");
                }
                // 校验子树在新父级下的层级深度
                validateSubtreeDepth(id, newParent.getLevel());
            }

            // 更新 parentId
            org.setParentId(dto.getParentId() > 0 ? dto.getParentId() : 0L);
            needRecalculatePath = true;
        }

        // 更新组织名称时，也需要重新计算路径
        if (StrUtil.isNotBlank(dto.getOrgName()) && !dto.getOrgName().equals(org.getOrgName())) {
            org.setOrgName(dto.getOrgName());
            needRecalculatePath = true;
        }

        // 重新计算层级和路径
        if (needRecalculatePath) {
            // 获取父组织（如果 parentId 没变但名称变了，需要重新获取）
            if (org.getParentId() != null && org.getParentId() > 0) {
                ensureOrgManageable(org.getParentId());
                newParent = organizationMapper.selectById(org.getParentId());
            }

            if (newParent != null) {
                org.setLevel(newParent.getLevel() + 1);
                org.setPath(newParent.getPath() + org.getOrgName() + "/");
            } else {
                org.setLevel(1);
                org.setPath("/" + org.getOrgName() + "/");
            }
        }

        // 更新其他字段
        if (StrUtil.isNotBlank(dto.getOrgType())) {
            org.setOrgType(dto.getOrgType());
        }
        if (dto.getLeaderName() != null) {
            org.setLeaderName(StrUtil.isBlank(dto.getLeaderName()) ? "" : dto.getLeaderName().trim());
        }
        if (dto.getContactPhone() != null) {
            org.setContactPhone(StrUtil.isBlank(dto.getContactPhone()) ? "" : dto.getContactPhone().trim());
        }
        if (dto.getAddress() != null) {
            org.setAddress(StrUtil.isBlank(dto.getAddress()) ? "" : dto.getAddress().trim());
        }
        if (StrUtil.isNotBlank(dto.getStatus())) {
            org.setStatus(dto.getStatus());
        }
        if (dto.getSortOrder() != null) {
            org.setSortOrder(dto.getSortOrder());
        }

        organizationMapper.updateById(org);

        // 如果路径变更，级联更新所有子组织的路径
        if (needRecalculatePath) {
            updateChildrenPath(id, org.getPath(), org.getLevel());
        }

        log.info("更新组织成功: id={}", id);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("orgName", org.getOrgName());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ORGANIZATION,
        operationType = AuditOperationType.DELETE,
        targetId = "#id",
        targetNo = "#entity.orgCode",
        desc = "'删除组织：' + #entity.orgName + '（' + #entity.orgCode + '）'",
        mapper = OrganizationMapper.class
    )
    public void delete(Long id) {
        ensureOrgManageable(id);
        Organization org = getOrganizationById(id);

        // 检查是否存在子组织
        Long childCount = organizationMapper.countChildrenByParentId(id);
        if (childCount > 0) {
            throw BizException.validationFailed("组织下存在子组织，无法删除");
        }

        // 检查是否存在关联成员
        Long memberCount = employeeMapper.countByOrgId(id);
        if (memberCount > 0) {
            throw BizException.validationFailed("组织下存在关联成员，无法删除");
        }

        // 检查是否存在业务数据引用
        if (hasBusinessData(id)) {
            throw BizException.validationFailed("该组织已存在业务数据，为保证数据完整性与追溯性，不允许删除");
        }

        organizationMapper.deleteById(id);
        log.info("删除组织成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_ORGANIZATION,
        operationType = AuditOperationType.STATUS_CHANGE,
        targetId = "#id",
        targetNo = "#entity.orgCode",
        desc = "(#dto.status == 'active' ? '启用' : '停用') + '组织：' + #entity.orgName + '（' + #entity.orgCode + '）'",
        mapper = OrganizationMapper.class
    )
    public Map<String, Object> updateStatus(Long id, OrganizationStatusDTO dto) {
        ensureOrgManageable(id);
        Organization org = getOrganizationById(id);

        // 校验状态值
        if (!"active".equals(dto.getStatus()) && !"inactive".equals(dto.getStatus())) {
            throw BizException.badRequest("状态值只能是 active 或 inactive");
        }

        // 如果要启用，检查父组织是否已启用
        if ("active".equals(dto.getStatus()) && org.getParentId() != null && org.getParentId() > 0) {
            Organization parent = organizationMapper.selectById(org.getParentId());
            if (parent != null && "inactive".equals(parent.getStatus())) {
                throw BizException.validationFailed("父组织已禁用，无法启用该组织");
            }
        }

        // 停用前校验：当前组织及所有子组织均需通过校验，否则整体阻断
        if ("inactive".equals(dto.getStatus())) {
            validateDeactivateOrgTree(id);
        }

        org.setStatus(dto.getStatus());
        organizationMapper.updateById(org);

        // 停用父组织时，递归停用全部下级；启用时仅启用当前组织，不自动联动下级
        if ("inactive".equals(dto.getStatus())) {
            deactivateDescendants(id);
        }

        log.info("更新组织状态成功: id={}, status={}", id, dto.getStatus());

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("orgName", org.getOrgName());
        result.put("status", dto.getStatus());
        return result;
    }

    @Override
    public List<OrganizationTreeVO> getTree(String orgType, String status, String keyword, Boolean includeChildren) {
        Set<Long> manageableOrgIds = resolveManageableOrgIds();
        if (manageableOrgIds != null && manageableOrgIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 先查询当前用户权限范围内的全量组织，再基于筛选条件计算“命中节点”
        List<Organization> scopedOrganizations;
        if (manageableOrgIds == null) {
            scopedOrganizations = organizationMapper.selectList(new LambdaQueryWrapper<Organization>()
                    .orderByAsc(Organization::getSortOrder));
        } else {
            scopedOrganizations = organizationMapper.selectBatchIds(manageableOrgIds);
            scopedOrganizations.sort(Comparator.comparing(Organization::getSortOrder));
        }

        List<Organization> matchedOrganizations = scopedOrganizations.stream()
                .filter(org -> StrUtil.isBlank(orgType) || orgType.equals(org.getOrgType()))
                .filter(org -> StrUtil.isBlank(status) || status.equals(org.getStatus()))
                .filter(org -> StrUtil.isBlank(keyword)
                        || StrUtil.containsIgnoreCase(org.getOrgName(), keyword)
                        || StrUtil.containsIgnoreCase(org.getOrgCode(), keyword))
                .collect(Collectors.toList());

        if (matchedOrganizations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Organization> organizations;
        if (Boolean.TRUE.equals(includeChildren)) {
            Set<Long> rootIds = matchedOrganizations.stream().map(Organization::getId).collect(Collectors.toSet());
            Map<Long, List<Organization>> childrenMap = scopedOrganizations.stream()
                    .collect(Collectors.groupingBy(org -> org.getParentId() == null ? 0L : org.getParentId()));
            Set<Long> collectedIds = new HashSet<>(rootIds);
            Deque<Long> queue = new ArrayDeque<>(rootIds);
            while (!queue.isEmpty()) {
                Long currentId = queue.poll();
                List<Organization> children = childrenMap.getOrDefault(currentId, Collections.emptyList());
                for (Organization child : children) {
                    if (collectedIds.add(child.getId())) {
                        queue.offer(child.getId());
                    }
                }
            }

            organizations = scopedOrganizations.stream()
                    .filter(org -> collectedIds.contains(org.getId()))
                    .collect(Collectors.toList());
        } else {
            organizations = matchedOrganizations;
        }

        // 构建ID到名称的映射，用于设置parentName
        Map<Long, String> orgNameMap = organizations.stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getOrgName));

        // 批量查询各组织的成员数量
        Map<Long, Long> memberCountMap = batchCountMembers(organizations);

        // 数据权限下可能只拿到中间层节点（父��点不在结果集中），此时把这些节点视作可见根节点
        Set<Long> visibleOrgIds = organizations.stream()
                .map(Organization::getId)
                .collect(Collectors.toSet());

        List<OrganizationTreeVO> tree = new ArrayList<>();
        for (Organization org : organizations) {
            Long parentId = org.getParentId();
            if (parentId == null || parentId == 0L || !visibleOrgIds.contains(parentId)) {
                OrganizationTreeVO node = convertToTreeNode(org, orgNameMap, memberCountMap);
                node.setChildren(buildTree(organizations, org.getId(), orgNameMap, memberCountMap));
                tree.add(node);
            }
        }
        return tree;
    }

    @Override
    public OrganizationStatisticsVO getStatistics() {
        Set<Long> manageableOrgIds = resolveManageableOrgIds();
        OrganizationStatisticsVO vo = new OrganizationStatisticsVO();
        if (manageableOrgIds != null && manageableOrgIds.isEmpty()) {
            vo.setTotal(0L);
            vo.setGroupCount(0L);
            vo.setCompanyCount(0L);
            vo.setCanteenCount(0L);
            vo.setDeptCount(0L);
            vo.setActiveCount(0L);
            vo.setInactiveCount(0L);
            return vo;
        }

        LambdaQueryWrapper<Organization> baseWrapper = new LambdaQueryWrapper<>();
        baseWrapper.in(manageableOrgIds != null, Organization::getId, manageableOrgIds);

        vo.setTotal(organizationMapper.selectCount(baseWrapper));
        vo.setGroupCount(countByType(manageableOrgIds, "group"));
        vo.setCompanyCount(countByType(manageableOrgIds, "company"));
        vo.setCanteenCount(countByType(manageableOrgIds, "canteen"));
        vo.setDeptCount(countByType(manageableOrgIds, "dept"));
        vo.setActiveCount(organizationMapper.selectCount(new LambdaQueryWrapper<Organization>()
                .in(manageableOrgIds != null, Organization::getId, manageableOrgIds)
                .eq(Organization::getStatus, "active")));
        vo.setInactiveCount(organizationMapper.selectCount(new LambdaQueryWrapper<Organization>()
                .in(manageableOrgIds != null, Organization::getId, manageableOrgIds)
                .eq(Organization::getStatus, "inactive")));

        return vo;
    }

    // ==================== 私有方法 ====================

    /**
     * 根据ID获取组织
     */
    private Organization getOrganizationById(Long id) {
        Organization org = organizationMapper.selectById(id);
        if (org == null || org.getDeleted() != null && org.getDeleted() == 1) {
            throw BizException.notFound("组织不存在");
        }
        return org;
    }

    private void ensureOrgManageable(Long orgId) {
        if (orgId == null || dataScopeService.isAdminUser()) {
            return;
        }
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(orgId)) {
            throw BizException.forbidden("无权访问该组织数据");
        }
    }

    private Set<Long> resolveManageableOrgIds() {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (scope.isAllAccess()) {
            return null;
        }
        return scope.getOrgIds();
    }

    private Long countByType(Set<Long> manageableOrgIds, String orgType) {
        LambdaQueryWrapper<Organization> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(manageableOrgIds != null, Organization::getId, manageableOrgIds)
                .eq(Organization::getOrgType, orgType);
        return organizationMapper.selectCount(wrapper);
    }

    /**
     * 校验组织编码唯一性
     */
    private void checkOrgCodeUnique(String orgCode, Long excludeId) {
        Long count = organizationMapper.countByOrgCodeExcludeId(orgCode, excludeId);
        if (count > 0) {
            throw BizException.conflict("组织编码已存在");
        }
    }

    /** 组织层级深度上限 */
    private static final int MAX_LEVEL = 10;

    /**
     * 校验新增组织的层级深度
     */
    private void validateLevelDepth(int newLevel) {
        if (newLevel > MAX_LEVEL) {
            throw BizException.validationFailed("组织层级不能超过" + MAX_LEVEL + "层");
        }
    }

    /**
     * 校验移动子树后的层级深度：自身及所有后代在新链路下均不能超限
     */
    private void validateSubtreeDepth(Long orgId, int newParentLevel) {
        validateSubtreeDepthRecursive(orgId, newParentLevel);
    }

    private void validateSubtreeDepthRecursive(Long orgId, int parentLevel) {
        int currentLevel = parentLevel + 1;
        if (currentLevel > MAX_LEVEL) {
            throw BizException.validationFailed("组织层级不能超过" + MAX_LEVEL + "层，移动后存在后代组织超限");
        }
        List<Organization> children = organizationMapper.selectList(
                new LambdaQueryWrapper<Organization>().eq(Organization::getParentId, orgId));
        for (Organization child : children) {
            validateSubtreeDepthRecursive(child.getId(), currentLevel);
        }
    }

    /**
     * 校验同父级下组织名称去空格后唯一
     */
    private void checkOrgNameUniqueInParent(String orgName, Long parentId, Long excludeId) {
        String trimmedName = orgName.replaceAll("\\s+", "");
        LambdaQueryWrapper<Organization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Organization::getParentId, parentId)
                .apply("REPLACE(REPLACE(REPLACE(org_name, ' ', ''), '\\t', ''), '\\n', '') = {0}", trimmedName);
        if (excludeId != null) {
            wrapper.ne(Organization::getId, excludeId);
        }
        Long count = organizationMapper.selectCount(wrapper);
        if (count > 0) {
            throw BizException.conflict("同一父级下已存在同名组织");
        }
    }

    /**
     * 构建数据库中每个父级ID下的组织名称集合（去空格）
     */
    private Map<Long, Set<String>> buildDbOrgNameSetByParentId() {
        List<Organization> all = organizationMapper.selectList(null);
        Map<Long, Set<String>> map = new HashMap<>();
        for (Organization org : all) {
            Long parentId = org.getParentId() != null ? org.getParentId() : 0L;
            map.computeIfAbsent(parentId, k -> new HashSet<>())
                    .add(org.getOrgName().replaceAll("\\s+", ""));
        }
        return map;
    }

    /**
     * 根据父组织编码解析父组织ID（导入用）
     */
    private Long resolveImportParentId(String parentCode, Map<String, Long> existingMap, Map<String, Long> newMap) {
        if (StrUtil.isBlank(parentCode)) {
            return 0L;
        }
        return newMap.getOrDefault(parentCode, existingMap.getOrDefault(parentCode, 0L));
    }

    /**
     * 校验停用组织树：当前组织及所有子组织均需通过校验，否则整体阻断
     */
    private void validateDeactivateOrgTree(Long orgId) {
        collectDescendantIds(orgId).forEach(this::validateDeactivateSingleOrg);
    }

    /**
     * 收集组织自身及所有子孙组织ID
     */
    private List<Long> collectDescendantIds(Long orgId) {
        List<Long> ids = new ArrayList<>();
        ids.add(orgId);
        collectDescendantIdsRecursive(orgId, ids);
        return ids;
    }

    private void collectDescendantIdsRecursive(Long parentId, List<Long> ids) {
        List<Organization> children = organizationMapper.selectList(
                new LambdaQueryWrapper<Organization>().eq(Organization::getParentId, parentId));
        for (Organization child : children) {
            ids.add(child.getId());
            collectDescendantIdsRecursive(child.getId(), ids);
        }
    }

    /** 停用组织校验失败提示 */
    private static final String DEACTIVATE_ERR_MSG = "该组织/下级组织存在未办结在途业务数据，请先办结、作废相关单据与任务后，再执行停用操作";

    /**
     * 校验单个组织是否允许停用：无员工、无未完结业务单据
     */
    private void validateDeactivateSingleOrg(Long orgId) {
        // 检查是否有员工
        Long memberCount = employeeMapper.countByOrgId(orgId);
        if (memberCount != null && memberCount > 0) {
            throw BizException.validationFailed("组织下存在关联成员，无法停用");
        }
        // 检查是否有未完结业务单据
        if (hasUnfinishedBusiness(orgId)) {
            throw BizException.validationFailed(DEACTIVATE_ERR_MSG);
        }
    }

    /**
     * 检查组织是否存在未完结业务单据
     */
    private boolean hasUnfinishedBusiness(Long orgId) {
        // 入库单：completed/cancelled 为已完结，其余（含已驳回）为未完结
        if (countUnfinished("wms_inbound_order", orgId, "receiving_org_id",
                "status NOT IN ('completed', 'cancelled')")) return true;
        // 出库单：completed 为已完结，其余（含已驳回）为未完结
        if (countUnfinished("wms_outbound_order", orgId, "target_org_id",
                "status NOT IN ('completed')")) return true;
        // 采购订单：completed/voided/cancelled/closed 为已完结
        if (countUnfinished("scm_purchase_order", orgId, "org_id",
                "status NOT IN ('completed', 'voided', 'cancelled', 'closed')")) return true;
        // 采购计划：approved/voided 为已完结
        if (countUnfinished("scm_purchase_plan", orgId, "org_id",
                "status NOT IN ('approved', 'voided')")) return true;
        // 盘点单：completed/voided 为已完结
        if (countUnfinished("wms_stocktake_order", orgId, "org_id",
                "status NOT IN ('completed', 'voided')")) return true;
        return false;
    }

    /**
     * 查询指定表中某组织的未完结单据数
     */
    private boolean countUnfinished(String table, Long orgId, String orgColumn, String statusCondition) {
        if (!tableExists(table) || !columnExists(table, orgColumn)) return false;
        String deletedCondition = columnExists(table, "deleted") ? " AND deleted = 0" : "";
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + orgColumn + " = ? AND " + statusCondition + deletedCondition,
                Long.class, orgId);
        return count != null && count > 0;
    }

    /**
     * 检查指定组织是否是另一个组织的子孙
     * @param parentId 要设为新父组织的ID
     * @param ancestorId 当前组织ID
     * @return true 如果是子孙组织
     */
    private boolean isDescendant(Long parentId, Long ancestorId) {
        Organization current = organizationMapper.selectById(parentId);
        while (current != null && current.getParentId() != null && current.getParentId() > 0) {
            if (current.getParentId().equals(ancestorId)) {
                return true;
            }
            current = organizationMapper.selectById(current.getParentId());
        }
        return false;
    }

    /**
     * 递归更新子组织的路径
     * @param parentId 父组织ID
     * @param parentPath 父组织的新路径
     * @param parentLevel 父组织的层级
     */
    private void updateChildrenPath(Long parentId, String parentPath, Integer parentLevel) {
        // 查询所有子组织
        List<Organization> children = organizationMapper.selectList(
                new LambdaQueryWrapper<Organization>().eq(Organization::getParentId, parentId)
        );

        for (Organization child : children) {
            // 更新子组织的路径
            child.setPath(parentPath + child.getOrgName() + "/");
            child.setLevel(parentLevel + 1); // 子组织层级 = 父组织层级 + 1
            organizationMapper.updateById(child);

            // 递归更新孙子组织
            updateChildrenPath(child.getId(), child.getPath(), child.getLevel());
        }
    }

    /**
     * 递归停用所有下级组织，返回被停用的组织编码集合
     */
    private Set<String> deactivateDescendants(Long parentId) {
        Set<String> deactivatedCodes = new HashSet<>();
        List<Organization> children = organizationMapper.selectList(
                new LambdaQueryWrapper<Organization>().eq(Organization::getParentId, parentId)
        );

        for (Organization child : children) {
            if (!"inactive".equals(child.getStatus())) {
                child.setStatus("inactive");
                organizationMapper.updateById(child);
            }
            deactivatedCodes.add(child.getOrgCode());
            deactivatedCodes.addAll(deactivateDescendants(child.getId()));
        }
        return deactivatedCodes;
    }

    /**
     * 组织是否存在业务数据引用
     */
    private boolean hasBusinessData(Long orgId) {
        String[] tables = {
                "scm_supplier",
                "scm_purchase_plan",
                "scm_purchase_order",
                "wms_material",
                "wms_warehouse",
                "wms_inbound_order",
                "wms_outbound_order",
                "wms_stocktake_order",
                "recipe",
                "recipe_plan",
                "cook_task",
                "health_certificate",
                "health_check_record",
                "sample_record",
                "device_info",
                "sys_complaint",
                "sys_meal_review"
        };

        for (String table : tables) {
            if (!tableExists(table) || !columnExists(table, "org_id")) {
                continue;
            }

            String sql = columnExists(table, "deleted")
                    ? "SELECT COUNT(*) FROM " + table + " WHERE org_id = ? AND deleted = 0"
                    : "SELECT COUNT(*) FROM " + table + " WHERE org_id = ?";

            Long count = jdbcTemplate.queryForObject(sql, Long.class, orgId);
            if (count != null && count > 0) {
                return true;
            }
        }

        return false;
    }

    private boolean tableExists(String tableName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Long.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Long.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    /**
     * 获取启用的组织类型code集合
     */
    private Set<String> getEnabledOrgTypeCodes() {
        List<DictCategoryOptionVO> options = dictCategoryService.getOptions("org_type", false);
        return options.stream()
                .map(DictCategoryOptionVO::getDictCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }

    /**
     * 获取组织类型说明文案，如 "group(集团)、company(分公司)、canteen(食堂)、dept(部门)"
     */
    private String getOrgTypeDesc() {
        List<DictCategoryOptionVO> options = dictCategoryService.getOptions("org_type", false);
        if (options.isEmpty()) {
            return "请在字典分类维护中配置组织类型";
        }
        return options.stream()
                .map(o -> o.getDictCode() + "(" + o.getDictName() + ")")
                .collect(Collectors.joining("、"));
    }

    /**
     * 转换为VO
     */
    private OrganizationVO convertToVO(Organization org) {
        OrganizationVO vo = new OrganizationVO();
        BeanUtil.copyProperties(org, vo);
        return vo;
    }

    /**
     * 填充父组织名称
     */
    private void fillParentNames(List<OrganizationVO> voList) {
        // 收集所有父组织ID
        Set<Long> parentIds = voList.stream()
                .map(OrganizationVO::getParentId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        if (parentIds.isEmpty()) {
            return;
        }

        // 批量查询父组织
        List<Organization> parents = organizationMapper.selectBatchIds(parentIds);
        Map<Long, String> parentNameMap = parents.stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getOrgName));

        // 填充父组织名称
        voList.forEach(vo -> {
            if (vo.getParentId() != null && vo.getParentId() > 0) {
                vo.setParentName(parentNameMap.get(vo.getParentId()));
            }
        });
    }

    /**
     * 递归构建组织树
     */
    private List<OrganizationTreeVO> buildTree(List<Organization> organizations, Long parentId, Map<Long, String> orgNameMap, Map<Long, Long> memberCountMap) {
        List<OrganizationTreeVO> tree = new ArrayList<>();

        for (Organization org : organizations) {
            if (Objects.equals(org.getParentId(), parentId)) {
                OrganizationTreeVO node = convertToTreeNode(org, orgNameMap, memberCountMap);
                // 递归查询子节点
                node.setChildren(buildTree(organizations, org.getId(), orgNameMap, memberCountMap));
                tree.add(node);
            }
        }

        return tree;
    }

    /**
     * 转换为树节点VO
     */
    private OrganizationTreeVO convertToTreeNode(Organization org, Map<Long, String> orgNameMap, Map<Long, Long> memberCountMap) {
        OrganizationTreeVO node = new OrganizationTreeVO();
        node.setId(org.getId());
        node.setOrgCode(org.getOrgCode());
        node.setOrgName(org.getOrgName());
        node.setOrgType(org.getOrgType());
        node.setParentId(org.getParentId());
        // 设置父组织名称
        if (org.getParentId() != null && org.getParentId() > 0) {
            node.setParentName(orgNameMap.get(org.getParentId()));
        }
        node.setLevel(org.getLevel());
        node.setSortOrder(org.getSortOrder());
        node.setStatus(org.getStatus());
        node.setLeaderName(org.getLeaderName());
        node.setMemberCount(memberCountMap.getOrDefault(org.getId(), 0L).intValue());
        return node;
    }

    /**
     * 批量统计各组织的成员数量
     */
    private Map<Long, Long> batchCountMembers(List<Organization> organizations) {
        List<Long> orgIds = organizations.stream()
                .map(Organization::getId)
                .collect(Collectors.toList());
        if (orgIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> countList = employeeMapper.countByOrgIds(orgIds);
        Map<Long, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : countList) {
            Long orgId = ((Number) row.get("orgId")).longValue();
            Long count = ((Number) row.get("count")).longValue();
            countMap.put(orgId, count);
        }
        return countMap;
    }

    /**
     * 按树展示顺序（前序遍历）拉平组织ID
     */
    private void flattenTreeIds(List<OrganizationTreeVO> nodes, List<Long> orderedIds) {
        for (OrganizationTreeVO node : nodes) {
            orderedIds.add(node.getId());
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                flattenTreeIds(node.getChildren(), orderedIds);
            }
        }
    }

    // ==================== 导入导出相关方法 ====================

    /** 错误文件存储目录 */
    private static final String ERROR_FILE_DIR = System.getProperty("java.io.tmpdir") + "/org-import-errors/";

    /** 模板表头 */
    private static final String[] TEMPLATE_HEADERS = {
            "组织编码\n(必填，唯一)",
            "组织名称\n(必填)",
            "组织类型\n(必填，见说明行)",
            "父组织编码\n(留空为顶级)",
            "负责人",
            "联系电话",
            "地址",
            "排序\n(数字)",
            "状态\n(active/inactive)"
    };

    /** 列宽 */
    private static final int[] COLUMN_WIDTHS = {20, 25, 15, 20, 12, 15, 30, 10, 15};

    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("组织导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            // 使用 POI 直接创建模板
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("组织数据");

            // 创建样式
            // 说明行样式（浅蓝色背景）
            CellStyle tipStyle = workbook.createCellStyle();
            tipStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            tipStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tipStyle.setAlignment(HorizontalAlignment.LEFT);
            tipStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            tipStyle.setWrapText(true);
            Font tipFont = workbook.createFont();
            tipFont.setFontName("微软雅黑");
            tipFont.setFontHeightInPoints((short) 10);
            tipStyle.setFont(tipFont);

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            Font headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 示例数据样式（红色文字）
            CellStyle sampleStyle = workbook.createCellStyle();
            sampleStyle.setAlignment(HorizontalAlignment.LEFT);
            sampleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            sampleStyle.setBorderTop(BorderStyle.THIN);
            sampleStyle.setBorderBottom(BorderStyle.THIN);
            sampleStyle.setBorderLeft(BorderStyle.THIN);
            sampleStyle.setBorderRight(BorderStyle.THIN);
            Font sampleFont = workbook.createFont();
            sampleFont.setFontName("微软雅黑");
            sampleFont.setFontHeightInPoints((short) 10);
            sampleFont.setColor(IndexedColors.RED.getIndex());
            sampleStyle.setFont(sampleFont);

            int rowNum = 0;

            // 第1行：说明行
            Row tipRow = sheet.createRow(rowNum++);
            tipRow.setHeightInPoints(30);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】前两行为示例数据（红色文字，编码以#开头），导入时自动跳过。组织编码必填且唯一，重复时将覆盖更新已有组织。状态：active(启用)、inactive(停用)。\n组织类型：" + getOrgTypeDesc() + "。");
            tipCell.setCellStyle(tipStyle);
            // 合并单元格
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

            // 第2行：表头
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(45);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, COLUMN_WIDTHS[i] * 256);
            }

            // 第3-4行：示例数据（红色文字）
            String[][] sampleData = {
                    {"#GROUP-001", "智慧食安集团（示例）", "group", "", "张三", "13800138000", "北京市海淀区科技园区", "1", "active"},
                    {"#COMP-001", "华东区分公司（示例）", "company", "#GROUP-001", "李四", "13900139000", "上海市浦东新区商务中心", "2", "active"}
            };

            for (String[] data : sampleData) {
                Row dataRow = sheet.createRow(rowNum++);
                for (int i = 0; i < data.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(data[i]);
                    cell.setCellStyle(sampleStyle);
                }
            }

            // 写入响应流
            workbook.write(response.getOutputStream());
            workbook.close();

        } catch (IOException e) {
            log.error("下载导入模板失败", e);
            throw new BizException("下载导入模板失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationImportResultDTO importOrganizations(MultipartFile file) {
        try {
            // 读取Excel文件
            // 注意：由于模板第1行是说明行，第2行是表头，需要从第3行开始读取数据
            // 使用自定义读取方式，跳过说明行
            List<OrganizationImportDTO> importList = EasyExcel.read(file.getInputStream())
                    .head(OrganizationImportDTO.class)
                    .sheet()
                    .headRowNumber(2)  // 表头占2行（包含说明行），从第3行开始读数据
                    .doReadSync();

            if (importList.isEmpty()) {
                throw new BizException("导入文件为空");
            }

            if (importList.size() > 5000) {
                throw new BizException("单次导入不能超过5000行，当前" + importList.size() + "行");
            }

            int total = 0;  // 实际导入数量（不含示例数据）
            int successCount = 0;
            int failCount = 0;
            List<OrganizationImportDTO> errorList = new ArrayList<>();

            // 构建现有组织编码到ID的映射
            Map<String, Long> existingOrgCodeMap = buildOrgCodeMap();
            // 停用组织的编码集合（用于校验父组织状态）
            Set<String> inactiveOrgCodes = buildInactiveOrgCodes();
            // 当前用户可管理的组织编码集合（用于数据权限校验）
            Set<String> manageableOrgCodes = buildManageableOrgCodes();
            // 跟踪本次导入新创建的组织编码（用于处理同一文件中的父子关系）
            Map<String, Long> newOrgCodeMap = new ConcurrentHashMap<>();
            // 跟踪文件内同一父组织编码下的组织名称（去空格），用于同父级名称唯一校验
            Map<String, Set<String>> fileNameSetByParentCode = new HashMap<>();
            // 构建数据库中每个父级ID下的组织名称集合（去空格），用于同父级名称唯一校验
            Map<Long, Set<String>> dbNameSetByParentId = buildDbOrgNameSetByParentId();

            // 获取有效的组织类型（从字典分类读取）
            Set<String> validOrgTypes = getEnabledOrgTypeCodes();

            // 处理每一行数据
            for (int i = 0; i < importList.size(); i++) {
                OrganizationImportDTO dto = importList.get(i);
                dto.setRowNum(i + 3); // Excel行号（从1开始，加上说明行和表头）

                // 跳过示例数据（orgCode以#开头的是示例数据）
                if (dto.getOrgCode() != null && dto.getOrgCode().startsWith("#")) {
                    continue;
                }

                // 统计实际导入数量
                total++;

                try {
                    // 校验数据
                    String error = validateImportData(dto, existingOrgCodeMap, newOrgCodeMap, validOrgTypes, inactiveOrgCodes, manageableOrgCodes, fileNameSetByParentCode, dbNameSetByParentId);
                    if (error != null) {
                        dto.setErrorMessage(error);
                        dto.setSuccess(false);
                        errorList.add(dto);
                        failCount++;
                        continue;
                    }

                    // 确定父组织ID
                    Long parentId = 0L;
                    int parentLevel = 0;
                    if (StrUtil.isNotBlank(dto.getParentOrgCode())) {
                        // 先从本次导入的新组织中查找，再从现有组织中查找
                        parentId = newOrgCodeMap.getOrDefault(
                                dto.getParentOrgCode(),
                                existingOrgCodeMap.getOrDefault(dto.getParentOrgCode(), 0L)
                        );
                    }
                    // 获取父组织层级
                    if (parentId > 0) {
                        Organization parentOrg = organizationMapper.selectById(parentId);
                        if (parentOrg != null) {
                            parentLevel = parentOrg.getLevel();
                        }
                    }

                    // 判断是新增还是覆盖更新（编码已存在于数据库）
                    Long existingId = existingOrgCodeMap.get(dto.getOrgCode());
                    if (existingId != null) {
                        // 覆盖更新已有组织
                        Organization org = organizationMapper.selectById(existingId);
                        if (org != null) {
                            // 层级深度校验：父级变更时检查子树深度
                            if (!org.getParentId().equals(parentId) && parentId > 0) {
                                try {
                                    validateSubtreeDepth(org.getId(), parentLevel);
                                } catch (BizException e) {
                                    dto.setErrorMessage(e.getMessage());
                                    dto.setSuccess(false);
                                    errorList.add(dto);
                                    failCount++;
                                    continue;
                                }
                            }
                            org.setOrgName(dto.getOrgName());
                            org.setOrgType(dto.getOrgType());
                            org.setParentId(parentId);
                            org.setLeaderName(dto.getLeaderName());
                            org.setContactPhone(dto.getContactPhone());
                            org.setAddress(dto.getAddress());
                            org.setSortOrder(parseSortOrder(dto.getSortOrder()));
                            String oldStatus = org.getStatus();
                            if (StrUtil.isNotBlank(dto.getStatus())) {
                                org.setStatus(dto.getStatus());
                            }
                            // 停用时与页面停用做相同校验（含所有子组织）
                            if ("inactive".equals(org.getStatus()) && !"inactive".equals(oldStatus)) {
                                try {
                                    validateDeactivateOrgTree(org.getId());
                                } catch (BizException e) {
                                    dto.setErrorMessage(e.getMessage());
                                    dto.setSuccess(false);
                                    errorList.add(dto);
                                    failCount++;
                                    continue;
                                }
                            }
                            calculateLevelAndPath(org, parentId);
                            organizationMapper.updateById(org);
                            // 停用时级联停用所有子组织
                            if ("inactive".equals(org.getStatus()) && !"inactive".equals(oldStatus)) {
                                Set<String> descendantCodes = deactivateDescendants(org.getId());
                                inactiveOrgCodes.addAll(descendantCodes);
                            }
                            newOrgCodeMap.put(dto.getOrgCode(), org.getId());
                        }
                    } else {
                        // 新增组织 — 校验层级深度
                        int newLevel = parentLevel + 1;
                        if (newLevel > MAX_LEVEL) {
                            dto.setErrorMessage("组织层级不能超过" + MAX_LEVEL + "层");
                            dto.setSuccess(false);
                            errorList.add(dto);
                            failCount++;
                            continue;
                        }
                        Organization org = new Organization();
                        org.setOrgCode(dto.getOrgCode());
                        org.setOrgName(dto.getOrgName());
                        org.setOrgType(dto.getOrgType());
                        org.setParentId(parentId);
                        org.setLeaderName(dto.getLeaderName());
                        org.setContactPhone(dto.getContactPhone());
                        org.setAddress(dto.getAddress());
                        // 解析排序值
                        org.setSortOrder(parseSortOrder(dto.getSortOrder()));
                        org.setStatus(StrUtil.isNotBlank(dto.getStatus()) ? dto.getStatus() : "active");
                        org.setTenantId(UserContext.getTenantId());
                        // 计算层级和路径
                        calculateLevelAndPath(org, parentId);
                        // 保存组织
                        organizationMapper.insert(org);
                        // 记录新创建的组织编码
                        newOrgCodeMap.put(dto.getOrgCode(), org.getId());
                    }

                    // 文件内停用的组织，后续行作为父组织时应拦截
                    String rowStatus = StrUtil.isNotBlank(dto.getStatus()) ? dto.getStatus() : "active";
                    if ("inactive".equals(rowStatus)) {
                        inactiveOrgCodes.add(dto.getOrgCode());
                    }
                    // 记录文件内同父级下的组织名称（去空格），供后续行校验
                    String fileParentCode = StrUtil.isNotBlank(dto.getParentOrgCode()) ? dto.getParentOrgCode() : "";
                    fileNameSetByParentCode.computeIfAbsent(fileParentCode, k -> new HashSet<>()).add(dto.getOrgName().replaceAll("\\s+", ""));

                    dto.setSuccess(true);
                    successCount++;

                } catch (Exception e) {
                    log.error("导入组织失败，行号：{}，错误：{}", dto.getRowNum(), e.getMessage());
                    dto.setErrorMessage("导入失败：" + e.getMessage());
                    dto.setSuccess(false);
                    errorList.add(dto);
                    failCount++;
                }
            }

            // 生成错误文件
            String errorFileUrl = null;
            if (!errorList.isEmpty()) {
                errorFileUrl = generateErrorFile(errorList);
            }

            OrganizationImportResultDTO result = new OrganizationImportResultDTO(
                    total,
                    successCount,
                    failCount,
                    !errorList.isEmpty(),
                    errorFileUrl
            );
            // 构建失败行详情
            Map<String, Object> afterDataMap = new HashMap<>();
            afterDataMap.put("total", result.getTotal());
            afterDataMap.put("successCount", result.getSuccessCount());
            afterDataMap.put("failCount", result.getFailCount());
            if (!errorList.isEmpty()) {
                List<Map<String, Object>> failureDetails = errorList.stream().map(err -> {
                    Map<String, Object> f = new HashMap<>();
                    f.put("row", err.getRowNum());
                    f.put("orgCode", err.getOrgCode());
                    f.put("orgName", err.getOrgName());
                    f.put("error", err.getErrorMessage());
                    return f;
                }).collect(Collectors.toList());
                afterDataMap.put("failures", failureDetails);
            }

            auditLogService.log(AuditModule.SYS_ORGANIZATION, AuditOperationType.IMPORT, null, null,
                "导入组织：共" + result.getTotal() + "条，成功" + result.getSuccessCount() + "条，失败" + result.getFailCount() + "条",
                null, JSONUtil.toJsonStr(afterDataMap));
            return result;

        } catch (IOException e) {
            log.error("读取导入文件失败", e);
            throw new BizException("读取导入文件失败，请检查文件是否为正确的 Excel 格式");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("导入组织失败，文件格式或内容异常", e);
            throw new BizException("导入失败，请确认使用正确的导入模板");
        }
    }

    @Override
    public void exportOrganizations(String orgType, String status, String keyword, Boolean includeChildren, HttpServletResponse response) {
        String operationResult = "success";
        String errorMsg = null;
        int exportCount = 0;

        try {
            // 与当前树查询保持一致：先按相同筛选得到树，再按页面展示顺序展开为导出列表
            List<OrganizationTreeVO> tree = getTree(orgType, status, keyword, includeChildren);
            List<Long> orderedIds = new ArrayList<>();
            flattenTreeIds(tree, orderedIds);

            List<Organization> organizations;
            if (orderedIds.isEmpty()) {
                organizations = Collections.emptyList();
            } else {
                Map<Long, Organization> orgMap = organizationMapper.selectBatchIds(orderedIds).stream()
                        .collect(Collectors.toMap(Organization::getId, org -> org));
                organizations = orderedIds.stream()
                        .map(orgMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
            exportCount = organizations.size();

            // 构建ID到编码的映射（用于获取父组织编码），补全不在查询结果中的父组织
            Set<Long> parentIds = organizations.stream()
                    .map(Organization::getParentId)
                    .filter(id -> id != null && id > 0)
                    .collect(Collectors.toSet());
            Map<Long, String> orgIdToCodeMap = new HashMap<>();
            // 先放入导出列表自身的编码
            organizations.forEach(org -> orgIdToCodeMap.put(org.getId(), org.getOrgCode()));
            // 再补全不在列表中的父组织编码
            parentIds.removeAll(orgIdToCodeMap.keySet());
            if (!parentIds.isEmpty()) {
                organizationMapper.selectBatchIds(parentIds).forEach(
                        parent -> orgIdToCodeMap.put(parent.getId(), parent.getOrgCode()));
            }

            // 使用 POI 生成导出文件，格式与模板一致
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("组织数据");

            // 创建样式
            // 说明行样式
            CellStyle tipStyle = workbook.createCellStyle();
            tipStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            tipStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tipStyle.setAlignment(HorizontalAlignment.LEFT);
            tipStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            tipStyle.setWrapText(true);
            Font tipFont = workbook.createFont();
            tipFont.setFontName("微软雅黑");
            tipFont.setFontHeightInPoints((short) 10);
            tipStyle.setFont(tipFont);

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            Font headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 数据样式
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.LEFT);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            Font dataFont = workbook.createFont();
            dataFont.setFontName("微软雅黑");
            dataFont.setFontHeightInPoints((short) 10);
            dataStyle.setFont(dataFont);

            int rowNum = 0;

            // 第1行：说明行
            Row tipRow = sheet.createRow(rowNum++);
            tipRow.setHeightInPoints(30);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】组织编码必填且唯一，重复时将覆盖更新已有组织。状态：active(启用)、inactive(停用)。\n组织类型：" + getOrgTypeDesc() + "。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

            // 第2行：表头
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(45);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, COLUMN_WIDTHS[i] * 256);
            }

            // 数据行
            for (Organization org : organizations) {
                Row dataRow = sheet.createRow(rowNum++);
                String parentOrgCode = org.getParentId() != null && org.getParentId() > 0
                        ? orgIdToCodeMap.getOrDefault(org.getParentId(), "") : "";

                String[] values = {
                        org.getOrgCode(),
                        org.getOrgName(),
                        org.getOrgType(),
                        parentOrgCode,
                        org.getLeaderName() != null ? org.getLeaderName() : "",
                        org.getContactPhone() != null ? org.getContactPhone() : "",
                        org.getAddress() != null ? org.getAddress() : "",
                        org.getSortOrder() != null ? String.valueOf(org.getSortOrder()) : "0",
                        org.getStatus()
                };

                for (int i = 0; i < values.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String fileName = URLEncoder.encode("组织数据导出_" + timestamp, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            workbook.write(response.getOutputStream());
            workbook.close();

        } catch (Exception e) {
            operationResult = "failure";
            errorMsg = e.getMessage();
            log.error("导出组织失败", e);
            throw new BizException("导出组织失败");
        } finally {
            try {
                String desc = "success".equals(operationResult)
                    ? "导出组织数据（" + exportCount + "条）"
                    : "导出组织数据失败";
                log.info("导出组织审计日志：result={}, desc={}, userId={}", operationResult, desc, UserContext.getUserId());
                auditLogService.log(AuditModule.SYS_ORGANIZATION, AuditOperationType.EXPORT,
                    null, null, desc, null, null, operationResult, errorMsg);
            } catch (Exception logEx) {
                log.error("导出审计日志记录失败: {}", logEx.getMessage(), logEx);
            }
        }
    }

    @Override
    public void downloadErrorFile(String fileName, HttpServletResponse response) {
        try {
            File file = new File(ERROR_FILE_DIR + fileName);
            if (!file.exists()) {
                throw new BizException("错误文件不存在或已过期");
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            try (FileInputStream fis = new FileInputStream(file)) {
                fis.transferTo(response.getOutputStream());
            }

        } catch (IOException e) {
            log.error("下载错误文件失败", e);
            throw new BizException("下载错误文件失败");
        }
    }

    /**
     * 解析排序值
     */
    private Integer parseSortOrder(String sortOrder) {
        if (StrUtil.isBlank(sortOrder)) {
            return 0;
        }
        try {
            return Integer.parseInt(sortOrder.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 构建组织编码到ID的映射
     */
    private Map<String, Long> buildOrgCodeMap() {
        List<Organization> all = organizationMapper.selectList(null);
        return all.stream().collect(Collectors.toMap(Organization::getOrgCode, Organization::getId));
    }

    /**
     * 构建停用组织的编码集合
     */
    private Set<String> buildInactiveOrgCodes() {
        List<Organization> all = organizationMapper.selectList(null);
        return all.stream()
                .filter(org -> "inactive".equals(org.getStatus()))
                .map(Organization::getOrgCode)
                .collect(Collectors.toSet());
    }

    /**
     * 构建当前用户可管理的组织编码集合（null 表示全部权限）
     */
    private Set<String> buildManageableOrgCodes() {
        Set<Long> manageableOrgIds = resolveManageableOrgIds();
        if (manageableOrgIds == null) {
            return null; // 全部权限
        }
        if (manageableOrgIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Organization> orgs = organizationMapper.selectBatchIds(manageableOrgIds);
        return orgs.stream().map(Organization::getOrgCode).collect(Collectors.toSet());
    }

    /**
     * 校验导入数据
     */
    private String validateImportData(OrganizationImportDTO dto, Map<String, Long> existingMap, Map<String, Long> newMap, Set<String> validOrgTypes, Set<String> inactiveOrgCodes, Set<String> manageableOrgCodes, Map<String, Set<String>> fileNameSetByParentCode, Map<Long, Set<String>> dbNameSetByParentId) {
        // 校验必填字段
        if (StrUtil.isBlank(dto.getOrgCode())) {
            return "组织编码不能为空";
        }
        if (StrUtil.isBlank(dto.getOrgName())) {
            return "组织名称不能为空";
        }
        if (StrUtil.isBlank(dto.getOrgType())) {
            return "组织类型不能为空";
        }

        // 校验组织类型（从字典分类读取有效值）
        if (!validOrgTypes.contains(dto.getOrgType())) {
            return "组织类型无效，有效值：" + String.join("、", validOrgTypes);
        }

        // 校验状态
        if (StrUtil.isNotBlank(dto.getStatus()) && !Arrays.asList("active", "inactive").contains(dto.getStatus())) {
            return "状态无效，必须为：active 或 inactive";
        }

        // 校验编码唯一性（仅校验同一导入文件内重复）
        if (newMap.containsKey(dto.getOrgCode())) {
            return "文件内组织编码重复：" + dto.getOrgCode();
        }

        // 校验同父级下组织名称去空格后唯一
        String parentCode = StrUtil.isNotBlank(dto.getParentOrgCode()) ? dto.getParentOrgCode() : "";
        String trimmedName = dto.getOrgName().replaceAll("\\s+", "");
        // 文件内去重
        Set<String> fileNameSet = fileNameSetByParentCode.computeIfAbsent(parentCode, k -> new HashSet<>());
        if (fileNameSet.contains(trimmedName)) {
            return "同一父组织下已存在同名组织：" + dto.getOrgName();
        }
        // 数据库去重（仅新增时或覆盖更新且名称/父级变更时需检查）
        Long existingOrgId = existingMap.get(dto.getOrgCode());
        if (existingOrgId == null) {
            // 新增：检查目标父级下是否已存在同名组织
            Long dbParentId = resolveImportParentId(parentCode, existingMap, newMap);
            Set<String> dbNameSet = dbNameSetByParentId.getOrDefault(dbParentId, Collections.emptySet());
            if (dbNameSet.contains(trimmedName)) {
                return "同一父组织下已存在同名组织：" + dto.getOrgName();
            }
        } else {
            // 覆盖更新：检查名称或父级是否变更
            Organization existingOrg = organizationMapper.selectById(existingOrgId);
            if (existingOrg != null) {
                Long oldParentId = existingOrg.getParentId();
                Long newParentId = StrUtil.isNotBlank(dto.getParentOrgCode())
                        ? resolveImportParentId(dto.getParentOrgCode(), existingMap, newMap)
                        : oldParentId;
                String oldTrimmedName = existingOrg.getOrgName().replaceAll("\\s+", "");
                boolean parentChanged = !Objects.equals(oldParentId, newParentId);
                boolean nameChanged = !trimmedName.equals(oldTrimmedName);
                if (parentChanged || nameChanged) {
                    Set<String> dbNameSet = dbNameSetByParentId.getOrDefault(newParentId, Collections.emptySet());
                    if (dbNameSet.contains(trimmedName)) {
                        return "同一父组织下已存在同名组织：" + dto.getOrgName();
                    }
                }
            }
        }

        // 数据权限校验：覆盖更新的组织需在权限范围内
        if (manageableOrgCodes != null && existingMap.containsKey(dto.getOrgCode())) {
            if (!manageableOrgCodes.contains(dto.getOrgCode())) {
                return "无权限修改该组织：" + dto.getOrgCode();
            }
        }

        // 校验父组织是否存在且未停用
        if (StrUtil.isNotBlank(dto.getParentOrgCode())) {
            if (!existingMap.containsKey(dto.getParentOrgCode()) && !newMap.containsKey(dto.getParentOrgCode())) {
                return "父组织编码不存在：" + dto.getParentOrgCode();
            }
            if (inactiveOrgCodes.contains(dto.getParentOrgCode())) {
                return "父组织已停用：" + dto.getParentOrgCode();
            }
            // 数据权限校验：父组织需在权限范围内（数据库中已有的父组织）
            if (manageableOrgCodes != null && existingMap.containsKey(dto.getParentOrgCode())) {
                if (!manageableOrgCodes.contains(dto.getParentOrgCode())) {
                    return "无权限将组织挂载到该父组织下：" + dto.getParentOrgCode();
                }
            }

            // 循环引用校验：父组织不能是自身
            if (dto.getParentOrgCode().equals(dto.getOrgCode())) {
                return "父组织不能是自身";
            }
            // 循环引用校验：父组织不能是当前组织的子孙（仅更新已有组织时需要检查）
            if (existingOrgId != null) {
                Long parentOrgId = newMap.getOrDefault(
                        dto.getParentOrgCode(),
                        existingMap.getOrDefault(dto.getParentOrgCode(), 0L)
                );
                if (parentOrgId > 0 && isDescendant(parentOrgId, existingOrgId)) {
                    return "父组织不能是当前组织的子组织";
                }
            }
        }

        return null;
    }

    /**
     * 计算层级和路径
     */
    private void calculateLevelAndPath(Organization org, Long parentId) {
        if (parentId != null && parentId > 0) {
            Organization parent = organizationMapper.selectById(parentId);
            if (parent != null) {
                org.setLevel(parent.getLevel() + 1);
                org.setPath(parent.getPath() + org.getOrgName() + "/");
            } else {
                org.setLevel(1);
                org.setPath("/" + org.getOrgName() + "/");
            }
        } else {
            org.setLevel(1);
            org.setPath("/" + org.getOrgName() + "/");
        }
    }

    /**
     * 生成错误文件
     */
    private String generateErrorFile(List<OrganizationImportDTO> errorList) {
        try {
            // 确保目录存在
            File dir = new File(ERROR_FILE_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String fileName = "org_import_errors_" + System.currentTimeMillis() + ".xlsx";
            String filePath = ERROR_FILE_DIR + fileName;

            // 使用 POI 生成错误文件，格式与模板一致
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("导入失败数据");

            // 创建样式
            // 说明行样式
            CellStyle tipStyle = workbook.createCellStyle();
            tipStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            tipStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tipStyle.setAlignment(HorizontalAlignment.LEFT);
            tipStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            tipStyle.setWrapText(true);
            Font tipFont = workbook.createFont();
            tipFont.setFontName("微软雅黑");
            tipFont.setFontHeightInPoints((short) 10);
            tipStyle.setFont(tipFont);

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            Font headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 错误数据样式（红色文字）
            CellStyle errorStyle = workbook.createCellStyle();
            errorStyle.setAlignment(HorizontalAlignment.LEFT);
            errorStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            errorStyle.setBorderTop(BorderStyle.THIN);
            errorStyle.setBorderBottom(BorderStyle.THIN);
            errorStyle.setBorderLeft(BorderStyle.THIN);
            errorStyle.setBorderRight(BorderStyle.THIN);
            Font errorFont = workbook.createFont();
            errorFont.setFontName("微软雅黑");
            errorFont.setFontHeightInPoints((short) 10);
            errorFont.setColor(IndexedColors.RED.getIndex());
            errorStyle.setFont(errorFont);

            int rowNum = 0;

            // 第1行：说明行
            Row tipRow = sheet.createRow(rowNum++);
            tipRow.setHeightInPoints(30);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】以下数据导入失败，请根据失败原因修改后重新导入。\n组织类型：" + getOrgTypeDesc() + "。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

            // 第2行：表头（增加失败原因列）
            String[] errorHeaders = {
                    "组织编码\n(必填，唯一)",
                    "组织名称\n(必填)",
                    "组织类型\n(必填，见说明行)",
                    "父组织编码\n(留空为顶级)",
                    "负责人",
                    "联系电话",
                    "地址",
                    "排序\n(数字)",
                    "状态\n(active/inactive)",
                    "失败原因"
            };
            int[] errorWidths = {20, 25, 15, 20, 12, 15, 30, 10, 15, 40};

            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(45);
            for (int i = 0; i < errorHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(errorHeaders[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, errorWidths[i] * 256);
            }

            // 数据行（红色文字）
            for (OrganizationImportDTO dto : errorList) {
                Row dataRow = sheet.createRow(rowNum++);
                String[] values = {
                        dto.getOrgCode() != null ? dto.getOrgCode() : "",
                        dto.getOrgName() != null ? dto.getOrgName() : "",
                        dto.getOrgType() != null ? dto.getOrgType() : "",
                        dto.getParentOrgCode() != null ? dto.getParentOrgCode() : "",
                        dto.getLeaderName() != null ? dto.getLeaderName() : "",
                        dto.getContactPhone() != null ? dto.getContactPhone() : "",
                        dto.getAddress() != null ? dto.getAddress() : "",
                        dto.getSortOrder() != null ? dto.getSortOrder() : "",
                        dto.getStatus() != null ? dto.getStatus() : "",
                        dto.getErrorMessage() != null ? dto.getErrorMessage() : ""
                };

                for (int i = 0; i < values.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(errorStyle);
                }
            }

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.close();

            // 返回下载URL
            return "/api/v1/sys/organizations/import/errors/" + fileName;

        } catch (Exception e) {
            log.error("生成错误文件失败", e);
            return null;
        }
    }
}
