package com.xykj.sys.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.ForceLogoutHelper;
import com.xykj.sys.dto.EmployeeCreateDTO;
import com.xykj.sys.dto.EmployeeImportDTO;
import com.xykj.sys.dto.EmployeeImportResultDTO;
import com.xykj.sys.dto.EmployeeQueryDTO;
import com.xykj.sys.dto.EmployeeUpdateDTO;
import com.xykj.sys.entity.Employee;
import com.xykj.sys.entity.Organization;
import com.xykj.sys.entity.Role;
import com.xykj.sys.entity.User;
import com.xykj.sys.mapper.EmployeeMapper;
import com.xykj.sys.mapper.OrganizationMapper;
import com.xykj.sys.mapper.RoleMapper;
import com.xykj.sys.mapper.UserMapper;
import com.xykj.sys.service.DictCategoryService;
import com.xykj.sys.service.EmployeeService;
import com.xykj.sys.service.UserService;
import com.xykj.sys.vo.DictCategoryOptionVO;
import com.xykj.sys.vo.EmployeeVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 员工服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final OrganizationMapper organizationMapper;
    private final UserService userService;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final DictCategoryService dictCategoryService;
    private final DataScopeService dataScopeService;
    private final ForceLogoutHelper forceLogoutHelper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    @DataScope
    public PageResult<EmployeeVO> list(EmployeeQueryDTO query) {
        // 构建查询条件
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(Employee::getEmployeeNo, query.getKeyword())
                        .or().like(Employee::getRealName, query.getKeyword())
                        .or().like(Employee::getPhone, query.getKeyword()))
                .eq(query.getOrgId() != null, Employee::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Employee::getOrgId, query.getOrgIds())
                .eq(StrUtil.isNotBlank(query.getStatus()), Employee::getStatus, query.getStatus())
                .orderByDesc(Employee::getCreatedAt);

        if (StrUtil.isNotBlank(query.getAccountStatus())) {
            List<Long> userIds = userMapper.selectIdsByStatus(query.getAccountStatus());
            if (userIds == null || userIds.isEmpty()) {
                wrapper.isNull(Employee::getId);
            } else {
                wrapper.in(Employee::getUserId, userIds);
            }
        }

        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(Employee::getId);
        }

        // 分页查询
        IPage<Employee> page = employeeMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        // 转换为VO
        List<EmployeeVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 填充组织名称
        fillOrgNames(voList);

        // 填充角色名称
        fillRoleNames(voList);

        return PageResult.of(page, voList);
    }

    @Override
    public EmployeeVO getDetail(Long id) {
        Employee employee = getEmployeeById(id);
        EmployeeVO vo = convertToVO(employee);

        // 查询组织名称
        if (employee.getOrgId() != null) {
            Organization org = organizationMapper.selectById(employee.getOrgId());
            if (org != null) {
                vo.setOrgName(org.getOrgName());
            }
        }

        // 查询角色信息
        if (employee.getUserId() != null) {
            List<Long> roleIds = roleMapper.selectRoleIdsByUserId(employee.getUserId());
            vo.setRoleIds(roleIds);

            if (!roleIds.isEmpty()) {
                List<Role> roles = roleMapper.selectRolesByUserId(employee.getUserId());
                String roleNames = roles.stream()
                        .map(Role::getRoleName)
                        .collect(Collectors.joining(","));
                vo.setRoleNames(roleNames);
            }
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(EmployeeCreateDTO dto) {
        // 生成员工工号
        String employeeNo = dto.getEmployeeNo();
        if (StrUtil.isBlank(employeeNo)) {
            employeeNo = generateEmployeeNo();
        } else {
            // 校验工号唯一性
            Long count = employeeMapper.countByEmployeeNoExcludeId(employeeNo, null);
            if (count > 0) {
                throw BizException.conflict("员工工号已存在");
            }
        }

        // 校验组织是否存在
        if (dto.getOrgId() != null) {
            Organization org = organizationMapper.selectById(dto.getOrgId());
            if (org == null) {
                throw BizException.notFound("所属组织不存在");
            }
        }

        Employee employee = null;
        User user = null;

        try {
            // 创建员工实体
            employee = new Employee();
            employee.setEmployeeNo(employeeNo);
            employee.setRealName(dto.getRealName());
            employee.setGender(convertGenderToInt(dto.getGender()));
            employee.setIdCard(dto.getIdCard());
            employee.setPhone(dto.getPhone());
            employee.setEmail(dto.getEmail());
            employee.setOrgId(dto.getOrgId() != null ? dto.getOrgId() : 1L);
            employee.setDepartment(dto.getDepartment());
            employee.setPosition(dto.getPosition());
            employee.setHireDate(dto.getHireDate());
            employee.setStatus(dto.getStatus() != null ? dto.getStatus() : "active");
            employee.setHealthCertStatus("pending");
            employee.setFaceEnrolled(0);
            employee.setTenantId(UserContext.getTenantId());
            employee.setRemark(dto.getRemark());

            employeeMapper.insert(employee);

            // 自动创建用户账号
            user = userService.createForEmployee(
                    employee.getEmployeeNo(),  // 用户名=员工编号
                    null,  // 使用默认密码123456
                    employee.getRealName(),
                    employee.getPhone(),
                    employee.getEmail(),
                    employee.getOrgId(),
                    employee.getTenantId(),
                    employee.getGender(),  // 性别
                    dto.getAccountStatus()
            );

            // 关联userId到员工表
            employee.setUserId(user.getId());
            employeeMapper.updateById(employee);

            // 处理角色分配
            if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty() && user.getId() != null) {
                for (Long roleId : dto.getRoleIds()) {
                    roleMapper.insertRoleMember(roleId, user.getId());
                }
            }

            log.info("创建员工成功: id={}, employeeNo={}, userId={}", employee.getId(), employee.getEmployeeNo(), user.getId());

            // 审计日志 — 多表操作手动记录
            Map<String, Object> afterDataMap = new HashMap<>();
            afterDataMap.put("employee", employee);
            afterDataMap.put("user", user);
            afterDataMap.put("roleIds", dto.getRoleIds());
            auditLogService.log(AuditModule.SYS_EMPLOYEE, AuditOperationType.CREATE, employee.getId(), employee.getEmployeeNo(),
                "新增员工：" + employee.getRealName() + "（" + employee.getEmployeeNo() + "）",
                null, JSONUtil.toJsonStr(afterDataMap), "success", null);

        } catch (Exception ex) {
            auditLogService.log(AuditModule.SYS_EMPLOYEE, AuditOperationType.CREATE,
                employee != null ? employee.getId() : null, employeeNo,
                "新增员工失败：" + (dto.getRealName() != null ? dto.getRealName() : ""),
                null, null, "failure", ex.getMessage());
            throw ex;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", employee.getId());
        result.put("empNo", employee.getEmployeeNo());
        result.put("name", employee.getRealName());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(Long id, EmployeeUpdateDTO dto) {
        Employee employee = getEmployeeById(id);

        // 禁止将自己状态改为离职
        if ("left".equals(dto.getStatus()) && employee.getUserId() != null
                && employee.getUserId().equals(UserContext.getUserId())) {
            throw BizException.validationFailed("不能将当前登录账号的员工状态改为离职");
        }

        // 捕获修改前数据（多表）— 在 try 之前捕获
        Employee employeeBefore = BeanUtil.copyProperties(employee, Employee.class);
        User userBefore = employee.getUserId() != null ? userService.getById(employee.getUserId()) : null;
        List<Long> roleIdsBefore = employee.getUserId() != null ? roleMapper.selectRoleIdsByUserId(employee.getUserId()) : null;

        Map<String, Object> beforeDataMap = new HashMap<>();
        beforeDataMap.put("employee", employeeBefore);
        beforeDataMap.put("user", userBefore);
        beforeDataMap.put("roleIds", roleIdsBefore);

        try {
            // 更新字段
            if (StrUtil.isNotBlank(dto.getRealName())) {
                employee.setRealName(dto.getRealName());
            }
            if (dto.getGender() != null) {
                employee.setGender(convertGenderToInt(dto.getGender()));
            }
            if (dto.getIdCard() != null) {
                employee.setIdCard(dto.getIdCard());
            }
            if (StrUtil.isNotBlank(dto.getPhone())) {
                employee.setPhone(dto.getPhone());
            }
            if (dto.getEmail() != null) {
                employee.setEmail(dto.getEmail());
            }
            if (dto.getOrgId() != null) {
                // 校验组织是否存在
                Organization org = organizationMapper.selectById(dto.getOrgId());
                if (org == null) {
                    throw BizException.notFound("所属组织不存在");
                }
                employee.setOrgId(dto.getOrgId());
            }
            if (dto.getDepartment() != null) {
                employee.setDepartment(dto.getDepartment());
            }
            employee.setPosition(StrUtil.isBlank(dto.getPosition()) ? null : dto.getPosition().trim());
            employee.setHireDate(dto.getHireDate());
            if (StrUtil.isNotBlank(dto.getStatus())) {
                employee.setStatus(dto.getStatus());
            }
            if (dto.getRemark() != null) {
                employee.setRemark(dto.getRemark());
            }

            employeeMapper.updateById(employee);

            // 同步更新用户表信息
            if (employee.getUserId() != null) {
                // 校验手机号唯一（账号维度）
                if (StrUtil.isNotBlank(dto.getPhone())) {
                    Long phoneCount = userService.countByPhoneExcludeId(dto.getPhone(), employee.getUserId());
                    if (phoneCount != null && phoneCount > 0) {
                        throw BizException.conflict("手机号已存在");
                    }
                }
                // 校验邮箱唯一（账号维度）
                if (StrUtil.isNotBlank(dto.getEmail())) {
                    Long emailCount = userService.countByEmailExcludeId(dto.getEmail(), employee.getUserId());
                    if (emailCount != null && emailCount > 0) {
                        throw BizException.conflict("邮箱已存在");
                    }
                }

                // 获取转换后的性别值
                Integer genderValue = convertGenderToInt(dto.getGender());

                userService.updateUserInfo(
                        employee.getUserId(),
                        dto.getRealName(),
                        dto.getPhone(),
                        dto.getEmail(),
                        dto.getOrgId(),
                        genderValue
                );
                // 如果修改了账号状态
                if (StrUtil.isNotBlank(dto.getAccountStatus())) {
                    if ("inactive".equals(dto.getAccountStatus())) {
                        validateNoPendingDispatches(id);
                    }
                    userService.updateStatus(employee.getUserId(), dto.getAccountStatus());
                    if ("inactive".equals(dto.getAccountStatus())) {
                        forceLogoutUser(employee.getUserId(), "disabled");
                    } else if ("active".equals(dto.getAccountStatus())) {
                        forceLogoutHelper.clearForceLogout(employee.getUserId());
                    }
                }
            }

            // 员工状态改为离职时，自动禁用账号并解绑告警规则
            if ("left".equals(dto.getStatus()) && employee.getUserId() != null) {
                validateNoPendingDispatches(id);
                userService.updateStatus(employee.getUserId(), "inactive");
                removeFromAlertRules(id);
                forceLogoutUser(employee.getUserId(), "left");
            }

            // 处理角色分配
            if (dto.getRoleIds() != null && employee.getUserId() != null) {
                // 查询现有角色
                List<Long> existingRoleIds = roleMapper.selectRoleIdsByUserId(employee.getUserId());

                // 计算需要新增的角色
                List<Long> toAdd = dto.getRoleIds().stream()
                        .filter(roleId -> !existingRoleIds.contains(roleId))
                        .collect(Collectors.toList());

                // 计算需要删除的角色
                List<Long> toRemove = existingRoleIds.stream()
                        .filter(roleId -> !dto.getRoleIds().contains(roleId))
                        .collect(Collectors.toList());

                // 执行新增
                for (Long roleId : toAdd) {
                    roleMapper.insertRoleMember(roleId, employee.getUserId());
                }

                // 执行删除
                for (Long roleId : toRemove) {
                    roleMapper.deleteRoleMember(roleId, employee.getUserId());
                }
            }

            log.info("更新员工成功: id={}", id);

            // 审计日志 — 成功
            Map<String, Object> afterDataMap = new HashMap<>();
            afterDataMap.put("employee", employee);
            afterDataMap.put("user", employee.getUserId() != null ? userService.getById(employee.getUserId()) : null);
            afterDataMap.put("roleIds", dto.getRoleIds());

            auditLogService.log(AuditModule.SYS_EMPLOYEE, AuditOperationType.UPDATE, id, employee.getEmployeeNo(),
                "编辑员工：" + employee.getRealName() + "（" + employee.getEmployeeNo() + "）",
                JSONUtil.toJsonStr(beforeDataMap), JSONUtil.toJsonStr(afterDataMap), "success", null);

        } catch (Exception ex) {
            if (containsKeyInCauseChain(ex, "uk_user_phone")) {
                throw BizException.conflict("手机号已存在");
            }
            if (containsKeyInCauseChain(ex, "uk_user_email")) {
                throw BizException.conflict("邮箱已存在");
            }
            auditLogService.log(AuditModule.SYS_EMPLOYEE, AuditOperationType.UPDATE, id, employeeBefore.getEmployeeNo(),
                "编辑员工失败：" + employeeBefore.getRealName() + "（" + employeeBefore.getEmployeeNo() + "）",
                JSONUtil.toJsonStr(beforeDataMap), null, "failure", ex.getMessage());
            throw ex;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("name", employee.getRealName());
        if ("left".equals(dto.getStatus())) {
            result.put("message", "员工状态已改为离职，关联账号已自动禁用");
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.SYS_EMPLOYEE,
        operationType = AuditOperationType.STATUS_CHANGE,
        targetId = "#id",
        targetNo = "#entity.employeeNo",
        desc = "('active'.equals(#accountStatus) ? '启用' : '禁用') + '员工账号：' + #entity.realName + '（' + #entity.employeeNo + '）'",
        mapper = EmployeeMapper.class
    )
    public void updateAccountStatus(Long id, String accountStatus) {
        Employee employee = getEmployeeById(id);

        // 禁止禁用自己
        if ("inactive".equals(accountStatus) && employee.getUserId() != null
                && employee.getUserId().equals(UserContext.getUserId())) {
            throw BizException.validationFailed("不能禁用当前登录账号");
        }

        // 离职员工不允许启用账号
        if ("active".equals(accountStatus) && "left".equals(employee.getStatus())) {
            throw BizException.validationFailed("离职员工账号无法启用");
        }

        if (employee.getUserId() == null) {
            throw BizException.validationFailed("该员工未关联账号");
        }

        // 禁用时校验是否有未完成派单
        if ("inactive".equals(accountStatus)) {
            validateNoPendingDispatches(id);
        }

        userService.updateStatus(employee.getUserId(), accountStatus);
        log.info("修改员工账号状态: id={}, accountStatus={}", id, accountStatus);

        // 强制下线或清除标记
        if ("inactive".equals(accountStatus)) {
            forceLogoutUser(employee.getUserId(), "disabled");
        } else if ("active".equals(accountStatus)) {
            forceLogoutHelper.clearForceLogout(employee.getUserId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Employee employee = getEmployeeById(id);

        if (employee.getUserId() != null && employee.getUserId().equals(UserContext.getUserId())) {
            throw BizException.validationFailed("不能删除当前登录账号");
        }

        // 校验未完成派单
        validateNoPendingDispatches(id);

        // 捕获删除前数据（多表）— 在 try 之前捕获
        Map<String, Object> beforeDataMap = new HashMap<>();
        beforeDataMap.put("employee", BeanUtil.copyProperties(employee, Employee.class));
        if (employee.getUserId() != null) {
            beforeDataMap.put("user", userService.getById(employee.getUserId()));
            beforeDataMap.put("roleIds", roleMapper.selectRoleIdsByUserId(employee.getUserId()));
        }

        try {
            // 强制下线被删除的员工
            forceLogoutUser(employee.getUserId(), "deleted");

            // 同步删除用户的角色关联和用户账号
            if (employee.getUserId() != null) {
                // 删除用户的所有角色关联
                roleMapper.deleteUserRolesByUserId(employee.getUserId());
                // 删除用户账号
                userService.deleteById(employee.getUserId());
            }

            employeeMapper.deleteById(id);

            // 审计日志 — 成功
            auditLogService.log(AuditModule.SYS_EMPLOYEE, AuditOperationType.DELETE, id, employee.getEmployeeNo(),
                "删除员工：" + employee.getRealName() + "（" + employee.getEmployeeNo() + "）",
                JSONUtil.toJsonStr(beforeDataMap), null, "success", null);
            log.info("删除员工成功: id={}, employeeNo={}", id, employee.getEmployeeNo());

        } catch (Exception ex) {
            auditLogService.log(AuditModule.SYS_EMPLOYEE, AuditOperationType.DELETE, id, employee.getEmployeeNo(),
                "删除员工失败：" + employee.getRealName() + "（" + employee.getEmployeeNo() + "）",
                JSONUtil.toJsonStr(beforeDataMap), null, "failure", ex.getMessage());
            throw ex;
        }
    }

    @Override
    public String generateEmployeeNo() {
        String employeeNo = employeeMapper.generateEmployeeNo();
        // 如果当天还没有员工，返回第一个工号
        if (StrUtil.isBlank(employeeNo)) {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            employeeNo = "EMP" + dateStr + "0001";
        }
        return employeeNo;
    }

    // ==================== 私有方法 ====================

    private boolean containsKeyInCauseChain(Throwable throwable, String keyName) {
        Throwable current = throwable;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.contains(keyName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 根据ID获取员工
     */
    private Employee getEmployeeById(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw BizException.notFound("员工不存在");
        }
        return employee;
    }

    /**
     * 转换为VO
     */
    private EmployeeVO convertToVO(Employee employee) {
        EmployeeVO vo = new EmployeeVO();
        vo.setId(employee.getId());
        vo.setEmployeeNo(employee.getEmployeeNo());
        vo.setRealName(employee.getRealName());
        vo.setGender(convertGender(employee.getGender()));
        vo.setPhone(employee.getPhone());
        vo.setEmail(employee.getEmail());
        vo.setIdCard(employee.getIdCard());
        vo.setOrgId(employee.getOrgId());
        vo.setPosition(employee.getPosition());
        vo.setHireDate(employee.getHireDate());
        vo.setRemark(employee.getRemark());
        vo.setStatus(employee.getStatus());  // 员工在职状态（active/left）
        if (employee.getCreatedBy() != null) {
            User creator = userMapper.selectById(employee.getCreatedBy());
            if (creator != null) {
                vo.setCreatedBy(StrUtil.isNotBlank(creator.getRealName()) ? creator.getRealName() : creator.getUsername());
            } else {
                vo.setCreatedBy(String.valueOf(employee.getCreatedBy()));
            }
        }
        vo.setCreatedAt(employee.getCreatedAt());
        vo.setUpdatedAt(employee.getUpdatedAt());

        // 填充账号信息
        if (employee.getUserId() != null) {
            User user = userService.getById(employee.getUserId());
            if (user != null) {
                EmployeeVO.AccountInfo accountInfo = new EmployeeVO.AccountInfo();
                accountInfo.setUserId(user.getId());
                accountInfo.setUsername(user.getUsername());
                accountInfo.setAccountStatus(user.getStatus());  // 账号状态（active/inactive/locked）
                accountInfo.setLastLoginAt(user.getLastLoginAt());
                vo.setAccount(accountInfo);
            }
        }

        return vo;
    }

    /**
     * 转换性别值（数据库 0/1/2 -> 前端 unknown/male/female）
     */
    private String convertGender(Integer gender) {
        if (gender == null || gender == 0) {
            return "unknown";
        }
        return gender == 1 ? "male" : "female";
    }

    /**
     * 转换性别值（前端 male/female -> 数据库 1/2）
     */
    private Integer convertGenderToInt(String gender) {
        if (gender == null || "unknown".equals(gender)) {
            return 0;
        }
        return "male".equals(gender) ? 1 : 2;
    }

    /**
     * 填充组织名称
     */
    private void fillOrgNames(List<EmployeeVO> voList) {
        // 收集所有组织ID
        List<Long> orgIds = voList.stream()
                .map(EmployeeVO::getOrgId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        if (orgIds.isEmpty()) {
            return;
        }

        // 批量查询组织
        List<Organization> orgs = organizationMapper.selectBatchIds(orgIds);
        Map<Long, String> orgNameMap = orgs.stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getOrgName));

        // 填充组织名称
        voList.forEach(vo -> {
            if (vo.getOrgId() != null) {
                vo.setOrgName(orgNameMap.get(vo.getOrgId()));
            }
        });
    }

    /**
     * 填充角色名称
     */
    private void fillRoleNames(List<EmployeeVO> voList) {
        // 收集所有有关联用户的userId
        List<Long> userIds = voList.stream()
                .map(vo -> vo.getAccount() != null ? vo.getAccount().getUserId() : null)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        if (userIds.isEmpty()) {
            return;
        }

        // 批量查询角色名称
        List<Map<String, Object>> roleDataList = roleMapper.selectRoleNamesByUserIds(userIds);
        Map<Long, String> userRoleNamesMap = new HashMap<>();
        for (Map<String, Object> row : roleDataList) {
            Long userId = ((Number) row.get("userId")).longValue();
            String roleNames = (String) row.get("roleNames");
            userRoleNamesMap.put(userId, roleNames);
        }

        // 填充角色名称
        voList.forEach(vo -> {
            if (vo.getAccount() != null && vo.getAccount().getUserId() != null) {
                vo.setRoleNames(userRoleNamesMap.get(vo.getAccount().getUserId()));
            }
        });
    }

    // ==================== 导入导出 ====================

    /** 错误文件存储目录 */
    private static final String EMP_ERROR_FILE_DIR = System.getProperty("java.io.tmpdir") + "/emp-import-errors/";

    /** 模板/导出表头（12列，与 EmployeeImportDTO 索引对齐） */
    private static final String[] EMP_TEMPLATE_HEADERS = {
            "员工编号\n(留空自动生成)",
            "姓名\n(必填)",
            "性别\n(male/female)",
            "手机号\n(必填)",
            "邮箱",
            "身份证号",
            "所属组织编码\n(必填)",
            "职位\n(见说明行)",
            "入职日期\n(YYYY-MM-DD)",
            "员工状态\n(active/left)",
            "账号状态\n(active/inactive)",
            "备注"
    };

    private static final int[] EMP_COLUMN_WIDTHS = {22, 12, 15, 15, 25, 20, 20, 15, 15, 15, 15, 30};

    /** 获取职位说明文案 */
    private String getPositionDesc() {
        List<DictCategoryOptionVO> options = dictCategoryService.getOptions("employee_position", false);
        if (options.isEmpty()) return "请在字典分类维护中配置职位";
        return options.stream()
                .map(o -> o.getDictCode() + "(" + o.getDictName() + ")")
                .collect(Collectors.joining("、"));
    }

    /** 获取启用的职位code集合 */
    private Set<String> getEnabledPositionCodes() {
        List<DictCategoryOptionVO> options = dictCategoryService.getOptions("employee_position", false);
        return options.stream()
                .map(DictCategoryOptionVO::getDictCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }

    /** 构建员工编号→ID映射 */
    private Map<String, Long> buildEmployeeNoMap() {
        List<Employee> all = employeeMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, Long> map = new HashMap<>();
        for (Employee e : all) {
            if (StrUtil.isNotBlank(e.getEmployeeNo())) {
                map.put(e.getEmployeeNo(), e.getId());
            }
        }
        return map;
    }

    /** 构建员工编号→所属组织ID映射（用于数据权限校验） */
    private Map<String, Long> buildEmployeeNoToOrgIdMap() {
        List<Employee> all = employeeMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, Long> map = new HashMap<>();
        for (Employee e : all) {
            if (StrUtil.isNotBlank(e.getEmployeeNo())) {
                map.put(e.getEmployeeNo(), e.getOrgId());
            }
        }
        return map;
    }

    /** 解析当前用户可管理的组织ID集合（null表示全部权限） */
    private Set<Long> resolveManageableOrgIds() {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (scope.isAllAccess()) {
            return null;
        }
        return scope.getOrgIds();
    }

    /** 构建组织编码→ID映射（仅启用的组织） */
    private Map<String, Long> buildActiveOrgCodeToIdMap() {
        List<Organization> all = organizationMapper.selectList(
                new LambdaQueryWrapper<Organization>().eq(Organization::getStatus, "active")
        );
        Map<String, Long> map = new HashMap<>();
        for (Organization o : all) {
            if (StrUtil.isNotBlank(o.getOrgCode())) {
                map.put(o.getOrgCode(), o.getId());
            }
        }
        return map;
    }

    /** 构建组织编码→ID映射（全部组织，用于状态校验区分） */
    private Map<String, Organization> buildOrgCodeToEntityMap() {
        List<Organization> all = organizationMapper.selectList(null);
        Map<String, Organization> map = new HashMap<>();
        for (Organization o : all) {
            if (StrUtil.isNotBlank(o.getOrgCode())) {
                map.put(o.getOrgCode(), o);
            }
        }
        return map;
    }

    /** 构建组织ID→编码映射（导出用） */
    private Map<Long, String> buildOrgIdToCodeMap() {
        List<Organization> all = organizationMapper.selectList(null);
        Map<Long, String> map = new HashMap<>();
        for (Organization o : all) {
            map.put(o.getId(), o.getOrgCode());
        }
        return map;
    }

    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("员工导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("员工导入");

            // 样式定义（同组织模板模式）
            CellStyle tipStyle = workbook.createCellStyle();
            tipStyle.setAlignment(HorizontalAlignment.LEFT);
            tipStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            tipStyle.setWrapText(true);
            tipStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            tipStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font tipFont = workbook.createFont();
            tipFont.setFontName("微软雅黑");
            tipFont.setFontHeightInPoints((short) 10);
            tipStyle.setFont(tipFont);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

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
            tipRow.setHeightInPoints(40);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】前两行为示例数据（红色文字，编号以#开头），导入时自动跳过。员工编号留空则自动生成（格式：EMP+日期+序号），填写已存在编号则覆盖更新。职位：" + getPositionDesc() + "。性别：male(男)/female(女)。员工状态：active(在职)/left(离职)。账号状态：active(启用)/inactive(禁用)。新增员工默认在职且启用。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));

            // 第2行：表头
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(45);
            for (int i = 0; i < EMP_TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(EMP_TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, EMP_COLUMN_WIDTHS[i] * 256);
            }

            // 第3-4行：示例数据（红色文字）
            String[][] sampleData = {
                    {"#EMP202605070001", "张三（示例）", "male", "13800138000", "zhangsan@example.com", "110101199001011234", "ORG-001", "chef", "2020-03-15", "active", "active", "高级厨师"},
                    {"#EMP202605070002", "李四（示例）", "female", "13900139000", "", "", "ORG-002", "manager", "2021-06-01", "active", "active", ""}
            };
            for (String[] data : sampleData) {
                Row dataRow = sheet.createRow(rowNum++);
                for (int i = 0; i < data.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(data[i]);
                    cell.setCellStyle(sampleStyle);
                }
            }

            workbook.write(response.getOutputStream());
            workbook.close();
        } catch (IOException e) {
            log.error("下载员工导入模板失败", e);
            throw new BizException("下载模板失败");
        }
    }

    @Override
    public EmployeeImportResultDTO importEmployees(MultipartFile file) {
        List<EmployeeImportDTO> importList;
        try {
            importList = EasyExcel.read(file.getInputStream())
                    .head(EmployeeImportDTO.class)
                    .sheet()
                    .headRowNumber(2)
                    .doReadSync();
        } catch (Exception e) {
            throw new BizException("文件读取失败，请检查文件格式是否正确");
        }

        if (importList.isEmpty()) {
            throw new BizException("导入文件为空");
        }

        if (importList.size() > 5000) {
            throw new BizException("单次导入不能超过5000行，当前" + importList.size() + "行");
        }

        int total = 0;
        int successCount = 0;
        int failCount = 0;
        List<EmployeeImportDTO> errorList = new ArrayList<>();

        Map<String, Long> existingEmpNoMap = buildEmployeeNoMap();
        Map<String, Long> newEmpNoMap = new ConcurrentHashMap<>();
        Map<String, Long> activeOrgCodeToIdMap = buildActiveOrgCodeToIdMap();
        Map<String, Organization> orgCodeToEntityMap = buildOrgCodeToEntityMap();
        Set<String> validPositions = getEnabledPositionCodes();

        // 当前用户可管理的组织ID集合（null表示全部权限，用于数据权限校验）
        Set<Long> manageableOrgIds = resolveManageableOrgIds();
        // 构建员工编号→所属组织ID映射（用于覆盖更新时的数据权限校验）
        Map<String, Long> existingEmpNoToOrgIdMap = buildEmployeeNoToOrgIdMap();

        // 构建已占用的手机号和邮箱集合（用于新增时校验唯一性）
        Set<String> existingPhones = new HashSet<>();
        Set<String> existingEmails = new HashSet<>();
        List<Employee> allEmployees = employeeMapper.selectList(new LambdaQueryWrapper<>());
        for (Employee e : allEmployees) {
            if (StrUtil.isNotBlank(e.getPhone())) existingPhones.add(e.getPhone().trim());
            if (StrUtil.isNotBlank(e.getEmail())) existingEmails.add(e.getEmail().trim().toLowerCase());
        }
        // 同时收集本文件中已使用的手机号/邮箱
        Set<String> filePhones = new HashSet<>();
        Set<String> fileEmails = new HashSet<>();

        for (int i = 0; i < importList.size(); i++) {
            EmployeeImportDTO dto = importList.get(i);
            dto.setRowNum(i + 3);

            // 跳过示例数据
            if (dto.getEmployeeNo() != null && dto.getEmployeeNo().startsWith("#")) {
                continue;
            }

            total++;

            // 校验
            String error = validateEmployeeImportData(dto, existingEmpNoMap, newEmpNoMap, activeOrgCodeToIdMap, orgCodeToEntityMap, validPositions, existingPhones, existingEmails, filePhones, fileEmails, manageableOrgIds, existingEmpNoToOrgIdMap);
            if (error != null) {
                dto.setErrorMessage(error);
                dto.setSuccess(false);
                errorList.add(dto);
                failCount++;
                continue;
            }

            try {
                boolean isUpdate = StrUtil.isNotBlank(dto.getEmployeeNo()) && existingEmpNoMap.containsKey(dto.getEmployeeNo());
                Long orgId = activeOrgCodeToIdMap.get(dto.getOrgCode().trim());

                if (isUpdate) {
                    // 覆盖更新
                    Long empId = existingEmpNoMap.get(dto.getEmployeeNo());
                    Employee employee = employeeMapper.selectById(empId);
                    if (employee == null) continue;

                    // 禁止禁用自己
                    if ("inactive".equals(dto.getAccountStatus()) && employee.getUserId() != null
                            && employee.getUserId().equals(UserContext.getUserId())) {
                        throw new BizException("不能禁用当前登录账号");
                    }
                    // 禁止把自己状态改为离职
                    if ("left".equals(dto.getStatus()) && employee.getUserId() != null
                            && employee.getUserId().equals(UserContext.getUserId())) {
                        throw new BizException("不能将当前登录账号的员工状态改为离职");
                    }

                    // 禁用/离职时校验是否有未完成派单
                    boolean willDisable = "inactive".equals(dto.getAccountStatus()) || "left".equals(dto.getStatus());
                    if (willDisable) {
                        validateNoPendingDispatches(empId);
                    }

                    updateEmployeeFromImport(employee, dto, orgId);

                    // 同步用户信息
                    if (employee.getUserId() != null) {
                        User user = userService.getById(employee.getUserId());
                        if (user != null) {
                            userService.updateUserInfo(
                                    user.getId(),
                                    StrUtil.isNotBlank(dto.getRealName()) ? dto.getRealName() : null,
                                    StrUtil.isNotBlank(dto.getPhone()) ? dto.getPhone() : null,
                                    dto.getEmail(),
                                    employee.getOrgId(),
                                    convertGenderToInt(dto.getGender())
                            );
                            // 账号状态
                            String acctStatus = StrUtil.isNotBlank(dto.getAccountStatus()) ? dto.getAccountStatus() : "active";
                            // 离职时强制禁用
                            if ("left".equals(employee.getStatus())) {
                                acctStatus = "inactive";
                                removeFromAlertRules(employee.getId());
                            }
                            userService.updateStatus(user.getId(), acctStatus);
                            // 强制下线
                            if ("inactive".equals(acctStatus)) {
                                forceLogoutUser(user.getId(), "left".equals(employee.getStatus()) ? "left" : "disabled");
                            }
                        }
                    }
                } else {
                    // 新增 — 强制在职+启用
                    String empNo = StrUtil.isNotBlank(dto.getEmployeeNo()) ? dto.getEmployeeNo() : generateEmployeeNo();

                    Employee employee = new Employee();
                    employee.setEmployeeNo(empNo);
                    employee.setRealName(dto.getRealName());
                    employee.setGender(convertGenderToInt(dto.getGender()));
                    employee.setIdCard(StrUtil.isNotBlank(dto.getIdCard()) ? dto.getIdCard() : null);
                    employee.setPhone(dto.getPhone());
                    employee.setEmail(StrUtil.isNotBlank(dto.getEmail()) ? dto.getEmail() : null);
                    employee.setOrgId(orgId);
                    employee.setPosition(StrUtil.isNotBlank(dto.getPosition()) ? dto.getPosition().trim() : null);
                    employee.setHireDate(parseHireDate(dto.getHireDate()));
                    employee.setStatus("active");
                    employee.setHealthCertStatus("pending");
                    employee.setFaceEnrolled(0);
                    employee.setTenantId(UserContext.getTenantId());
                    employee.setRemark(StrUtil.isNotBlank(dto.getRemark()) ? dto.getRemark() : null);
                    employeeMapper.insert(employee);

                    // 创建账号 — 强制启用
                    User user = userService.createForEmployee(
                            empNo, null, dto.getRealName(), dto.getPhone(),
                            dto.getEmail(), orgId, employee.getTenantId(),
                            employee.getGender(), "active"
                    );
                    employee.setUserId(user.getId());
                    employeeMapper.updateById(employee);

                    newEmpNoMap.put(empNo, employee.getId());
                }

                // 记录成功行的手机号/邮箱，防止文件内后续行重复
                if (StrUtil.isNotBlank(dto.getPhone())) filePhones.add(dto.getPhone().trim());
                if (StrUtil.isNotBlank(dto.getEmail())) fileEmails.add(dto.getEmail().trim());

                dto.setSuccess(true);
                successCount++;
            } catch (Exception ex) {
                dto.setErrorMessage(ex.getMessage() != null ? ex.getMessage() : "导入失败");
                dto.setSuccess(false);
                errorList.add(dto);
                failCount++;
            }
        }

        // 生成错误文件
        String errorFileUrl = null;
        if (!errorList.isEmpty()) {
            errorFileUrl = generateEmployeeErrorFile(errorList);
        }

        // 审计日志
        try {
            Map<String, Object> afterData = new HashMap<>();
            afterData.put("total", total);
            afterData.put("successCount", successCount);
            afterData.put("failCount", failCount);
            if (!errorList.isEmpty()) {
                List<Map<String, String>> failures = errorList.stream().map(dto -> {
                    Map<String, String> f = new HashMap<>();
                    f.put("row", String.valueOf(dto.getRowNum()));
                    f.put("employeeNo", dto.getEmployeeNo());
                    f.put("realName", dto.getRealName());
                    f.put("error", dto.getErrorMessage());
                    return f;
                }).collect(Collectors.toList());
                afterData.put("failures", failures);
            }
            auditLogService.log(AuditModule.SYS_EMPLOYEE, AuditOperationType.IMPORT, null, null,
                    "导入员工：共" + total + "条，成功" + successCount + "条，失败" + failCount + "条",
                    null, JSONUtil.toJsonStr(afterData), "success", null);
        } catch (Exception e) {
            log.warn("记录审计日志失败", e);
        }

        return new EmployeeImportResultDTO(total, successCount, failCount, !errorList.isEmpty(), errorFileUrl);
    }

    /** 更新已有员工（导入覆盖） */
    private void updateEmployeeFromImport(Employee employee, EmployeeImportDTO dto, Long orgId) {
        if (StrUtil.isNotBlank(dto.getRealName())) employee.setRealName(dto.getRealName());
        if (dto.getGender() != null) employee.setGender(convertGenderToInt(dto.getGender()));
        if (dto.getIdCard() != null) employee.setIdCard(StrUtil.isNotBlank(dto.getIdCard()) ? dto.getIdCard() : null);
        if (StrUtil.isNotBlank(dto.getPhone())) employee.setPhone(dto.getPhone());
        if (dto.getEmail() != null) employee.setEmail(StrUtil.isNotBlank(dto.getEmail()) ? dto.getEmail() : null);
        if (orgId != null) employee.setOrgId(orgId);
        employee.setPosition(StrUtil.isNotBlank(dto.getPosition()) ? dto.getPosition().trim() : null);
        employee.setHireDate(parseHireDate(dto.getHireDate()));
        if (StrUtil.isNotBlank(dto.getStatus())) employee.setStatus(dto.getStatus());
        if (dto.getRemark() != null) employee.setRemark(StrUtil.isNotBlank(dto.getRemark()) ? dto.getRemark() : null);
        employeeMapper.updateById(employee);
    }

    /** 解析入职日期 */
    private LocalDate parseHireDate(String dateStr) {
        if (StrUtil.isBlank(dateStr)) return null;
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 校验导入数据 */
    private String validateEmployeeImportData(EmployeeImportDTO dto, Map<String, Long> existingMap,
                                               Map<String, Long> newMap, Map<String, Long> activeOrgCodeMap,
                                               Map<String, Organization> orgCodeToEntityMap,
                                               Set<String> validPositions,
                                               Set<String> existingPhones, Set<String> existingEmails,
                                               Set<String> filePhones, Set<String> fileEmails,
                                               Set<Long> manageableOrgIds, Map<String, Long> existingEmpNoToOrgIdMap) {
        boolean isUpdate = StrUtil.isNotBlank(dto.getEmployeeNo()) && existingMap.containsKey(dto.getEmployeeNo().trim());

        if (StrUtil.isBlank(dto.getRealName())) return "姓名不能为空";
        if (StrUtil.isBlank(dto.getPhone())) return "手机号不能为空";
        if (!dto.getPhone().trim().matches("^1[3-9]\\d{9}$")) return "手机号格式不正确";
        if (StrUtil.isBlank(dto.getOrgCode())) return "所属组织编码不能为空";

        String orgCode = dto.getOrgCode().trim();
        Organization orgEntity = orgCodeToEntityMap.get(orgCode);
        if (orgEntity == null) return "所属组织编码不存在：" + orgCode;
        if (!"active".equals(orgEntity.getStatus())) return "所属组织已停用：" + orgCode;
        if (!activeOrgCodeMap.containsKey(orgCode)) return "所属组织不可用：" + orgCode;

        // 数据权限校验：覆盖更新时，检查已有员工所属组织是否在权限范围内
        if (isUpdate && manageableOrgIds != null) {
            Long existingOrgId = existingEmpNoToOrgIdMap.get(dto.getEmployeeNo().trim());
            if (existingOrgId == null || !manageableOrgIds.contains(existingOrgId)) {
                return "无权修改该员工数据，员工所属组织不在您的管理范围内";
            }
        }

        // 数据权限校验：所属组织是否在权限范围内
        if (manageableOrgIds != null) {
            Long targetOrgId = activeOrgCodeMap.get(orgCode);
            if (targetOrgId == null || !manageableOrgIds.contains(targetOrgId)) {
                return "无权操作该组织下的员工，组织不在您的管理范围内";
            }
        }

        if (StrUtil.isNotBlank(dto.getGender()) && !Arrays.asList("male", "female").contains(dto.getGender().trim()))
            return "性别无效，必须为：male 或 female";
        if (StrUtil.isNotBlank(dto.getPosition()) && !validPositions.contains(dto.getPosition().trim()))
            return "职位无效，有效值：" + String.join("、", validPositions);
        if (StrUtil.isNotBlank(dto.getStatus()) && !Arrays.asList("active", "left").contains(dto.getStatus().trim()))
            return "员工状态无效，必须为：active 或 left";
        if (StrUtil.isNotBlank(dto.getAccountStatus()) && !Arrays.asList("active", "inactive").contains(dto.getAccountStatus().trim()))
            return "账号状态无效，必须为：active 或 inactive";
        if (StrUtil.isNotBlank(dto.getHireDate())) {
            try {
                LocalDate.parse(dto.getHireDate().trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (Exception e) {
                return "入职日期格式不正确，应为：YYYY-MM-DD";
            }
        }
        // 编号文件内唯一
        if (StrUtil.isNotBlank(dto.getEmployeeNo()) && newMap.containsKey(dto.getEmployeeNo().trim())) {
            return "文件内员工编号重复：" + dto.getEmployeeNo();
        }

        // 手机号唯一性校验
        String phone = dto.getPhone().trim();
        if (!isUpdate) {
            // 新增：校验全局 + 文件内
            if (existingPhones.contains(phone)) return "手机号已存在：" + phone;
            if (filePhones.contains(phone)) return "文件内手机号重复：" + phone;
        }
        // 更新时手机号不变不报错，变了也不校验（更新流程中已有校验）

        // 邮箱唯一性校验（新增时）
        if (!isUpdate && StrUtil.isNotBlank(dto.getEmail())) {
            String email = dto.getEmail().trim().toLowerCase();
            if (existingEmails.contains(email)) return "邮箱已存在：" + dto.getEmail();
            if (fileEmails.contains(email)) return "文件内邮箱重复：" + dto.getEmail();
        }

        return null;
    }

    @Override
    @DataScope
    public void exportEmployees(EmployeeQueryDTO query, HttpServletResponse response) {
        try {
            // 复用 list() 的权限过滤逻辑
            LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
            wrapper.and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                            .like(Employee::getEmployeeNo, query.getKeyword())
                            .or().like(Employee::getRealName, query.getKeyword())
                            .or().like(Employee::getPhone, query.getKeyword()))
                    .eq(query.getOrgId() != null, Employee::getOrgId, query.getOrgId())
                    .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Employee::getOrgId, query.getOrgIds())
                    .orderByDesc(Employee::getCreatedAt);

            if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
                wrapper.isNull(Employee::getId);
            }

            if (StrUtil.isNotBlank(query.getAccountStatus())) {
                List<Long> userIds = userMapper.selectIdsByStatus(query.getAccountStatus());
                if (userIds == null || userIds.isEmpty()) {
                    wrapper.isNull(Employee::getId);
                } else {
                    wrapper.in(Employee::getUserId, userIds);
                }
            }

            List<Employee> employees = employeeMapper.selectList(wrapper);

            // 构建映射
            Map<Long, String> orgIdToCodeMap = buildOrgIdToCodeMap();

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String fileName = URLEncoder.encode("员工数据导出_" + timestamp, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("员工数据");

            // 样式（同模板）
            CellStyle tipStyle = workbook.createCellStyle();
            tipStyle.setAlignment(HorizontalAlignment.LEFT);
            tipStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            tipStyle.setWrapText(true);
            tipStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            tipStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font tipFont = workbook.createFont();
            tipFont.setFontName("微软雅黑");
            tipFont.setFontHeightInPoints((short) 10);
            tipStyle.setFont(tipFont);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

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

            // 第1行：说明行（与模板一致，可直接用于导入）
            Row tipRow = sheet.createRow(rowNum++);
            tipRow.setHeightInPoints(40);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】员工编号留空则自动生成（格式：EMP+日期+序号），填写已存在编号则覆盖更新。职位：" + getPositionDesc() + "。性别：male(男)/female(女)。员工状态：active(在职)/left(离职)。账号状态：active(启用)/inactive(禁用)。新增员工默认在职且启用。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));

            // 第2行：表头
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(45);
            for (int i = 0; i < EMP_TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(EMP_TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, EMP_COLUMN_WIDTHS[i] * 256);
            }

            // 第3行+：数据
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (Employee emp : employees) {
                Row dataRow = sheet.createRow(rowNum++);

                // 获取账号状态
                String acctStatus = "";
                if (emp.getUserId() != null) {
                    User user = userService.getById(emp.getUserId());
                    if (user != null) acctStatus = user.getStatus();
                }

                String[] values = {
                        emp.getEmployeeNo() != null ? emp.getEmployeeNo() : "",
                        emp.getRealName() != null ? emp.getRealName() : "",
                        convertGender(emp.getGender()),
                        emp.getPhone() != null ? emp.getPhone() : "",
                        emp.getEmail() != null ? emp.getEmail() : "",
                        emp.getIdCard() != null ? emp.getIdCard() : "",
                        orgIdToCodeMap.getOrDefault(emp.getOrgId(), ""),
                        emp.getPosition() != null ? emp.getPosition() : "",
                        emp.getHireDate() != null ? emp.getHireDate().format(dateFormatter) : "",
                        emp.getStatus() != null ? emp.getStatus() : "",
                        acctStatus,
                        emp.getRemark() != null ? emp.getRemark() : ""
                };
                for (int i = 0; i < values.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            workbook.write(response.getOutputStream());
            workbook.close();

            // 审计日志
            auditLogService.log(AuditModule.SYS_EMPLOYEE, AuditOperationType.EXPORT, null, null,
                    "导出员工数据：" + employees.size() + "条",
                    null, String.valueOf(employees.size()), "success", null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("导出员工失败", e);
            try {
                auditLogService.log(AuditModule.SYS_EMPLOYEE, AuditOperationType.EXPORT, null, null,
                        "导出员工数据失败", null, null, "failure", e.getMessage());
            } catch (Exception ignored) {
            }
            throw new BizException("导出失败");
        }
    }

    @Override
    public void downloadErrorFile(String fileName, HttpServletResponse response) {
        String filePath = EMP_ERROR_FILE_DIR + fileName;
        File file = new File(filePath);
        if (!file.exists()) {
            throw BizException.notFound("错误文件不存在或已过期");
        }
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            Files.copy(file.toPath(), response.getOutputStream());
        } catch (IOException e) {
            log.error("下载错误文件失败", e);
            throw new BizException("下载错误文件失败");
        }
    }

    /** 生成员工导入错误文件 */
    private String generateEmployeeErrorFile(List<EmployeeImportDTO> errorList) {
        try {
            File dir = new File(EMP_ERROR_FILE_DIR);
            if (!dir.exists()) dir.mkdirs();

            String fileName = "emp_import_errors_" + System.currentTimeMillis() + ".xlsx";
            String filePath = EMP_ERROR_FILE_DIR + fileName;

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("导入失败数据");

            // 样式
            CellStyle tipStyle = workbook.createCellStyle();
            tipStyle.setAlignment(HorizontalAlignment.LEFT);
            tipStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            tipStyle.setWrapText(true);
            tipStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            tipStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font tipFont = workbook.createFont();
            tipFont.setFontName("微软雅黑");
            tipFont.setFontHeightInPoints((short) 10);
            tipStyle.setFont(tipFont);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

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
            tipCell.setCellValue("【说明】以下数据导入失败，请根据失败原因修改后重新导入。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12));

            // 第2行：表头（比模板多一列"失败原因"）
            String[] errorHeaders = {
                    "员工编号\n(留空自动生成)", "姓名\n(必填)", "性别\n(male/female)", "手机号\n(必填)",
                    "邮箱", "身份证号", "所属组织编码\n(必填)", "职位\n(见说明行)", "入职日期\n(YYYY-MM-DD)",
                    "员工状态\n(active/left)", "账号状态\n(active/inactive)", "备注", "失败原因"
            };
            int[] errorWidths = {22, 12, 15, 15, 25, 20, 20, 15, 15, 15, 15, 30, 40};
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(45);
            for (int i = 0; i < errorHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(errorHeaders[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, errorWidths[i] * 256);
            }

            // 数据行
            for (EmployeeImportDTO dto : errorList) {
                Row dataRow = sheet.createRow(rowNum++);
                String[] values = {
                        dto.getEmployeeNo() != null ? dto.getEmployeeNo() : "",
                        dto.getRealName() != null ? dto.getRealName() : "",
                        dto.getGender() != null ? dto.getGender() : "",
                        dto.getPhone() != null ? dto.getPhone() : "",
                        dto.getEmail() != null ? dto.getEmail() : "",
                        dto.getIdCard() != null ? dto.getIdCard() : "",
                        dto.getOrgCode() != null ? dto.getOrgCode() : "",
                        dto.getPosition() != null ? dto.getPosition() : "",
                        dto.getHireDate() != null ? dto.getHireDate() : "",
                        dto.getStatus() != null ? dto.getStatus() : "",
                        dto.getAccountStatus() != null ? dto.getAccountStatus() : "",
                        dto.getRemark() != null ? dto.getRemark() : "",
                        dto.getErrorMessage() != null ? dto.getErrorMessage() : ""
                };
                for (int i = 0; i < values.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(errorStyle);
                }
            }

            FileOutputStream fos = new FileOutputStream(filePath);
            workbook.write(fos);
            fos.close();
            workbook.close();

            return "/api/v1/sys/employees/import/errors/" + fileName;
        } catch (Exception e) {
            log.error("生成错误文件失败", e);
            return null;
        }
    }

    /**
     * 校验员工是否有未完成的派单（投诉派单 + 告警派单）
     * @param employeeId 员工ID
     * @throws BizException 如有未完成派单则抛出异常
     */
    private void validateNoPendingDispatches(Long employeeId) {
        // 1. 投诉派单：sys_complaint_dispatch
        Long complaintCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_complaint_dispatch WHERE handler_id = ? AND status IN ('pending', 'processing') AND deleted = 0",
            Long.class, employeeId);
        if (complaintCount != null && complaintCount > 0) {
            throw BizException.validationFailed("该员工存在未完成的投诉派单，无法禁用/离职/删除");
        }

        // 2. 告警派单：device_alert_dispatch
        Long alertCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM device_alert_dispatch WHERE handler_id = ? AND status IN ('pending', 'processing') AND deleted = 0",
            Long.class, employeeId);
        if (alertCount != null && alertCount > 0) {
            throw BizException.validationFailed("该员工存在未完成的告警派单，无法禁用/离职/删除");
        }
    }

    /**
     * 撤销用户所有 token 并标记强制下线
     */
    private void forceLogoutUser(Long userId, String reason) {
        if (userId == null) return;
        try {
            // 1. 撤销 auth_token 表中所有未撤销的 token
            jdbcTemplate.update(
                "UPDATE auth_token SET is_revoked = 1 WHERE user_id = ? AND is_revoked = 0", userId);
            // 2. 设置 Redis 强制下线标记
            forceLogoutHelper.forceLogout(userId, reason);
        } catch (Exception e) {
            log.error("强制下线失败: userId={}, reason={}", userId, reason, e);
        }
    }

    /**
     * 从所有告警规则的 notify_users 中移除指定员工
     */
    private void removeFromAlertRules(Long employeeId) {
        try {
            String empIdStr = String.valueOf(employeeId);
            // 查找包含该员工ID的告警规则（精确匹配CSV中的ID，避免123匹配到1234）
            List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                    "SELECT id, notify_users FROM device_alert_rule WHERE deleted = 0 " +
                            "AND (notify_users = ? OR notify_users LIKE ? OR notify_users LIKE ? OR notify_users LIKE ?)",
                    empIdStr,
                    empIdStr + ",%",
                    "%," + empIdStr + ",%",
                    "%," + empIdStr);
            for (Map<String, Object> rule : rules) {
                String notifyUsers = (String) rule.get("notify_users");
                if (notifyUsers == null || notifyUsers.isBlank()) continue;
                String updated = Arrays.stream(notifyUsers.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty() && !s.equals(empIdStr))
                        .collect(Collectors.joining(","));
                jdbcTemplate.update(
                        "UPDATE device_alert_rule SET notify_users = ? WHERE id = ?",
                        updated, rule.get("id"));
            }
            log.info("已从告警规则中移除离职员工: employeeId={}", employeeId);
        } catch (Exception e) {
            log.warn("从告警规则移除员工失败（不影响主流程）: employeeId={}", employeeId, e);
        }
    }
}