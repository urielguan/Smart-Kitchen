package com.xykj.device.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.device.dto.AlertRuleCreateDTO;
import com.xykj.device.dto.AlertRuleQueryDTO;
import com.xykj.device.dto.AlertRuleUpdateDTO;
import com.xykj.device.entity.DeviceAlert;
import com.xykj.device.entity.DeviceAlertRule;
import com.xykj.device.mapper.DeviceAlertMapper;
import com.xykj.device.mapper.DeviceAlertRuleMapper;
import com.xykj.device.service.DeviceAlertRuleService;
import com.xykj.device.vo.AlertRuleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 告警规则配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAlertRuleServiceImpl implements DeviceAlertRuleService {

    private final DeviceAlertRuleMapper ruleMapper;
    private final DeviceAlertMapper alertMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 规则类型映射 */
    private static final Map<String, String> RULE_TYPE_MAP = Map.of(
            "threshold", "阈值告警",
            "offline", "离线告警",
            "ai_event", "AI事件告警",
            "material", "物料告警"
    );

    /** 设备类型映射 */
    private static final Map<String, String> DEVICE_TYPE_MAP = Map.of(
            "camera", "监控摄像头",
            "sensor", "温湿度传感器",
            "scale", "食材检测设备",
            "gas_detector", "气体监测设备",
            "sample_terminal", "智能留样设备",
            "health_terminal", "智能晨检设备"
    );

    /** 告警级别映射 */
    private static final Map<String, String> ALERT_LEVEL_MAP = Map.of(
            "info", "提示",
            "warning", "警告",
            "error", "错误",
            "critical", "严重"
    );

    /** 合法的阈值指标 */
    private static final Set<String> THRESHOLD_METRICS = Set.of("temperature", "humidity", "gas");
    /** 合法的比较运算符 */
    private static final Set<String> OPERATORS = Set.of(">", "<", ">=", "<=");

    @Override
    @DataScope
    public Page<AlertRuleVO> list(AlertRuleQueryDTO query) {
        LambdaQueryWrapper<DeviceAlertRule> wrapper = new LambdaQueryWrapper<DeviceAlertRule>()
                .eq(DeviceAlertRule::getDeleted, 0)
                .like(StrUtil.isNotBlank(query.getRuleName()), DeviceAlertRule::getRuleName, query.getRuleName())
                .eq(StrUtil.isNotBlank(query.getRuleType()), DeviceAlertRule::getRuleType, query.getRuleType())
                .eq(StrUtil.isNotBlank(query.getDeviceType()), DeviceAlertRule::getDeviceType, query.getDeviceType())
                .eq(StrUtil.isNotBlank(query.getAlertLevel()), DeviceAlertRule::getAlertLevel, query.getAlertLevel())
                .eq(query.getIsEnabled() != null, DeviceAlertRule::getIsEnabled, query.getIsEnabled())
                .eq(query.getOrgId() != null, DeviceAlertRule::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(),
                        DeviceAlertRule::getOrgId, query.getOrgIds())
                .orderByDesc(DeviceAlertRule::getUpdatedAt);

        // 空组织列表 → 不返回任何数据
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(DeviceAlertRule::getId);
        }

        Page<DeviceAlertRule> page = ruleMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        List<AlertRuleVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        Page<AlertRuleVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    public AlertRuleVO getDetail(Long id) {
        DeviceAlertRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<DeviceAlertRule>()
                .eq(DeviceAlertRule::getId, id)
                .eq(DeviceAlertRule::getDeleted, 0));
        if (rule == null) {
            throw new BizException("告警规则不存在");
        }
        return convertToVO(rule);
    }

    @Override
    @AuditLog(
            module = AuditModule.DEVICE_ALERT_RULE,
            operationType = AuditOperationType.CREATE,
            targetId = "#result",
            desc = "'新增告警规则：' + #dto.ruleName",
            mapper = DeviceAlertRuleMapper.class
    )
    public Long create(AlertRuleCreateDTO dto) {
        validateConditionJson(dto.getRuleType(), dto.getConditionJson());

        // 校验规则名称同租户唯一
        validateRuleNameUnique(dto.getRuleName(), null);

        // material 类型校验物料唯一性
        if ("material".equals(dto.getRuleType())) {
            validateMaterialUniqueness(dto.getMaterialIds(), null);
        }

        DeviceAlertRule rule = new DeviceAlertRule();
        BeanUtils.copyProperties(dto, rule);

        // material 类型时清空设备相关字段
        if ("material".equals(dto.getRuleType())) {
            rule.setDeviceType(null);
            rule.setConditionJson(null);
        }

        rule.setOrgId(UserContext.getOrgId() != null ? UserContext.getOrgId() : dto.getOrgId());
        rule.setTenantId(UserContext.getTenantId());
        if (rule.getIsEnabled() == null) {
            rule.setIsEnabled(1);
        }

        ruleMapper.insert(rule);
        log.info("新增告警规则成功: id={}, name={}, type={}", rule.getId(), rule.getRuleName(), rule.getRuleType());
        return rule.getId();
    }

    @Override
    @AuditLog(
            module = AuditModule.DEVICE_ALERT_RULE,
            operationType = AuditOperationType.UPDATE,
            targetId = "#id",
            desc = "'编辑告警规则：' + #dto.ruleName",
            mapper = DeviceAlertRuleMapper.class
    )
    public void update(Long id, AlertRuleUpdateDTO dto) {
        DeviceAlertRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<DeviceAlertRule>()
                .eq(DeviceAlertRule::getId, id)
                .eq(DeviceAlertRule::getDeleted, 0));
        if (rule == null) {
            throw new BizException("告警规则不存在");
        }

        validateConditionJson(dto.getRuleType(), dto.getConditionJson());

        // 校验规则名称同租户唯一
        validateRuleNameUnique(dto.getRuleName(), id);

        // material 类型校验物料唯一性
        if ("material".equals(dto.getRuleType())) {
            validateMaterialUniqueness(dto.getMaterialIds(), id);
        }

        rule.setRuleName(dto.getRuleName());
        rule.setRuleType(dto.getRuleType());
        rule.setAlertLevel(dto.getAlertLevel());
        rule.setNotifyChannels(dto.getNotifyChannels() != null ? dto.getNotifyChannels() : "");
        rule.setNotifyUsers(dto.getNotifyUsers() != null ? dto.getNotifyUsers() : "");
        rule.setDispatchScopeRoles(dto.getDispatchScopeRoles() != null ? dto.getDispatchScopeRoles() : "");
        rule.setIsEnabled(dto.getIsEnabled());
        rule.setAutoDispatch(dto.getAutoDispatch());

        if ("material".equals(dto.getRuleType())) {
            rule.setDeviceType(null);
            rule.setDeviceIds(null);
            rule.setConditionJson(null);
            rule.setMaterialIds(dto.getMaterialIds());
        } else {
            rule.setDeviceType(dto.getDeviceType());
            rule.setDeviceIds(dto.getDeviceIds());
            rule.setConditionJson(dto.getConditionJson());
            rule.setMaterialIds(null);
        }

        ruleMapper.updateById(rule);
        log.info("编辑告警规则成功: id={}, name={}", id, dto.getRuleName());
    }

    @Override
    @AuditLog(
            module = AuditModule.DEVICE_ALERT_RULE,
            operationType = AuditOperationType.DELETE,
            targetId = "#id",
            desc = "'删除告警规则：' + #entity.ruleName",
            mapper = DeviceAlertRuleMapper.class
    )
    public void delete(Long id) {
        DeviceAlertRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<DeviceAlertRule>()
                .eq(DeviceAlertRule::getId, id)
                .eq(DeviceAlertRule::getDeleted, 0));
        if (rule == null) {
            throw new BizException("告警规则不存在");
        }

        // 校验是否已触发过告警
        Long alertCount = alertMapper.selectCount(new LambdaQueryWrapper<DeviceAlert>()
                .eq(DeviceAlert::getAlertRuleId, id)
                .eq(DeviceAlert::getDeleted, 0));
        if (alertCount != null && alertCount > 0) {
            throw new BizException("该告警策略已触发过告警事件，为保障食安监控不中断，不允许删除");
        }

        ruleMapper.deleteById(id);
        log.info("删除告警规则成功: id={}, name={}", id, rule.getRuleName());
    }

    @Override
    @AuditLog(
            module = AuditModule.DEVICE_ALERT_RULE,
            operationType = AuditOperationType.STATUS_CHANGE,
            targetId = "#id",
            desc = "'切换告警规则状态：' + #entity.ruleName",
            mapper = DeviceAlertRuleMapper.class
    )
    public void toggleEnabled(Long id) {
        DeviceAlertRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<DeviceAlertRule>()
                .eq(DeviceAlertRule::getId, id)
                .eq(DeviceAlertRule::getDeleted, 0));
        if (rule == null) {
            throw new BizException("告警规则不存在");
        }

        rule.setIsEnabled(rule.getIsEnabled() == null || rule.getIsEnabled() == 1 ? 0 : 1);
        ruleMapper.updateById(rule);
        log.info("切换告警规则状态: id={}, enabled={}", id, rule.getIsEnabled());
    }

    // ========== 辅助方法 ==========

    /**
     * 校验 conditionJson 格式
     * - threshold 新格式: { "logic": "and|or", "conditions": [{metric, operator, value}, ...], "duration": 60 }
     * - threshold 旧格式: { "metric": "temperature", "operator": ">", "value": 37.3, "duration": 60 }
     * - offline: { "offlineMinutes": 5 }
     * - ai_event: 不校验（暂不支持）
     * - material: 不校验（条件继承自物料配置）
     */
    private void validateConditionJson(String ruleType, String conditionJson) {
        // material 和 ai_event 类型不需要校验 conditionJson
        if ("material".equals(ruleType) || "ai_event".equals(ruleType)) {
            return;
        }

        if (StrUtil.isBlank(conditionJson)) {
            throw new BizException("触发条件不能为空");
        }

        try {
            Map<String, Object> condition = objectMapper.readValue(conditionJson,
                    new TypeReference<Map<String, Object>>() {});

            switch (ruleType) {
                case "threshold" -> {
                    if (condition.containsKey("conditions") && condition.get("conditions") instanceof List) {
                        // 新格式：多条件
                        String logic = condition.get("logic") != null
                                ? condition.get("logic").toString() : "and";
                        if (!Set.of("and", "or").contains(logic)) {
                            throw new BizException("逻辑连接符必须为 and 或 or");
                        }
                        List<?> rawList = (List<?>) condition.get("conditions");
                        if (rawList == null || rawList.isEmpty()) {
                            throw new BizException("触发条件不能为空");
                        }
                        if (rawList.size() > 2) {
                            throw new BizException("最多支持2个触发条件");
                        }
                        for (Object item : rawList) {
                            validateSingleCondition((Map<?, ?>) item);
                        }
                    } else {
                        // 旧格式：单条件（向后兼容）
                        validateSingleCondition(condition);
                    }
                }
                case "offline" -> {
                    Object minutes = condition.get("offlineMinutes");
                    if (minutes == null) {
                        throw new BizException("离线时长不能为空");
                    }
                    int offlineMinutes = ((Number) minutes).intValue();
                    if (offlineMinutes <= 0) {
                        throw new BizException("离线时长必须大于0");
                    }
                }
                default -> throw new BizException("不支持的规则类型: " + ruleType);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("触发条件JSON格式错误: " + e.getMessage());
        }
    }

    /** 校验单个阈值条件 */
    private void validateSingleCondition(Map<?, ?> c) {
        String metric = (String) c.get("metric");
        String operator = (String) c.get("operator");
        Object value = c.get("value");

        if (!THRESHOLD_METRICS.contains(metric)) {
            throw new BizException("不支持的指标类型: " + metric);
        }
        if (!OPERATORS.contains(operator)) {
            throw new BizException("不支持的比较运算符: " + operator);
        }
        if (value == null) {
            throw new BizException("阈值不能为空");
        }
    }

    /**
     * DeviceAlertRule → AlertRuleVO
     */
    private AlertRuleVO convertToVO(DeviceAlertRule rule) {
        AlertRuleVO vo = new AlertRuleVO();
        BeanUtils.copyProperties(rule, vo);
        vo.setRuleTypeName(RULE_TYPE_MAP.getOrDefault(rule.getRuleType(), rule.getRuleType()));
        vo.setDeviceTypeName(rule.getDeviceType() != null
                ? DEVICE_TYPE_MAP.getOrDefault(rule.getDeviceType(), rule.getDeviceType())
                : "全部设备");
        vo.setAlertLevelName(ALERT_LEVEL_MAP.getOrDefault(rule.getAlertLevel(), rule.getAlertLevel()));

        // 解析派单范围角色名称
        if (StrUtil.isNotBlank(rule.getDispatchScopeRoles())) {
            try {
                List<Long> roleIds = Arrays.stream(rule.getDispatchScopeRoles().split(","))
                        .map(String::trim)
                        .filter(StrUtil::isNotBlank)
                        .map(Long::parseLong)
                        .collect(Collectors.toList());
                if (!roleIds.isEmpty()) {
                    String placeholders = roleIds.stream().map(id -> "?").collect(Collectors.joining(","));
                    List<String> roleNames = jdbcTemplate.queryForList(
                            "SELECT role_name FROM auth_role WHERE id IN (" + placeholders + ") AND deleted = 0",
                            String.class, roleIds.toArray());
                    vo.setDispatchScopeRoleNames(roleNames);
                } else {
                    vo.setDispatchScopeRoleNames(List.of());
                }
            } catch (Exception e) {
                log.warn("解析派单范围角色名称失败: ruleId={}, error={}", rule.getId(), e.getMessage());
                vo.setDispatchScopeRoleNames(List.of());
            }
        } else {
            vo.setDispatchScopeRoleNames(null);
        }

        // 解析适用设备名称
        if (StrUtil.isNotBlank(rule.getDeviceIds())) {
            try {
                List<Long> ids = Arrays.stream(rule.getDeviceIds().split(","))
                        .map(String::trim)
                        .filter(StrUtil::isNotBlank)
                        .map(Long::parseLong)
                        .collect(Collectors.toList());
                if (!ids.isEmpty()) {
                    String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
                    List<String> names = jdbcTemplate.queryForList(
                            "SELECT device_name FROM device_info WHERE id IN (" + placeholders + ") AND deleted = 0",
                            String.class, ids.toArray());
                    vo.setDeviceNames(names);
                } else {
                    vo.setDeviceNames(List.of());
                }
            } catch (Exception e) {
                log.warn("解析适用设备名称失败: ruleId={}, error={}", rule.getId(), e.getMessage());
                vo.setDeviceNames(List.of());
            }
        } else {
            vo.setDeviceNames(null);
        }

        // 解析适用物料名称
        if (StrUtil.isNotBlank(rule.getMaterialIds())) {
            try {
                List<Long> ids = Arrays.stream(rule.getMaterialIds().split(","))
                        .map(String::trim)
                        .filter(StrUtil::isNotBlank)
                        .map(Long::parseLong)
                        .collect(Collectors.toList());
                if (!ids.isEmpty()) {
                    String placeholders = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
                    List<String> names = jdbcTemplate.queryForList(
                            "SELECT material_name FROM wms_material WHERE id IN (" + placeholders + ") AND deleted = 0",
                            String.class);
                    vo.setMaterialNames(names);
                } else {
                    vo.setMaterialNames(List.of());
                }
            } catch (Exception e) {
                log.warn("解析适用物料名称失败: ruleId={}, error={}", rule.getId(), e.getMessage());
                vo.setMaterialNames(List.of());
            }
        } else {
            vo.setMaterialNames(null);
        }

        return vo;
    }

    /**
     * 校验规则名称同租户唯一
     */
    private void validateRuleNameUnique(String ruleName, Long excludeRuleId) {
        Long tenantId = UserContext.getTenantId();
        LambdaQueryWrapper<DeviceAlertRule> wrapper = new LambdaQueryWrapper<DeviceAlertRule>()
                .eq(DeviceAlertRule::getRuleName, ruleName)
                .eq(DeviceAlertRule::getDeleted, 0)
                .eq(tenantId != null, DeviceAlertRule::getTenantId, tenantId);
        if (excludeRuleId != null) {
            wrapper.ne(DeviceAlertRule::getId, excludeRuleId);
        }
        Long count = ruleMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException("规则名称「" + ruleName + "」已存在，同租户内不允许重复");
        }
    }

    /**
     * 校验物料唯一性：同一物料只能关联一个物料告警规则
     */
    private void validateMaterialUniqueness(String materialIds, Long excludeRuleId) {
        if (StrUtil.isBlank(materialIds)) {
            return;
        }
        // 查询所有启用的物料告警规则
        List<DeviceAlertRule> existingRules = ruleMapper.selectList(
                new LambdaQueryWrapper<DeviceAlertRule>()
                        .eq(DeviceAlertRule::getRuleType, "material")
                        .eq(DeviceAlertRule::getDeleted, 0));
        if (excludeRuleId != null) {
            existingRules = existingRules.stream()
                    .filter(r -> !r.getId().equals(excludeRuleId))
                    .collect(Collectors.toList());
        }
        // 收集已占用的物料ID
        Set<String> occupiedMaterialIds = new HashSet<>();
        for (DeviceAlertRule r : existingRules) {
            if (StrUtil.isNotBlank(r.getMaterialIds())) {
                Arrays.stream(r.getMaterialIds().split(","))
                        .map(String::trim)
                        .filter(StrUtil::isNotBlank)
                        .forEach(occupiedMaterialIds::add);
            }
        }
        // 检查新提交的物料ID是否冲突
        for (String mid : materialIds.split(",")) {
            String trimmed = mid.trim();
            if (!trimmed.isEmpty() && occupiedMaterialIds.contains(trimmed)) {
                // 查询物料名称用于提示
                String materialName = trimmed;
                try {
                    List<String> names = jdbcTemplate.queryForList(
                            "SELECT material_name FROM wms_material WHERE id = ? AND deleted = 0",
                            String.class, Long.parseLong(trimmed));
                    if (!names.isEmpty()) materialName = names.get(0);
                } catch (Exception ignored) {}
                throw new BizException("物料「" + materialName + "」已关联其他物料告警规则，同一物料只能关联一个物料告警规则");
            }
        }
    }
}
