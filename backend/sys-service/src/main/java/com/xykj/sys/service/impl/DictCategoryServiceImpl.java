package com.xykj.sys.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.MaterialCategoryCoefficientLockService;
import com.xykj.common.util.DictCategoryTextValidationUtil;
import com.xykj.sys.dto.DictCategoryAreaCoefficientRecalcDTO;
import com.xykj.sys.dto.DictCategoryAreaSuggestionDTO;
import com.xykj.sys.dto.DictCategoryCreateDTO;
import com.xykj.sys.dto.DictCategoryQueryDTO;
import com.xykj.sys.dto.DictCategoryStatusDTO;
import com.xykj.sys.dto.DictCategoryUpdateDTO;
import com.xykj.sys.entity.RecipeCategoryBridge;
import com.xykj.sys.entity.SysDictAreaCoefficientCorrection;
import com.xykj.sys.entity.SysDictAreaCoefficientLock;
import com.xykj.sys.entity.SysDictAreaCoefficientRecalcDetail;
import com.xykj.sys.entity.SysDictAreaCoefficientRecalcTask;
import com.xykj.sys.entity.SysDictItem;
import com.xykj.sys.enums.DictCategoryType;
import com.xykj.sys.mapper.RecipeCategoryBridgeMapper;
import com.xykj.sys.mapper.SysDictAreaCoefficientCorrectionMapper;
import com.xykj.sys.mapper.SysDictAreaCoefficientLockMapper;
import com.xykj.sys.mapper.SysDictAreaCoefficientRecalcDetailMapper;
import com.xykj.sys.mapper.SysDictAreaCoefficientRecalcTaskMapper;
import com.xykj.sys.mapper.SysDictItemMapper;
import com.xykj.sys.service.DictCategoryService;
import com.xykj.sys.vo.DictCategoryAreaCoefficientHistoryVO;
import com.xykj.sys.vo.DictCategoryAreaCoefficientRecalcDetailVO;
import com.xykj.sys.vo.DictCategoryAreaCoefficientRecalcTaskVO;
import com.xykj.sys.vo.DictCategoryAreaSuggestionVO;
import com.xykj.sys.vo.DictCategoryDetailVO;
import com.xykj.sys.vo.DictCategoryItemVO;
import com.xykj.sys.vo.DictCategoryMetaVO;
import com.xykj.sys.vo.DictCategoryOptionVO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 字典分类维护服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictCategoryServiceImpl implements DictCategoryService {

    private static final String DELETE_BLOCKED_MESSAGE = "该分类已被业务数据引用绑定，为保证数据完整可追溯，不允许删除。";
    private static final String DEFAULT_AI_REASON = "AI结合物料类别名称、行业通用仓储占用标准与常见堆码经验值生成建议";
    private static final String IMPACT_SCOPE_SUBSEQUENT_ONLY = "subsequent_only";
    private static final String IMPACT_SCOPE_MANUAL_RECALC = "manual_recalc";
    private static final String LOCK_STATUS_LOCKED = "locked";
    private static final String LOCK_STATUS_UNLOCKED = "unlocked";
    private static final String LOCK_TYPE_COEFFICIENT_UPDATE = "coefficient_update";
    private static final String LOCK_TYPE_HISTORY_RECALC = "history_recalc";
    private static final String RECALC_STATUS_NOT_APPLICABLE = "not_applicable";
    private static final String RECALC_STATUS_PENDING = "pending";
    private static final String RECALC_STATUS_RUNNING = "running";
    private static final String RECALC_STATUS_COMPLETED = "completed";
    private static final String RECALC_STATUS_FAILED = "failed";

    private static final Map<DictCategoryType, List<BuiltinItem>> BUILTIN_ITEM_MAP = buildBuiltinItemMap();

    private static final Set<String> BUILTIN_RECIPE_CODES = BUILTIN_ITEM_MAP.getOrDefault(
            DictCategoryType.RECIPE_CATEGORY, Collections.emptyList()
    ).stream().map(BuiltinItem::getCode).collect(Collectors.toCollection(LinkedHashSet::new));

    private final SysDictItemMapper sysDictItemMapper;
    private final RecipeCategoryBridgeMapper recipeCategoryBridgeMapper;
    private final SysDictAreaCoefficientCorrectionMapper areaCoefficientCorrectionMapper;
    private final SysDictAreaCoefficientLockMapper areaCoefficientLockMapper;
    private final SysDictAreaCoefficientRecalcTaskMapper areaCoefficientRecalcTaskMapper;
    private final SysDictAreaCoefficientRecalcDetailMapper areaCoefficientRecalcDetailMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final DataScopeService dataScopeService;
    private final MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService;

    private final Set<Long> initializedTenantIds = ConcurrentHashMap.newKeySet();
    private volatile boolean recipeCategoryInitialized = false;

    @Override
    public List<DictCategoryMetaVO> listCategories() {
        ensureInitialized();

        Long tenantId = getCurrentTenantId();
        List<DictCategoryMetaVO> result = new ArrayList<>();
        for (DictCategoryType categoryType : DictCategoryType.values()) {
            DictCategoryMetaVO vo = new DictCategoryMetaVO();
            vo.setCategoryType(categoryType.getCode());
            vo.setCategoryName(categoryType.getName());

            if (categoryType.useRecipeTable()) {
                List<RecipeCategoryBridge> items = recipeCategoryBridgeMapper.selectList(
                        buildVisibleRecipeCategoryWrapper()
                                .orderByAsc(RecipeCategoryBridge::getSortOrder)
                                .orderByDesc(RecipeCategoryBridge::getUpdatedAt)
                );
                long total = items.size();
                long systemCount = items.stream().filter(this::isSystemRecipeCategory).count();
                vo.setItemCount(total);
                vo.setSystemCount(systemCount);
                vo.setCustomCount(total - systemCount);
            } else {
                List<SysDictItem> items = sysDictItemMapper.selectList(
                        new LambdaQueryWrapper<SysDictItem>()
                                .eq(SysDictItem::getDictType, categoryType.getCode())
                                .eq(SysDictItem::getTenantId, tenantId)
                );
                long total = items.size();
                long systemCount = items.stream().filter(item -> Objects.equals(item.getIsSystem(), 1)).count();
                vo.setItemCount(total);
                vo.setSystemCount(systemCount);
                vo.setCustomCount(total - systemCount);
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public PageResult<DictCategoryItemVO> list(DictCategoryQueryDTO queryDTO) {
        ensureInitialized();

        DictCategoryType categoryType = parseCategoryType(queryDTO.getCategoryType());
        if (categoryType.useRecipeTable()) {
            return listRecipeCategories(categoryType, queryDTO);
        }
        return listSysDictItems(categoryType, queryDTO);
    }

    @Override
    public DictCategoryDetailVO getDetail(String categoryType, Long id) {
        ensureInitialized();

        DictCategoryType parsedType = parseCategoryType(categoryType);
        if (parsedType.useRecipeTable()) {
            return convertRecipeToDetail(getVisibleRecipeCategoryById(id));
        }

        return convertSysDictToDetail(getTenantScopedSysDictById(id, parsedType));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(DictCategoryCreateDTO dto) {
        ensureInitialized();

        DictCategoryType categoryType = parseCategoryType(dto.getCategoryType());
        String dictCode = normalizeCode(dto.getDictCode());
        String dictName = normalizeName(dto.getDictName());
        Integer sortOrder = dto.getSortOrder() != null ? dto.getSortOrder() : 0;
        String status = normalizeStatus(dto.getStatus());
        String remark = normalizeRemark(dto.getRemark());

        checkCodeUnique(categoryType, dictCode, null);
        checkNameUnique(categoryType, dictName, null);
        MaterialCategoryFieldState materialFieldState = resolveMaterialCategoryFieldState(
                categoryType,
                dto.getAreaCoefficient(),
                dto.getAreaCoefficientSource(),
                dto.getAiSuggestedAreaCoefficient(),
                dto.getAiSuggestionReason(),
                dto.getAiSuggestionGeneratedAt()
        );

        Map<String, Object> result;
        if (categoryType.useRecipeTable()) {
            RecipeCategoryBridge entity = new RecipeCategoryBridge();
            entity.setCategoryCode(dictCode);
            entity.setCategoryName(dictName);
            entity.setSortOrder(sortOrder);
            entity.setStatus(status);
            entity.setRemark(remark);
            entity.setOrgId(getTenantSharedOrgId());
            entity.setTenantId(getCurrentTenantId());
            recipeCategoryBridgeMapper.insert(entity);

            result = buildResult(entity.getId(), dictCode, dictName, categoryType.getCode());
            auditLogService.log(
                    AuditModule.SYS_DICT_CATEGORY,
                    AuditOperationType.CREATE,
                    entity.getId(),
                    dictCode,
                    "新增字典项：" + categoryType.getName() + " - " + dictName + "（" + dictCode + "）",
                    null,
                    JSONUtil.toJsonStr(convertRecipeToDetail(entity))
            );
            return result;
        }

        SysDictItem entity = new SysDictItem();
        entity.setDictType(categoryType.getCode());
        entity.setDictCode(dictCode);
        entity.setDictName(dictName);
        entity.setDictValue(buildDictValue(categoryType, dictCode, dictName, null));
        entity.setSortOrder(sortOrder);
        entity.setStatus(status);
        entity.setRemark(remark);
        entity.setIsSystem(0);
        entity.setOrgId(getTenantSharedOrgId());
        entity.setTenantId(getCurrentTenantId());
        applyMaterialCategoryFields(entity, materialFieldState);
        sysDictItemMapper.insert(entity);

        result = buildResult(entity.getId(), dictCode, dictName, categoryType.getCode());
        auditLogService.log(
                AuditModule.SYS_DICT_CATEGORY,
                AuditOperationType.CREATE,
                entity.getId(),
                dictCode,
                "新增字典项：" + categoryType.getName() + " - " + dictName + "（" + dictCode + "）",
                null,
                JSONUtil.toJsonStr(convertSysDictToDetail(entity))
        );
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(String categoryType, Long id, DictCategoryUpdateDTO dto) {
        ensureInitialized();

        DictCategoryType parsedType = parseCategoryType(categoryType);
        if (parsedType.useRecipeTable()) {
            RecipeCategoryBridge recipeCategory = getVisibleRecipeCategoryById(id);
            if (isSystemRecipeCategory(recipeCategory)) {
                throw BizException.validationFailed("系统内置分类项仅支持查看，不允许修改或删除");
            }
            String dictCode = normalizeCode(dto.getDictCode());
            String dictName = normalizeName(dto.getDictName());
            String remark = normalizeRemark(dto.getRemark());
            checkCodeUnique(parsedType, dictCode, id);
            checkNameUnique(parsedType, dictName, id);

            String beforeData = JSONUtil.toJsonStr(convertRecipeToDetail(recipeCategory));
            recipeCategory.setCategoryCode(dictCode);
            recipeCategory.setCategoryName(dictName);
            recipeCategory.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
            recipeCategory.setRemark(remark);
            recipeCategoryBridgeMapper.updateById(recipeCategory);
            forceUpdateRecipeCategoryRemark(recipeCategory.getId(), remark);

            auditLogService.log(
                    AuditModule.SYS_DICT_CATEGORY,
                    AuditOperationType.UPDATE,
                    recipeCategory.getId(),
                    recipeCategory.getCategoryCode(),
                    "编辑字典项：" + parsedType.getName() + " - " + recipeCategory.getCategoryName() + "（" + recipeCategory.getCategoryCode() + "）",
                    beforeData,
                    JSONUtil.toJsonStr(convertRecipeToDetail(recipeCategory))
            );
            return buildResult(recipeCategory.getId(), recipeCategory.getCategoryCode(), recipeCategory.getCategoryName(), parsedType.getCode());
        }

        SysDictItem item = getTenantScopedSysDictById(id, parsedType);
        ensureCustomEditable(item);

        String dictCode = normalizeCode(dto.getDictCode());
        String dictName = normalizeName(dto.getDictName());
        checkCodeUnique(parsedType, dictCode, id);
        checkNameUnique(parsedType, dictName, id);
        MaterialCategoryFieldState materialFieldState = resolveMaterialCategoryFieldState(
                parsedType,
                dto.getAreaCoefficient(),
                dto.getAreaCoefficientSource(),
                dto.getAiSuggestedAreaCoefficient(),
                dto.getAiSuggestionReason(),
                dto.getAiSuggestionGeneratedAt()
        );
        if (isMaterialCategory(parsedType)) {
            validateMaterialCategoryUpdateVersion(item, dto);
            ensureAreaCoefficientUnlocked(item, "编辑物料类别");
        }

        SysDictAreaCoefficientCorrection correctionRecord = null;
        boolean areaCoefficientChanged = isMaterialCategory(parsedType)
                && !sameDecimal(item.getAreaCoefficient(), materialFieldState.getAreaCoefficient());
        String newDictValue = buildDictValue(parsedType, dictCode, dictName, item.getId());
        if (areaCoefficientChanged) {
            String effectScope = normalizeAreaCoefficientEffectScope(dto.getAreaCoefficientEffectScope());
            validateAreaCoefficientImpactConfirmation(dto.getAreaCoefficientImpactConfirmed());
            correctionRecord = createAreaCoefficientCorrectionRecord(
                    item,
                    dictCode,
                    dictName,
                    newDictValue,
                    materialFieldState,
                    effectScope
            );
            lockAreaCoefficient(item, correctionRecord, null, LOCK_TYPE_COEFFICIENT_UPDATE, "面积系数修正保存处理中");
        }

        String oldValue = item.getDictValue();
        String beforeData = JSONUtil.toJsonStr(convertSysDictToDetail(item));
        String remark = normalizeRemark(dto.getRemark());

        item.setDictCode(dictCode);
        item.setDictName(dictName);
        item.setDictValue(newDictValue);
        item.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        item.setRemark(remark);
        applyMaterialCategoryFields(item, materialFieldState);
        sysDictItemMapper.updateById(item);
        forceUpdateSysDictRemark(item.getId(), remark);

        cascadeBusinessValueChange(parsedType, oldValue, item.getDictValue());
        if (correctionRecord != null) {
            releaseAreaCoefficientLock(item, correctionRecord, null);
            auditLogService.log(
                    AuditModule.SYS_DICT_CATEGORY,
                    AuditOperationType.UPDATE,
                    item.getId(),
                    item.getDictCode(),
                    "物料类别面积系数修正：" + item.getDictName() + "，修正版本 V" + correctionRecord.getCorrectionVersion(),
                    JSONUtil.toJsonStr(buildAreaCoefficientCorrectionAuditData(correctionRecord, true)),
                    JSONUtil.toJsonStr(buildAreaCoefficientCorrectionAuditData(correctionRecord, false))
            );
        }

        auditLogService.log(
                AuditModule.SYS_DICT_CATEGORY,
                AuditOperationType.UPDATE,
                item.getId(),
                item.getDictCode(),
                "编辑字典项：" + parsedType.getName() + " - " + item.getDictName() + "（" + item.getDictCode() + "）",
                beforeData,
                JSONUtil.toJsonStr(convertSysDictToDetail(item))
        );
        return buildResult(item.getId(), item.getDictCode(), item.getDictName(), parsedType.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateStatus(String categoryType, Long id, DictCategoryStatusDTO dto) {
        ensureInitialized();

        String status = normalizeStatus(dto.getStatus());
        DictCategoryType parsedType = parseCategoryType(categoryType);
        if (parsedType.useRecipeTable()) {
            RecipeCategoryBridge recipeCategory = getVisibleRecipeCategoryById(id);
            if (isSystemRecipeCategory(recipeCategory)) {
                throw BizException.validationFailed("系统内置分类项仅支持查看，不允许修改或删除");
            }
            String beforeData = JSONUtil.toJsonStr(convertRecipeToDetail(recipeCategory));
            recipeCategory.setStatus(status);
            recipeCategoryBridgeMapper.updateById(recipeCategory);

            auditLogService.log(
                    AuditModule.SYS_DICT_CATEGORY,
                    AuditOperationType.STATUS_CHANGE,
                    recipeCategory.getId(),
                    recipeCategory.getCategoryCode(),
                    ("active".equals(status) ? "启用" : "停用") + "字典项：" + parsedType.getName() + " - " + recipeCategory.getCategoryName(),
                    beforeData,
                    JSONUtil.toJsonStr(convertRecipeToDetail(recipeCategory))
            );
            return buildResult(recipeCategory.getId(), recipeCategory.getCategoryCode(), recipeCategory.getCategoryName(), parsedType.getCode());
        }

        SysDictItem item = getTenantScopedSysDictById(id, parsedType);
        ensureCustomEditable(item);
        String beforeData = JSONUtil.toJsonStr(convertSysDictToDetail(item));
        item.setStatus(status);
        sysDictItemMapper.updateById(item);

        auditLogService.log(
                AuditModule.SYS_DICT_CATEGORY,
                AuditOperationType.STATUS_CHANGE,
                item.getId(),
                item.getDictCode(),
                ("active".equals(status) ? "启用" : "停用") + "字典项：" + parsedType.getName() + " - " + item.getDictName(),
                beforeData,
                JSONUtil.toJsonStr(convertSysDictToDetail(item))
        );
        return buildResult(item.getId(), item.getDictCode(), item.getDictName(), parsedType.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String categoryType, Long id) {
        ensureInitialized();

        DictCategoryType parsedType = parseCategoryType(categoryType);
        if (parsedType.useRecipeTable()) {
            RecipeCategoryBridge recipeCategory = getVisibleRecipeCategoryById(id);
            if (isSystemRecipeCategory(recipeCategory)) {
                throw BizException.validationFailed("系统内置分类项仅支持查看，不允许修改或删除");
            }
            long referenceCount = countRecipeReferences(recipeCategory.getId());
            if (referenceCount > 0) {
                throw BizException.validationFailed(DELETE_BLOCKED_MESSAGE);
            }
            String beforeData = JSONUtil.toJsonStr(convertRecipeToDetail(recipeCategory));
            recipeCategoryBridgeMapper.physicalDeleteById(id);
            auditLogService.log(
                    AuditModule.SYS_DICT_CATEGORY,
                    AuditOperationType.DELETE,
                    recipeCategory.getId(),
                    recipeCategory.getCategoryCode(),
                    "删除字典项：" + parsedType.getName() + " - " + recipeCategory.getCategoryName() + "（" + recipeCategory.getCategoryCode() + "）",
                    beforeData,
                    null
            );
            return;
        }

        SysDictItem item = getTenantScopedSysDictById(id, parsedType);
        ensureCustomEditable(item);

        long referenceCount = countSysDictReferences(parsedType, item.getDictValue());
        if (referenceCount > 0) {
            throw BizException.validationFailed(DELETE_BLOCKED_MESSAGE);
        }

        String beforeData = JSONUtil.toJsonStr(convertSysDictToDetail(item));
        sysDictItemMapper.physicalDeleteById(id);
        auditLogService.log(
                AuditModule.SYS_DICT_CATEGORY,
                AuditOperationType.DELETE,
                item.getId(),
                item.getDictCode(),
                "删除字典项：" + parsedType.getName() + " - " + item.getDictName() + "（" + item.getDictCode() + "）",
                beforeData,
                null
        );
    }

    @Override
    public List<DictCategoryOptionVO> getOptions(String categoryType, boolean includeInactive) {
        ensureInitialized();

        DictCategoryType parsedType = parseCategoryType(categoryType);
        if (parsedType.useRecipeTable()) {
            LambdaQueryWrapper<RecipeCategoryBridge> wrapper = buildVisibleRecipeCategoryWrapper()
                    .eq(!includeInactive, RecipeCategoryBridge::getStatus, "active")
                    .orderByAsc(RecipeCategoryBridge::getSortOrder)
                    .orderByDesc(RecipeCategoryBridge::getUpdatedAt);
            return recipeCategoryBridgeMapper.selectList(wrapper)
                    .stream()
                    .map(this::convertRecipeToOption)
                    .collect(Collectors.toList());
        }

        return sysDictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, parsedType.getCode())
                        .eq(SysDictItem::getTenantId, getCurrentTenantId())
                        .eq(!includeInactive, SysDictItem::getStatus, "active")
                        .orderByAsc(SysDictItem::getSortOrder)
                        .orderByDesc(SysDictItem::getUpdatedAt)
        ).stream().map(this::convertSysDictToOption).collect(Collectors.toList());
    }

    @Override
    public DictCategoryAreaSuggestionVO getAreaCoefficientSuggestion(DictCategoryAreaSuggestionDTO dto) {
        ensureInitialized();

        DictCategoryType categoryType = parseCategoryType(dto.getCategoryType());
        if (!Objects.equals(categoryType, DictCategoryType.MATERIAL_CATEGORY)) {
            throw BizException.validationFailed("仅物料类别支持AI建议面积系数");
        }

        String dictName = normalizeName(dto.getDictName());
        AreaSuggestion suggestion = resolveAreaSuggestion(dictName);

        DictCategoryAreaSuggestionVO vo = new DictCategoryAreaSuggestionVO();
        vo.setAreaCoefficient(suggestion.getAreaCoefficient());
        vo.setReason(suggestion.getReason());
        vo.setGeneratedAt(LocalDateTime.now());
        return vo;
    }

    @Override
    public List<DictCategoryAreaCoefficientHistoryVO> getAreaCoefficientHistory(String categoryType, Long id) {
        ensureInitialized();
        DictCategoryType parsedType = parseCategoryType(categoryType);
        if (!Objects.equals(parsedType, DictCategoryType.MATERIAL_CATEGORY)) {
            return List.of();
        }
        log.warn("已屏蔽物料类别面积系数修正历史接口调用: categoryType={}, id={}", categoryType, id);
        return List.of();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startAreaCoefficientRecalc(String categoryType, Long id, DictCategoryAreaCoefficientRecalcDTO dto) {
        ensureInitialized();
        requireAdminForAreaCoefficientRecalc();

        DictCategoryType parsedType = parseMaterialCategoryType(categoryType);
        SysDictItem item = getTenantScopedSysDictById(id, parsedType);
        SysDictAreaCoefficientCorrection correction = getAreaCoefficientCorrection(item.getId(), dto.getCorrectionId());
        validateAreaCoefficientRecalcStart(item, correction);

        if (materialCategoryCoefficientLockService.hasActiveLockByDictId(item.getId())) {
            throw BizException.conflict("该物料类别正在执行面积系数处理，请稍后重试");
        }

        SysDictAreaCoefficientRecalcTask task = createAreaCoefficientRecalcTask(item, correction);
        correction.setRecalcStatus(RECALC_STATUS_RUNNING);
        correction.setRecalcTaskId(task.getId());
        correction.setRecalcCompletedAt(null);
        areaCoefficientCorrectionMapper.updateById(correction);
        lockAreaCoefficient(item, correction, task.getId(), LOCK_TYPE_HISTORY_RECALC, "面积系数历史回溯重算执行中");

        auditLogService.log(
                AuditModule.SYS_DICT_CATEGORY,
                AuditOperationType.UPDATE,
                item.getId(),
                item.getDictCode(),
                "管理员发起物料类别面积系数历史回溯重算：" + item.getDictName() + "，修正版本 V" + correction.getCorrectionVersion(),
                null,
                JSONUtil.toJsonStr(Map.of(
                        "correctionId", correction.getId(),
                        "correctionVersion", correction.getCorrectionVersion(),
                        "taskId", task.getId(),
                        "taskNo", task.getTaskNo()
                ))
        );

        AsyncOperatorContext operatorContext = captureAsyncOperatorContext();
        CompletableFuture.runAsync(() -> executeAreaCoefficientRecalcAsync(operatorContext, item, correction, task));

        return Map.of(
                "taskId", task.getId(),
                "taskNo", task.getTaskNo(),
                "correctionId", correction.getId()
        );
    }

    @Override
    public DictCategoryAreaCoefficientRecalcTaskVO getAreaCoefficientRecalcTaskDetail(String categoryType, Long id, Long taskId) {
        ensureInitialized();

        DictCategoryType parsedType = parseMaterialCategoryType(categoryType);
        SysDictItem item = getTenantScopedSysDictById(id, parsedType);
        SysDictAreaCoefficientRecalcTask task = areaCoefficientRecalcTaskMapper.selectOne(
                new LambdaQueryWrapper<SysDictAreaCoefficientRecalcTask>()
                        .eq(SysDictAreaCoefficientRecalcTask::getId, taskId)
                        .eq(SysDictAreaCoefficientRecalcTask::getDictId, item.getId())
                        .eq(SysDictAreaCoefficientRecalcTask::getTenantId, getCurrentTenantId())
                        .eq(SysDictAreaCoefficientRecalcTask::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (task == null) {
            throw BizException.notFound("重算任务不存在");
        }

        List<SysDictAreaCoefficientRecalcDetail> details = areaCoefficientRecalcDetailMapper.selectList(
                new LambdaQueryWrapper<SysDictAreaCoefficientRecalcDetail>()
                        .eq(SysDictAreaCoefficientRecalcDetail::getTaskId, task.getId())
                        .eq(SysDictAreaCoefficientRecalcDetail::getDeleted, 0)
                        .orderByAsc(SysDictAreaCoefficientRecalcDetail::getId)
        );
        return buildAreaCoefficientRecalcTaskVO(task, details);
    }

    private PageResult<DictCategoryItemVO> listRecipeCategories(DictCategoryType categoryType, DictCategoryQueryDTO queryDTO) {
        IPage<RecipeCategoryBridge> page = recipeCategoryBridgeMapper.selectPageWithReferenceCount(
                new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()),
                getCurrentTenantId(),
                BUILTIN_RECIPE_CODES,
                queryDTO
        );

        List<DictCategoryItemVO> list = page.getRecords().stream()
                .map(this::convertRecipeToItem)
                .collect(Collectors.toList());
        return PageResult.of(page, list);
    }

    private PageResult<DictCategoryItemVO> listSysDictItems(DictCategoryType categoryType, DictCategoryQueryDTO queryDTO) {
        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictItem::getDictType, categoryType.getCode())
                .eq(SysDictItem::getTenantId, getCurrentTenantId())
                .and(StrUtil.isNotBlank(queryDTO.getKeyword()),
                        q -> q.like(SysDictItem::getDictName, queryDTO.getKeyword())
                                .or()
                                .like(SysDictItem::getDictCode, queryDTO.getKeyword()))
                .eq("system".equals(queryDTO.getSourceType()), SysDictItem::getIsSystem, 1)
                .eq("custom".equals(queryDTO.getSourceType()), SysDictItem::getIsSystem, 0)
                .eq(StrUtil.isNotBlank(queryDTO.getStatus()), SysDictItem::getStatus, queryDTO.getStatus())
                .orderByAsc(SysDictItem::getSortOrder)
                .orderByDesc(SysDictItem::getUpdatedAt);

        IPage<SysDictItem> page = sysDictItemMapper.selectPage(
                new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()),
                wrapper
        );

        List<DictCategoryItemVO> list = page.getRecords().stream()
                .map(this::convertSysDictToItem)
                .collect(Collectors.toList());
        return PageResult.of(page, list);
    }

    private DictCategoryDetailVO convertRecipeToDetail(RecipeCategoryBridge entity) {
        DictCategoryDetailVO vo = new DictCategoryDetailVO();
        vo.setId(entity.getId());
        vo.setCategoryType(DictCategoryType.RECIPE_CATEGORY.getCode());
        vo.setCategoryName(DictCategoryType.RECIPE_CATEGORY.getName());
        vo.setDictCode(entity.getCategoryCode());
        vo.setDictName(entity.getCategoryName());
        vo.setDictValue(entity.getId() != null ? String.valueOf(entity.getId()) : null);
        vo.setSourceType(isSystemRecipeCategory(entity) ? "system" : "custom");
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setReferenceCount(countRecipeReferences(entity.getId()));
        vo.setSystem(isSystemRecipeCategory(entity));
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setUpdatedBy(entity.getUpdatedBy());
        vo.setOrgId(entity.getOrgId());
        vo.setTenantId(entity.getTenantId());
        vo.setRemark(entity.getRemark());
        return vo;
    }

    private DictCategoryItemVO convertRecipeToItem(RecipeCategoryBridge entity) {
        DictCategoryItemVO vo = new DictCategoryItemVO();
        vo.setId(entity.getId());
        vo.setCategoryType(DictCategoryType.RECIPE_CATEGORY.getCode());
        vo.setCategoryName(DictCategoryType.RECIPE_CATEGORY.getName());
        vo.setDictCode(entity.getCategoryCode());
        vo.setDictName(entity.getCategoryName());
        vo.setDictValue(entity.getId() != null ? String.valueOf(entity.getId()) : null);
        vo.setSourceType(isSystemRecipeCategory(entity) ? "system" : "custom");
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setReferenceCount(entity.getReferenceCount() != null ? entity.getReferenceCount() : 0L);
        vo.setSystem(isSystemRecipeCategory(entity));
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private DictCategoryOptionVO convertRecipeToOption(RecipeCategoryBridge entity) {
        DictCategoryOptionVO vo = new DictCategoryOptionVO();
        vo.setId(entity.getId());
        vo.setCategoryType(DictCategoryType.RECIPE_CATEGORY.getCode());
        vo.setDictCode(entity.getCategoryCode());
        vo.setDictName(entity.getCategoryName());
        vo.setValue(entity.getId() != null ? String.valueOf(entity.getId()) : null);
        vo.setStatus(entity.getStatus());
        vo.setSystem(isSystemRecipeCategory(entity));
        vo.setSortOrder(entity.getSortOrder());
        return vo;
    }

    private DictCategoryDetailVO convertSysDictToDetail(SysDictItem entity) {
        DictCategoryDetailVO vo = new DictCategoryDetailVO();
        BeanUtil.copyProperties(entity, vo);
        DictCategoryType categoryType = parseCategoryType(entity.getDictType());
        vo.setCategoryType(entity.getDictType());
        vo.setCategoryName(categoryType.getName());
        vo.setSourceType(Objects.equals(entity.getIsSystem(), 1) ? "system" : "custom");
        vo.setReferenceCount(countSysDictReferences(categoryType, entity.getDictValue()));
        vo.setSystem(Objects.equals(entity.getIsSystem(), 1));
        return vo;
    }

    private DictCategoryItemVO convertSysDictToItem(SysDictItem entity) {
        DictCategoryItemVO vo = new DictCategoryItemVO();
        BeanUtil.copyProperties(entity, vo);
        DictCategoryType categoryType = parseCategoryType(entity.getDictType());
        vo.setCategoryType(entity.getDictType());
        vo.setCategoryName(categoryType.getName());
        vo.setSourceType(Objects.equals(entity.getIsSystem(), 1) ? "system" : "custom");
        vo.setReferenceCount(countSysDictReferences(categoryType, entity.getDictValue()));
        vo.setSystem(Objects.equals(entity.getIsSystem(), 1));
        return vo;
    }

    private DictCategoryOptionVO convertSysDictToOption(SysDictItem entity) {
        DictCategoryOptionVO vo = new DictCategoryOptionVO();
        vo.setId(entity.getId());
        vo.setCategoryType(entity.getDictType());
        vo.setDictCode(entity.getDictCode());
        vo.setDictName(entity.getDictName());
        vo.setValue(entity.getDictValue());
        vo.setStatus(entity.getStatus());
        vo.setSystem(Objects.equals(entity.getIsSystem(), 1));
        vo.setSortOrder(entity.getSortOrder());
        vo.setAreaCoefficient(entity.getAreaCoefficient());
        return vo;
    }

    private void ensureInitialized() {
        Long tenantId = getCurrentTenantId();
        if (!initializedTenantIds.contains(tenantId)) {
            synchronized (this) {
                if (!initializedTenantIds.contains(tenantId)) {
                    syncBuiltinSysDictItems(tenantId);
                    initializedTenantIds.add(tenantId);
                }
            }
        }
        if (!recipeCategoryInitialized) {
            synchronized (this) {
                if (!recipeCategoryInitialized) {
                    syncBuiltinRecipeCategories();
                    recipeCategoryInitialized = true;
                }
            }
        }
    }

    private void syncBuiltinSysDictItems(Long tenantId) {
        for (Map.Entry<DictCategoryType, List<BuiltinItem>> entry : BUILTIN_ITEM_MAP.entrySet()) {
            DictCategoryType categoryType = entry.getKey();
            if (categoryType.useRecipeTable()) {
                continue;
            }
            for (BuiltinItem builtinItem : entry.getValue()) {
                SysDictItem existed = sysDictItemMapper.selectOne(
                        new LambdaQueryWrapper<SysDictItem>()
                                .eq(SysDictItem::getDictType, categoryType.getCode())
                                .eq(SysDictItem::getTenantId, tenantId)
                                .eq(SysDictItem::getDictCode, builtinItem.getCode())
                                .last("LIMIT 1")
                );
                if (existed != null) {
                    if (syncExistingBuiltinSysDictItem(existed, categoryType, builtinItem)) {
                        sysDictItemMapper.updateById(existed);
                    }
                    continue;
                }

                SysDictItem item = new SysDictItem();
                item.setDictType(categoryType.getCode());
                item.setDictCode(builtinItem.getCode());
                item.setDictName(builtinItem.getName());
                item.setDictValue(buildDictValue(categoryType, builtinItem.getCode(), builtinItem.getName(), null));
                item.setSortOrder(builtinItem.getSortOrder());
                item.setIsSystem(1);
                item.setStatus("active");
                item.setRemark("系统内置");
                applyBuiltinMaterialAreaFields(item, categoryType, builtinItem);
                item.setOrgId(getTenantSharedOrgId());
                item.setTenantId(tenantId);
                sysDictItemMapper.insert(item);
            }
        }
    }

    private void syncBuiltinRecipeCategories() {
        for (BuiltinItem builtinItem : BUILTIN_ITEM_MAP.getOrDefault(DictCategoryType.RECIPE_CATEGORY, Collections.emptyList())) {
            RecipeCategoryBridge existed = recipeCategoryBridgeMapper.selectOne(
                    new LambdaQueryWrapper<RecipeCategoryBridge>()
                            .eq(RecipeCategoryBridge::getCategoryCode, builtinItem.getCode())
                            .last("LIMIT 1")
            );
            if (existed != null) {
                if (syncExistingBuiltinRecipeCategory(existed, builtinItem)) {
                    recipeCategoryBridgeMapper.updateById(existed);
                }
                continue;
            }

            RecipeCategoryBridge entity = new RecipeCategoryBridge();
            entity.setCategoryCode(builtinItem.getCode());
            entity.setCategoryName(builtinItem.getName());
            entity.setSortOrder(builtinItem.getSortOrder());
            entity.setStatus("active");
            entity.setRemark("系统内置");
            entity.setOrgId(0L);
            entity.setTenantId(0L);
            recipeCategoryBridgeMapper.insert(entity);
        }
    }

    private void checkCodeUnique(DictCategoryType categoryType, String dictCode, Long excludeId) {
        if (categoryType.useRecipeTable()) {
            RecipeCategoryBridge existed = recipeCategoryBridgeMapper.selectOne(
                    new LambdaQueryWrapper<RecipeCategoryBridge>()
                            .eq(RecipeCategoryBridge::getCategoryCode, dictCode)
                            .and(wrapper -> wrapper
                                    .in(RecipeCategoryBridge::getCategoryCode, BUILTIN_RECIPE_CODES)
                                    .or()
                                    .eq(RecipeCategoryBridge::getTenantId, getCurrentTenantId()))
                            .ne(excludeId != null, RecipeCategoryBridge::getId, excludeId)
                            .last("LIMIT 1")
            );
            if (existed != null) {
                throw BizException.validationFailed("同一分类大类下分类项编码已存在");
            }
            return;
        }

        SysDictItem existed = sysDictItemMapper.selectOne(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, categoryType.getCode())
                        .eq(SysDictItem::getTenantId, getCurrentTenantId())
                        .eq(SysDictItem::getDictCode, dictCode)
                        .ne(excludeId != null, SysDictItem::getId, excludeId)
                        .last("LIMIT 1")
        );
        if (existed != null) {
            throw BizException.validationFailed("同一分类大类下分类项编码已存在");
        }
    }

    private void checkNameUnique(DictCategoryType categoryType, String dictName, Long excludeId) {
        if (categoryType.useRecipeTable()) {
            RecipeCategoryBridge existed = recipeCategoryBridgeMapper.selectOne(
                    new LambdaQueryWrapper<RecipeCategoryBridge>()
                            .eq(RecipeCategoryBridge::getCategoryName, dictName)
                            .and(wrapper -> wrapper
                                    .in(RecipeCategoryBridge::getCategoryCode, BUILTIN_RECIPE_CODES)
                                    .or()
                                    .eq(RecipeCategoryBridge::getTenantId, getCurrentTenantId()))
                            .ne(excludeId != null, RecipeCategoryBridge::getId, excludeId)
                            .last("LIMIT 1")
            );
            if (existed != null) {
                throw BizException.validationFailed("同一分类大类下分类项名称已存在");
            }
            return;
        }

        SysDictItem existed = sysDictItemMapper.selectOne(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, categoryType.getCode())
                        .eq(SysDictItem::getTenantId, getCurrentTenantId())
                        .eq(SysDictItem::getDictName, dictName)
                        .ne(excludeId != null, SysDictItem::getId, excludeId)
                        .last("LIMIT 1")
        );
        if (existed != null) {
            throw BizException.validationFailed("同一分类大类下分类项名称已存在");
        }
    }

    private void ensureCustomEditable(SysDictItem item) {
        if (Objects.equals(item.getIsSystem(), 1)) {
            throw BizException.validationFailed("系统内置分类项仅支持查看，不允许修改或删除");
        }
    }

    private DictCategoryType parseCategoryType(String categoryType) {
        DictCategoryType parsedType = DictCategoryType.of(categoryType);
        if (parsedType == null) {
            throw BizException.badRequest("不支持的分类大类");
        }
        return parsedType;
    }

    private SysDictItem getTenantScopedSysDictById(Long id, DictCategoryType categoryType) {
        SysDictItem item = sysDictItemMapper.selectById(id);
        if (item == null
                || !Objects.equals(item.getDeleted(), 0)
                || !Objects.equals(item.getTenantId(), getCurrentTenantId())
                || !StrUtil.equals(item.getDictType(), categoryType.getCode())) {
            throw BizException.notFound("字典项不存在");
        }
        return item;
    }

    private long countRecipeReferences(Long recipeCategoryId) {
        return queryCount(
                "SELECT COUNT(*) FROM recipe WHERE deleted = 0 AND tenant_id = ? AND category_id = ?",
                getCurrentTenantId(),
                recipeCategoryId
        );
    }

    private long countSysDictReferences(DictCategoryType categoryType, String dictValue) {
        if (StrUtil.isBlank(dictValue)) {
            return 0L;
        }
        return switchCount(categoryType, dictValue);
    }

    private long switchCount(DictCategoryType categoryType, String dictValue) {
        Long tenantId = getCurrentTenantId();
        switch (categoryType) {
            case WAREHOUSE_TYPE:
                return queryCount("SELECT COUNT(*) FROM wms_warehouse WHERE deleted = 0 AND tenant_id = ? AND warehouse_type = ?", tenantId, dictValue);
            case MATERIAL_CATEGORY:
                return queryCount("SELECT COUNT(*) FROM wms_material WHERE deleted = 0 AND tenant_id = ? AND material_category = ?", tenantId, dictValue);
            case SUPPLIER_TYPE:
                return queryCount("SELECT COUNT(*) FROM scm_supplier WHERE deleted = 0 AND tenant_id = ? AND supplier_type = ?", tenantId, dictValue);
            case DEVICE_TYPE:
                return queryCount("SELECT COUNT(*) FROM device_info WHERE deleted = 0 AND tenant_id = ? AND device_type = ?", tenantId, dictValue);
            case ORG_TYPE:
                return queryCount("SELECT COUNT(*) FROM sys_organization WHERE deleted = 0 AND tenant_id = ? AND org_type = ?", tenantId, dictValue);
            case EMPLOYEE_POSITION:
                return queryCount("SELECT COUNT(*) FROM sys_employee WHERE deleted = 0 AND tenant_id = ? AND position = ?", tenantId, dictValue);
            default:
                return 0L;
        }
    }

    private long queryCount(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count != null ? count : 0L;
    }

    private void forceUpdateRecipeCategoryRemark(Long id, String remark) {
        jdbcTemplate.update(
                "UPDATE recipe_category SET remark = ? WHERE id = ? AND deleted = 0 AND tenant_id = ?",
                remark,
                id,
                getCurrentTenantId()
        );
    }

    private void forceUpdateSysDictRemark(Long id, String remark) {
        jdbcTemplate.update(
                "UPDATE sys_dict SET remark = ? WHERE id = ? AND deleted = 0 AND tenant_id = ?",
                remark,
                id,
                getCurrentTenantId()
        );
    }

    private void cascadeBusinessValueChange(DictCategoryType categoryType, String oldValue, String newValue) {
        if (categoryType.useRecipeTable() || StrUtil.equals(oldValue, newValue) || StrUtil.isBlank(oldValue)) {
            return;
        }
        Long tenantId = getCurrentTenantId();
        switch (categoryType) {
            case WAREHOUSE_TYPE:
                jdbcTemplate.update("UPDATE wms_warehouse SET warehouse_type = ? WHERE deleted = 0 AND tenant_id = ? AND warehouse_type = ?", newValue, tenantId, oldValue);
                break;
            case MATERIAL_CATEGORY:
                jdbcTemplate.update("UPDATE wms_material SET material_category = ? WHERE deleted = 0 AND tenant_id = ? AND material_category = ?", newValue, tenantId, oldValue);
                break;
            case SUPPLIER_TYPE:
                jdbcTemplate.update("UPDATE scm_supplier SET supplier_type = ? WHERE deleted = 0 AND tenant_id = ? AND supplier_type = ?", newValue, tenantId, oldValue);
                break;
            case DEVICE_TYPE:
                jdbcTemplate.update("UPDATE device_info SET device_type = ? WHERE deleted = 0 AND tenant_id = ? AND device_type = ?", newValue, tenantId, oldValue);
                break;
            case ORG_TYPE:
                jdbcTemplate.update("UPDATE sys_organization SET org_type = ? WHERE deleted = 0 AND tenant_id = ? AND org_type = ?", newValue, tenantId, oldValue);
                break;
            case EMPLOYEE_POSITION:
                jdbcTemplate.update("UPDATE sys_employee SET position = ? WHERE deleted = 0 AND tenant_id = ? AND position = ?", newValue, tenantId, oldValue);
                break;
            default:
                break;
        }
    }

    private String buildDictValue(DictCategoryType categoryType, String dictCode, String dictName, Long id) {
        if (categoryType.useCodeAsValue()) {
            return dictCode;
        }
        if (categoryType.useNameAsValue()) {
            return dictName;
        }
        return id != null ? String.valueOf(id) : null;
    }

    private boolean isSystemRecipeCategory(RecipeCategoryBridge entity) {
        return entity != null && BUILTIN_RECIPE_CODES.contains(entity.getCategoryCode());
    }

    private String normalizeCode(String value) {
        String result = StrUtil.trimToEmpty(value);
        if (StrUtil.isBlank(result)) {
            throw BizException.validationFailed("分类项编码不能为空");
        }
        DictCategoryTextValidationUtil.validateDictCategoryCode(result);
        return result;
    }

    private String normalizeName(String value) {
        String result = StrUtil.trimToEmpty(value);
        if (StrUtil.isBlank(result)) {
            throw BizException.validationFailed("分类项名称不能为空");
        }
        DictCategoryTextValidationUtil.validateDictCategoryName(result);
        return result;
    }

    private String normalizeStatus(String value) {
        String result = StrUtil.blankToDefault(StrUtil.trim(value), "active");
        if (!Objects.equals(result, "active") && !Objects.equals(result, "inactive")) {
            throw BizException.badRequest("状态值只能是active或inactive");
        }
        return result;
    }

    private String normalizeRemark(String value) {
        String result = StrUtil.emptyToNull(StrUtil.trim(value));
        DictCategoryTextValidationUtil.validateDictCategoryRemark(result);
        return result;
    }

    private boolean syncExistingBuiltinSysDictItem(SysDictItem item, DictCategoryType categoryType, BuiltinItem builtinItem) {
        boolean changed = false;
        if (!StrUtil.equals(item.getDictName(), builtinItem.getName())) {
            item.setDictName(builtinItem.getName());
            changed = true;
        }
        if (!StrUtil.equals(item.getDictValue(), buildDictValue(categoryType, builtinItem.getCode(), builtinItem.getName(), item.getId()))) {
            item.setDictValue(buildDictValue(categoryType, builtinItem.getCode(), builtinItem.getName(), item.getId()));
            changed = true;
        }
        if (!Objects.equals(item.getSortOrder(), builtinItem.getSortOrder())) {
            item.setSortOrder(builtinItem.getSortOrder());
            changed = true;
        }
        if (!Objects.equals(item.getIsSystem(), 1)) {
            item.setIsSystem(1);
            changed = true;
        }
        if (!StrUtil.equals(item.getStatus(), "active")) {
            item.setStatus("active");
            changed = true;
        }
        if (!StrUtil.equals(item.getRemark(), "系统内置")) {
            item.setRemark("系统内置");
            changed = true;
        }
        if (!Objects.equals(item.getOrgId(), getTenantSharedOrgId())) {
            item.setOrgId(getTenantSharedOrgId());
            changed = true;
        }
        return applyBuiltinMaterialAreaFields(item, categoryType, builtinItem) || changed;
    }

    private boolean syncExistingBuiltinRecipeCategory(RecipeCategoryBridge entity, BuiltinItem builtinItem) {
        boolean changed = false;
        if (!StrUtil.equals(entity.getCategoryName(), builtinItem.getName())) {
            entity.setCategoryName(builtinItem.getName());
            changed = true;
        }
        if (!Objects.equals(entity.getSortOrder(), builtinItem.getSortOrder())) {
            entity.setSortOrder(builtinItem.getSortOrder());
            changed = true;
        }
        if (!StrUtil.equals(entity.getStatus(), "active")) {
            entity.setStatus("active");
            changed = true;
        }
        if (!StrUtil.equals(entity.getRemark(), "系统内置")) {
            entity.setRemark("系统内置");
            changed = true;
        }
        if (!Objects.equals(entity.getOrgId(), 0L)) {
            entity.setOrgId(0L);
            changed = true;
        }
        if (!Objects.equals(entity.getTenantId(), 0L)) {
            entity.setTenantId(0L);
            changed = true;
        }
        return changed;
    }

    private LambdaQueryWrapper<RecipeCategoryBridge> buildVisibleRecipeCategoryWrapper() {
        LambdaQueryWrapper<RecipeCategoryBridge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecipeCategoryBridge::getDeleted, 0);
        applyRecipeCategoryVisibility(wrapper);
        return wrapper;
    }

    private void applyRecipeCategoryVisibility(LambdaQueryWrapper<RecipeCategoryBridge> wrapper) {
        wrapper.and(query -> query
                .in(RecipeCategoryBridge::getCategoryCode, BUILTIN_RECIPE_CODES)
                .or()
                .eq(RecipeCategoryBridge::getTenantId, getCurrentTenantId()));
    }

    private RecipeCategoryBridge getVisibleRecipeCategoryById(Long id) {
        RecipeCategoryBridge recipeCategory = recipeCategoryBridgeMapper.selectById(id);
        if (recipeCategory == null
                || !Objects.equals(recipeCategory.getDeleted(), 0)
                || (!isSystemRecipeCategory(recipeCategory)
                && !Objects.equals(recipeCategory.getTenantId(), getCurrentTenantId()))) {
            throw BizException.notFound("字典项不存在");
        }
        return recipeCategory;
    }

    private boolean applyBuiltinMaterialAreaFields(SysDictItem item, DictCategoryType categoryType, BuiltinItem builtinItem) {
        if (!isMaterialCategory(categoryType) || builtinItem.getAreaCoefficient() == null) {
            return false;
        }
        boolean changed = false;
        BigDecimal builtinAreaCoefficient = builtinItem.getAreaCoefficient().setScale(4, RoundingMode.HALF_UP);
        if (!sameDecimal(item.getAreaCoefficient(), builtinAreaCoefficient)) {
            item.setAreaCoefficient(builtinAreaCoefficient);
            changed = true;
        }
        if (!StrUtil.equals(item.getAreaCoefficientSource(), "system")) {
            item.setAreaCoefficientSource("system");
            changed = true;
        }
        if (item.getAiSuggestedAreaCoefficient() != null) {
            item.setAiSuggestedAreaCoefficient(null);
            changed = true;
        }
        if (StrUtil.isNotBlank(item.getAiSuggestionReason())) {
            item.setAiSuggestionReason(null);
            changed = true;
        }
        if (item.getAiSuggestionGeneratedAt() != null) {
            item.setAiSuggestionGeneratedAt(null);
            changed = true;
        }
        return changed;
    }

    private DictCategoryType parseMaterialCategoryType(String categoryType) {
        DictCategoryType parsedType = parseCategoryType(categoryType);
        if (!isMaterialCategory(parsedType)) {
            throw BizException.validationFailed("仅物料类别支持面积系数修正与回溯功能");
        }
        return parsedType;
    }

    private void requireAdminForAreaCoefficientRecalc() {
        if (!dataScopeService.isAdminUser()) {
            throw BizException.forbidden("仅管理员支持触发面积系数历史回溯重算");
        }
    }

    private void validateMaterialCategoryUpdateVersion(SysDictItem item, DictCategoryUpdateDTO dto) {
        if (dto.getLastKnownUpdatedAt() == null) {
            return;
        }
        if (!Objects.equals(item.getUpdatedAt(), dto.getLastKnownUpdatedAt())) {
            throw BizException.conflict("当前物料类别已被其他用户更新，请刷新后重试。最新更新时间：" + formatDateTime(item.getUpdatedAt()));
        }
    }

    private void ensureAreaCoefficientUnlocked(SysDictItem item, String scene) {
        materialCategoryCoefficientLockService.assertUnlockedByCategoryNames(List.of(item.getDictName()), scene);
    }

    private void validateAreaCoefficientImpactConfirmation(Boolean impactConfirmed) {
        if (!Boolean.TRUE.equals(impactConfirmed)) {
            throw BizException.validationFailed("请先确认面积系数变更影响后再提交保存");
        }
    }

    private String normalizeAreaCoefficientEffectScope(String value) {
        String normalized = StrUtil.blankToDefault(StrUtil.trim(value), IMPACT_SCOPE_SUBSEQUENT_ONLY);
        if (!Objects.equals(normalized, IMPACT_SCOPE_SUBSEQUENT_ONLY)
                && !Objects.equals(normalized, IMPACT_SCOPE_MANUAL_RECALC)) {
            throw BizException.validationFailed("面积系数生效口径值不合法");
        }
        return normalized;
    }

    private SysDictAreaCoefficientCorrection createAreaCoefficientCorrectionRecord(
            SysDictItem item,
            String dictCode,
            String dictName,
            String dictValue,
            MaterialCategoryFieldState materialFieldState,
            String effectScope) {
        Integer currentMaxVersion = areaCoefficientCorrectionMapper.selectList(
                new LambdaQueryWrapper<SysDictAreaCoefficientCorrection>()
                        .select(SysDictAreaCoefficientCorrection::getCorrectionVersion)
                        .eq(SysDictAreaCoefficientCorrection::getDictId, item.getId())
                        .eq(SysDictAreaCoefficientCorrection::getTenantId, getCurrentTenantId())
                        .eq(SysDictAreaCoefficientCorrection::getDeleted, 0)
                        .orderByDesc(SysDictAreaCoefficientCorrection::getCorrectionVersion)
                        .last("LIMIT 1")
        ).stream().findFirst().map(SysDictAreaCoefficientCorrection::getCorrectionVersion).orElse(0);

        SysDictAreaCoefficientCorrection correction = new SysDictAreaCoefficientCorrection();
        correction.setDictId(item.getId());
        correction.setDictType(item.getDictType());
        correction.setDictCode(dictCode);
        correction.setDictName(dictName);
        correction.setDictValue(dictValue);
        correction.setCorrectionVersion(currentMaxVersion + 1);
        correction.setOldAreaCoefficient(item.getAreaCoefficient());
        correction.setNewAreaCoefficient(materialFieldState.getAreaCoefficient());
        correction.setOldAreaCoefficientSource(item.getAreaCoefficientSource());
        correction.setNewAreaCoefficientSource(materialFieldState.getAreaCoefficientSource());
        correction.setImpactScope(effectScope);
        correction.setImpactAcknowledged(1);
        correction.setImpactAcknowledgedAt(LocalDateTime.now());
        correction.setRecalcStatus(Objects.equals(effectScope, IMPACT_SCOPE_MANUAL_RECALC)
                ? RECALC_STATUS_PENDING
                : RECALC_STATUS_NOT_APPLICABLE);
        correction.setOperatorId(UserContext.getUserId());
        correction.setOperatorName(resolveCurrentOperatorName());
        correction.setOrgId(getCurrentOrgId());
        correction.setTenantId(getCurrentTenantId());
        areaCoefficientCorrectionMapper.insert(correction);
        return correction;
    }

    private SysDictAreaCoefficientCorrection getAreaCoefficientCorrection(Long dictId, Long correctionId) {
        SysDictAreaCoefficientCorrection correction = areaCoefficientCorrectionMapper.selectOne(
                new LambdaQueryWrapper<SysDictAreaCoefficientCorrection>()
                        .eq(SysDictAreaCoefficientCorrection::getId, correctionId)
                        .eq(SysDictAreaCoefficientCorrection::getDictId, dictId)
                        .eq(SysDictAreaCoefficientCorrection::getTenantId, getCurrentTenantId())
                        .eq(SysDictAreaCoefficientCorrection::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (correction == null) {
            throw BizException.notFound("面积系数修正版本不存在");
        }
        return correction;
    }

    private void validateAreaCoefficientRecalcStart(SysDictItem item, SysDictAreaCoefficientCorrection correction) {
        if (!Objects.equals(correction.getImpactScope(), IMPACT_SCOPE_MANUAL_RECALC)) {
            throw BizException.validationFailed("当前修正版本仅对后续业务生效，不支持历史回溯重算");
        }
        SysDictAreaCoefficientCorrection latestCorrection = areaCoefficientCorrectionMapper.selectOne(
                new LambdaQueryWrapper<SysDictAreaCoefficientCorrection>()
                        .eq(SysDictAreaCoefficientCorrection::getDictId, item.getId())
                        .eq(SysDictAreaCoefficientCorrection::getTenantId, getCurrentTenantId())
                        .eq(SysDictAreaCoefficientCorrection::getDeleted, 0)
                        .orderByDesc(SysDictAreaCoefficientCorrection::getCorrectionVersion)
                        .last("LIMIT 1")
        );
        if (latestCorrection == null || !Objects.equals(latestCorrection.getId(), correction.getId())) {
            throw BizException.validationFailed("仅支持对当前生效的最新面积系数修正版本执行历史回溯重算");
        }
        if (!sameDecimal(item.getAreaCoefficient(), correction.getNewAreaCoefficient())) {
            throw BizException.validationFailed("当前物料类别面积系数已变更，请刷新修正历史后重新选择最新版本");
        }
    }

    private SysDictAreaCoefficientRecalcTask createAreaCoefficientRecalcTask(SysDictItem item, SysDictAreaCoefficientCorrection correction) {
        List<RecalcStepDefinition> stepDefinitions = buildAreaCoefficientRecalcSteps();
        SysDictAreaCoefficientRecalcTask task = new SysDictAreaCoefficientRecalcTask();
        task.setTaskNo(generateAreaCoefficientRecalcTaskNo(item.getId()));
        task.setDictId(item.getId());
        task.setDictType(item.getDictType());
        task.setDictCode(item.getDictCode());
        task.setDictName(item.getDictName());
        task.setDictValue(item.getDictValue());
        task.setCorrectionId(correction.getId());
        task.setCorrectionVersion(correction.getCorrectionVersion());
        task.setOldAreaCoefficient(correction.getOldAreaCoefficient());
        task.setNewAreaCoefficient(correction.getNewAreaCoefficient());
        task.setStatus(RECALC_STATUS_PENDING);
        task.setProgressPercent(0);
        task.setTotalSteps(stepDefinitions.size());
        task.setCompletedSteps(0);
        task.setSuccessCount(0);
        task.setFailureCount(0);
        task.setStartedBy(UserContext.getUserId());
        task.setStartedByName(resolveCurrentOperatorName());
        task.setOrgId(getCurrentOrgId());
        task.setTenantId(getCurrentTenantId());
        areaCoefficientRecalcTaskMapper.insert(task);
        return task;
    }

    private void lockAreaCoefficient(
            SysDictItem item,
            SysDictAreaCoefficientCorrection correction,
            Long taskId,
            String lockType,
            String lockReason) {
        SysDictAreaCoefficientLock lock = areaCoefficientLockMapper.selectOne(
                new LambdaQueryWrapper<SysDictAreaCoefficientLock>()
                        .eq(SysDictAreaCoefficientLock::getDictId, item.getId())
                        .eq(SysDictAreaCoefficientLock::getTenantId, getCurrentTenantId())
                        .eq(SysDictAreaCoefficientLock::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (lock == null) {
            lock = new SysDictAreaCoefficientLock();
            lock.setDictId(item.getId());
            lock.setOrgId(getCurrentOrgId());
            lock.setTenantId(getCurrentTenantId());
        }
        lock.setDictType(item.getDictType());
        lock.setDictCode(item.getDictCode());
        lock.setDictName(item.getDictName());
        lock.setDictValue(item.getDictValue());
        lock.setLockStatus(LOCK_STATUS_LOCKED);
        lock.setLockType(lockType);
        lock.setLockReason(lockReason);
        lock.setCorrectionId(correction != null ? correction.getId() : null);
        lock.setCorrectionVersion(correction != null ? correction.getCorrectionVersion() : null);
        lock.setTaskId(taskId);
        lock.setLockedBy(UserContext.getUserId());
        lock.setLockedByName(resolveCurrentOperatorName());
        lock.setLockedAt(LocalDateTime.now());
        lock.setReleasedBy(null);
        lock.setReleasedByName(null);
        lock.setReleasedAt(null);
        if (lock.getId() == null) {
            areaCoefficientLockMapper.insert(lock);
        } else {
            areaCoefficientLockMapper.updateById(lock);
        }
    }

    private void releaseAreaCoefficientLock(SysDictItem item, SysDictAreaCoefficientCorrection correction, Long taskId) {
        SysDictAreaCoefficientLock lock = areaCoefficientLockMapper.selectOne(
                new LambdaQueryWrapper<SysDictAreaCoefficientLock>()
                        .eq(SysDictAreaCoefficientLock::getDictId, item.getId())
                        .eq(SysDictAreaCoefficientLock::getTenantId, getCurrentTenantId())
                        .eq(SysDictAreaCoefficientLock::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (lock == null) {
            return;
        }
        lock.setDictType(item.getDictType());
        lock.setDictCode(item.getDictCode());
        lock.setDictName(item.getDictName());
        lock.setDictValue(item.getDictValue());
        lock.setLockStatus(LOCK_STATUS_UNLOCKED);
        lock.setCorrectionId(correction != null ? correction.getId() : lock.getCorrectionId());
        lock.setCorrectionVersion(correction != null ? correction.getCorrectionVersion() : lock.getCorrectionVersion());
        lock.setTaskId(taskId != null ? taskId : lock.getTaskId());
        lock.setReleasedBy(UserContext.getUserId());
        lock.setReleasedByName(resolveCurrentOperatorName());
        lock.setReleasedAt(LocalDateTime.now());
        areaCoefficientLockMapper.updateById(lock);
    }

    private DictCategoryAreaCoefficientHistoryVO buildAreaCoefficientHistoryVO(
            SysDictAreaCoefficientCorrection correction,
            SysDictAreaCoefficientRecalcTask task,
            Long currentLatestCorrectionId) {
        DictCategoryAreaCoefficientHistoryVO vo = new DictCategoryAreaCoefficientHistoryVO();
        vo.setId(correction.getId());
        vo.setCorrectionVersion(correction.getCorrectionVersion());
        vo.setOldAreaCoefficient(correction.getOldAreaCoefficient());
        vo.setNewAreaCoefficient(correction.getNewAreaCoefficient());
        vo.setOldAreaCoefficientSource(correction.getOldAreaCoefficientSource());
        vo.setNewAreaCoefficientSource(correction.getNewAreaCoefficientSource());
        vo.setImpactScope(correction.getImpactScope());
        vo.setImpactAcknowledged(Objects.equals(correction.getImpactAcknowledged(), 1));
        vo.setImpactAcknowledgedAt(correction.getImpactAcknowledgedAt());
        vo.setRecalcStatus(correction.getRecalcStatus());
        vo.setRecalcTaskId(correction.getRecalcTaskId());
        vo.setRecalcCompletedAt(correction.getRecalcCompletedAt());
        vo.setOperatorId(correction.getOperatorId());
        vo.setOperatorName(correction.getOperatorName());
        vo.setCurrentVersion(Objects.equals(correction.getId(), currentLatestCorrectionId));
        vo.setCreatedAt(correction.getCreatedAt());
        vo.setRecalcAvailable(
                Objects.equals(correction.getId(), currentLatestCorrectionId)
                        && Objects.equals(correction.getImpactScope(), IMPACT_SCOPE_MANUAL_RECALC)
                        && !Objects.equals(correction.getRecalcStatus(), RECALC_STATUS_RUNNING)
        );
        if (task != null) {
            vo.setRecalcTaskNo(task.getTaskNo());
            vo.setRecalcProgressPercent(task.getProgressPercent());
            vo.setRecalcResultMessage(task.getResultMessage());
        }
        return vo;
    }

    private DictCategoryAreaCoefficientRecalcTaskVO buildAreaCoefficientRecalcTaskVO(
            SysDictAreaCoefficientRecalcTask task,
            List<SysDictAreaCoefficientRecalcDetail> details) {
        DictCategoryAreaCoefficientRecalcTaskVO vo = new DictCategoryAreaCoefficientRecalcTaskVO();
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setCorrectionId(task.getCorrectionId());
        vo.setCorrectionVersion(task.getCorrectionVersion());
        vo.setOldAreaCoefficient(task.getOldAreaCoefficient());
        vo.setNewAreaCoefficient(task.getNewAreaCoefficient());
        vo.setStatus(task.getStatus());
        vo.setProgressPercent(task.getProgressPercent());
        vo.setTotalSteps(task.getTotalSteps());
        vo.setCompletedSteps(task.getCompletedSteps());
        vo.setSuccessCount(task.getSuccessCount());
        vo.setFailureCount(task.getFailureCount());
        vo.setResultMessage(task.getResultMessage());
        vo.setStartedBy(task.getStartedBy());
        vo.setStartedByName(task.getStartedByName());
        vo.setStartedAt(task.getStartedAt());
        vo.setFinishedAt(task.getFinishedAt());
        vo.setDetails(details.stream().map(this::buildAreaCoefficientRecalcDetailVO).collect(Collectors.toList()));
        return vo;
    }

    private DictCategoryAreaCoefficientRecalcDetailVO buildAreaCoefficientRecalcDetailVO(SysDictAreaCoefficientRecalcDetail detail) {
        DictCategoryAreaCoefficientRecalcDetailVO vo = new DictCategoryAreaCoefficientRecalcDetailVO();
        vo.setId(detail.getId());
        vo.setDetailCode(detail.getDetailCode());
        vo.setDetailName(detail.getDetailName());
        vo.setDetailType(detail.getDetailType());
        vo.setStatus(detail.getStatus());
        vo.setAffectedRecordCount(detail.getAffectedRecordCount());
        vo.setQuantityTotal(detail.getQuantityTotal());
        vo.setOldAreaTotal(detail.getOldAreaTotal());
        vo.setNewAreaTotal(detail.getNewAreaTotal());
        vo.setDeltaAreaTotal(detail.getDeltaAreaTotal());
        vo.setDetailMessage(detail.getDetailMessage());
        vo.setSnapshotPayload(detail.getSnapshotPayload());
        return vo;
    }

    private AsyncOperatorContext captureAsyncOperatorContext() {
        return new AsyncOperatorContext(
                UserContext.getUserId(),
                UserContext.getUsername(),
                UserContext.getRealName(),
                getCurrentOrgId(),
                getCurrentTenantId()
        );
    }

    private void executeAreaCoefficientRecalcAsync(
            AsyncOperatorContext operatorContext,
            SysDictItem item,
            SysDictAreaCoefficientCorrection correction,
            SysDictAreaCoefficientRecalcTask task) {
        applyAsyncOperatorContext(operatorContext);
        SysDictAreaCoefficientRecalcDetail currentDetail = null;
        try {
            task.setStatus(RECALC_STATUS_RUNNING);
            task.setStartedAt(LocalDateTime.now());
            task.setProgressPercent(0);
            task.setCompletedSteps(0);
            task.setSuccessCount(0);
            task.setFailureCount(0);
            task.setResultMessage("面积系数历史回溯重算执行中");
            areaCoefficientRecalcTaskMapper.updateById(task);

            List<RecalcStepDefinition> steps = buildAreaCoefficientRecalcSteps();
            List<SysDictAreaCoefficientRecalcDetail> detailRows = new ArrayList<>(steps.size());
            for (RecalcStepDefinition step : steps) {
                SysDictAreaCoefficientRecalcDetail detail = new SysDictAreaCoefficientRecalcDetail();
                detail.setTaskId(task.getId());
                detail.setDetailCode(step.getCode());
                detail.setDetailName(step.getName());
                detail.setDetailType(step.getType());
                detail.setStatus(RECALC_STATUS_PENDING);
                detail.setAffectedRecordCount(0L);
                detail.setQuantityTotal(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
                detail.setOldAreaTotal(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
                detail.setNewAreaTotal(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
                detail.setDeltaAreaTotal(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
                detail.setDetailMessage("等待执行");
                detail.setSnapshotPayload(JSONUtil.toJsonStr(Map.of(
                        "detailCode", step.getCode(),
                        "detailType", step.getType(),
                        "dictId", item.getId(),
                        "dictName", item.getDictName()
                )));
                detail.setOrgId(item.getOrgId());
                detail.setTenantId(item.getTenantId());
                areaCoefficientRecalcDetailMapper.insert(detail);
                detailRows.add(detail);
            }

            for (int index = 0; index < steps.size(); index++) {
                RecalcStepDefinition step = steps.get(index);
                currentDetail = detailRows.get(index);
                currentDetail.setStatus(RECALC_STATUS_RUNNING);
                currentDetail.setDetailMessage("执行中");
                areaCoefficientRecalcDetailMapper.updateById(currentDetail);

                AggregateStats aggregateStats = Objects.equals(step.getCode(), "summary")
                        ? buildSummaryAggregateStats(detailRows.subList(0, index))
                        : queryAggregateStats(step.getSql(), task.getTenantId(), item.getDictValue());
                fillRecalcDetail(currentDetail, step, aggregateStats, correction);
                areaCoefficientRecalcDetailMapper.updateById(currentDetail);

                task.setCompletedSteps(index + 1);
                task.setSuccessCount(index + 1);
                task.setProgressPercent(calculateProgressPercent(index + 1, steps.size()));
                task.setResultMessage("已完成 " + (index + 1) + "/" + steps.size() + " 个重算步骤");
                areaCoefficientRecalcTaskMapper.updateById(task);
            }

            task.setStatus(RECALC_STATUS_COMPLETED);
            task.setProgressPercent(100);
            task.setCompletedSteps(steps.size());
            task.setSuccessCount(steps.size());
            task.setFailureCount(0);
            task.setFinishedAt(LocalDateTime.now());
            task.setResultMessage("面积系数历史回溯重算完成，共完成 " + steps.size() + " 个步骤");
            areaCoefficientRecalcTaskMapper.updateById(task);

            correction.setRecalcStatus(RECALC_STATUS_COMPLETED);
            correction.setRecalcTaskId(task.getId());
            correction.setRecalcCompletedAt(task.getFinishedAt());
            areaCoefficientCorrectionMapper.updateById(correction);
            releaseAreaCoefficientLock(item, correction, task.getId());

            auditLogService.log(
                    AuditModule.SYS_DICT_CATEGORY,
                    AuditOperationType.UPDATE,
                    item.getId(),
                    item.getDictCode(),
                    "物料类别面积系数历史回溯重算完成：" + item.getDictName() + "，任务号 " + task.getTaskNo(),
                    null,
                    JSONUtil.toJsonStr(Map.of(
                            "taskId", task.getId(),
                            "taskNo", task.getTaskNo(),
                            "progressPercent", task.getProgressPercent(),
                            "finishedAt", formatDateTime(task.getFinishedAt())
                    ))
            );
        } catch (Exception ex) {
            log.error("物料类别面积系数历史回溯重算失败, dictId={}, taskId={}, error={}", item.getId(), task.getId(), ex.getMessage(), ex);
            if (currentDetail != null) {
                currentDetail.setStatus(RECALC_STATUS_FAILED);
                currentDetail.setDetailMessage("执行失败：" + shortenText(ex.getMessage(), 400));
                currentDetail.setSnapshotPayload(JSONUtil.toJsonStr(Map.of(
                        "dictId", item.getId(),
                        "dictName", item.getDictName(),
                        "error", StrUtil.blankToDefault(ex.getMessage(), "未知异常")
                )));
                areaCoefficientRecalcDetailMapper.updateById(currentDetail);
            }
            task.setStatus(RECALC_STATUS_FAILED);
            task.setFailureCount(Math.max(1, task.getFailureCount() == null ? 0 : task.getFailureCount()));
            task.setFinishedAt(LocalDateTime.now());
            task.setProgressPercent(calculateProgressPercent(
                    task.getCompletedSteps() == null ? 0 : task.getCompletedSteps(),
                    task.getTotalSteps() == null ? 1 : task.getTotalSteps()
            ));
            task.setResultMessage("面积系数历史回溯重算失败：" + shortenText(StrUtil.blankToDefault(ex.getMessage(), "未知异常"), 300));
            areaCoefficientRecalcTaskMapper.updateById(task);

            correction.setRecalcStatus(RECALC_STATUS_FAILED);
            correction.setRecalcTaskId(task.getId());
            correction.setRecalcCompletedAt(null);
            areaCoefficientCorrectionMapper.updateById(correction);
            releaseAreaCoefficientLock(item, correction, task.getId());

            auditLogService.log(
                    AuditModule.SYS_DICT_CATEGORY,
                    AuditOperationType.UPDATE,
                    item.getId(),
                    item.getDictCode(),
                    "物料类别面积系数历史回溯重算失败：" + item.getDictName() + "，任务号 " + task.getTaskNo(),
                    null,
                    null,
                    "failed",
                    shortenText(StrUtil.blankToDefault(ex.getMessage(), "未知异常"), 500)
            );
        } finally {
            UserContext.clear();
        }
    }

    private List<RecalcStepDefinition> buildAreaCoefficientRecalcSteps() {
        return List.of(
                new RecalcStepDefinition(
                        "inventory",
                        "库存批次回溯重算",
                        "inventory",
                        """
                        SELECT COUNT(*) AS affectedCount,
                               COALESCE(SUM(i.quantity), 0) AS quantityTotal
                          FROM wms_inventory i
                          JOIN wms_material m
                            ON m.id = i.material_id
                           AND m.deleted = 0
                           AND m.tenant_id = i.tenant_id
                         WHERE i.tenant_id = ?
                           AND m.material_category = ?
                        """
                ),
                new RecalcStepDefinition(
                        "inbound_report",
                        "入库统计回溯重算",
                        "report",
                        """
                        SELECT COUNT(*) AS affectedCount,
                               COALESCE(SUM(ii.quantity), 0) AS quantityTotal
                          FROM wms_inbound_order_item ii
                          JOIN wms_inbound_order io
                            ON io.id = ii.inbound_id
                           AND io.deleted = 0
                         JOIN wms_material m
                            ON m.id = ii.material_id
                           AND m.deleted = 0
                           AND m.tenant_id = io.tenant_id
                         WHERE io.tenant_id = ?
                           AND m.material_category = ?
                        """
                ),
                new RecalcStepDefinition(
                        "outbound_report",
                        "出库统计回溯重算",
                        "report",
                        """
                        SELECT COUNT(*) AS affectedCount,
                               COALESCE(SUM(oi.quantity), 0) AS quantityTotal
                          FROM wms_outbound_order_item oi
                          JOIN wms_outbound_order oo
                            ON oo.id = oi.outbound_id
                           AND oo.deleted = 0
                         JOIN wms_material m
                            ON m.id = oi.material_id
                           AND m.deleted = 0
                           AND m.tenant_id = oo.tenant_id
                         WHERE oo.tenant_id = ?
                           AND m.material_category = ?
                        """
                ),
                new RecalcStepDefinition(
                        "stocktake_report",
                        "盘点统计回溯重算",
                        "report",
                        """
                        SELECT COUNT(*) AS affectedCount,
                               COALESCE(SUM(COALESCE(si.actual_qty, si.system_qty)), 0) AS quantityTotal
                          FROM wms_stocktake_order_item si
                          JOIN wms_stocktake_order so
                            ON so.id = si.stocktake_id
                           AND so.deleted = 0
                         JOIN wms_material m
                            ON m.id = si.material_id
                           AND m.deleted = 0
                           AND m.tenant_id = so.tenant_id
                         WHERE so.tenant_id = ?
                           AND m.material_category = ?
                        """
                ),
                new RecalcStepDefinition(
                        "purchase_plan",
                        "采购计划运算回溯重算",
                        "procurement",
                        """
                        SELECT COUNT(*) AS affectedCount,
                               COALESCE(SUM(ppi.plan_qty), 0) AS quantityTotal
                          FROM scm_purchase_plan_item ppi
                          JOIN scm_purchase_plan pp
                            ON pp.id = ppi.plan_id
                           AND pp.deleted = 0
                         JOIN wms_material m
                            ON m.id = ppi.material_id
                           AND m.deleted = 0
                           AND m.tenant_id = pp.tenant_id
                         WHERE pp.tenant_id = ?
                           AND m.material_category = ?
                        """
                ),
                new RecalcStepDefinition(
                        "purchase_order",
                        "采购订单运算回溯重算",
                        "procurement",
                        """
                        SELECT COUNT(*) AS affectedCount,
                               COALESCE(SUM(poi.order_qty), 0) AS quantityTotal
                          FROM scm_purchase_order_item poi
                          JOIN scm_purchase_order po
                            ON po.id = poi.order_id
                           AND po.deleted = 0
                         JOIN wms_material m
                            ON m.id = poi.material_id
                           AND m.deleted = 0
                           AND m.tenant_id = po.tenant_id
                         WHERE po.tenant_id = ?
                           AND m.material_category = ?
                        """
                ),
                new RecalcStepDefinition(
                        "recipe_ingredient",
                        "AI配比测算回溯重算",
                        "ai_measurement",
                        """
                        SELECT COUNT(*) AS affectedCount,
                               COALESCE(SUM(ri.quantity), 0) AS quantityTotal
                          FROM recipe_ingredient ri
                          JOIN recipe r
                            ON r.id = ri.recipe_id
                           AND r.deleted = 0
                         JOIN wms_material m
                            ON m.id = ri.material_id
                           AND m.deleted = 0
                           AND m.tenant_id = r.tenant_id
                         WHERE ri.deleted = 0
                           AND r.tenant_id = ?
                           AND m.material_category = ?
                        """
                ),
                new RecalcStepDefinition(
                        "recipe_plan_projection",
                        "AI测算排班回溯重算",
                        "ai_measurement",
                        """
                        SELECT COUNT(*) AS affectedCount,
                               COALESCE(SUM(ri.quantity * COALESCE(rpi.planned_servings, 0)), 0) AS quantityTotal
                          FROM recipe_plan_item rpi
                          JOIN recipe_plan rp
                            ON rp.id = rpi.plan_id
                           AND rp.deleted = 0
                         JOIN recipe_ingredient ri
                            ON ri.recipe_id = rpi.recipe_id
                           AND ri.deleted = 0
                         JOIN wms_material m
                            ON m.id = ri.material_id
                           AND m.deleted = 0
                           AND m.tenant_id = rp.tenant_id
                         WHERE rpi.deleted = 0
                           AND rp.tenant_id = ?
                           AND m.material_category = ?
                        """
                ),
                new RecalcStepDefinition(
                        "summary",
                        "历史回溯重算汇总",
                        "summary",
                        null
                )
        );
    }

    private AggregateStats queryAggregateStats(String sql, Long tenantId, String materialCategoryValue) {
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, tenantId, materialCategoryValue);
        long affectedCount = toLongValue(row.get("affectedCount"));
        BigDecimal quantityTotal = toBigDecimal(row.get("quantityTotal"));
        return new AggregateStats(affectedCount, quantityTotal);
    }

    private AggregateStats buildSummaryAggregateStats(List<SysDictAreaCoefficientRecalcDetail> completedDetails) {
        long affectedCount = completedDetails.stream()
                .filter(detail -> !Objects.equals(detail.getDetailType(), "summary"))
                .map(SysDictAreaCoefficientRecalcDetail::getAffectedRecordCount)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        BigDecimal quantityTotal = completedDetails.stream()
                .filter(detail -> !Objects.equals(detail.getDetailType(), "summary"))
                .map(SysDictAreaCoefficientRecalcDetail::getQuantityTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AggregateStats(affectedCount, quantityTotal);
    }

    private void fillRecalcDetail(
            SysDictAreaCoefficientRecalcDetail detail,
            RecalcStepDefinition step,
            AggregateStats aggregateStats,
            SysDictAreaCoefficientCorrection correction) {
        BigDecimal quantityTotal = aggregateStats.getQuantityTotal().setScale(4, RoundingMode.HALF_UP);
        BigDecimal oldAreaTotal = quantityTotal.multiply(correction.getOldAreaCoefficient()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal newAreaTotal = quantityTotal.multiply(correction.getNewAreaCoefficient()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal deltaAreaTotal = newAreaTotal.subtract(oldAreaTotal).setScale(4, RoundingMode.HALF_UP);
        detail.setStatus(RECALC_STATUS_COMPLETED);
        detail.setAffectedRecordCount(aggregateStats.getAffectedCount());
        detail.setQuantityTotal(quantityTotal);
        detail.setOldAreaTotal(oldAreaTotal);
        detail.setNewAreaTotal(newAreaTotal);
        detail.setDeltaAreaTotal(deltaAreaTotal);
        detail.setDetailMessage(step.getName() + "完成，影响记录 " + aggregateStats.getAffectedCount() + " 条");
        detail.setSnapshotPayload(JSONUtil.toJsonStr(Map.of(
                "detailCode", step.getCode(),
                "detailType", step.getType(),
                "affectedRecordCount", aggregateStats.getAffectedCount(),
                "quantityTotal", quantityTotal,
                "oldAreaCoefficient", correction.getOldAreaCoefficient(),
                "newAreaCoefficient", correction.getNewAreaCoefficient(),
                "deltaAreaTotal", deltaAreaTotal
        )));
    }

    private int calculateProgressPercent(int completedSteps, int totalSteps) {
        if (totalSteps <= 0) {
            return 0;
        }
        return Math.min(100, Math.max(0, completedSteps * 100 / totalSteps));
    }

    private void applyAsyncOperatorContext(AsyncOperatorContext context) {
        UserContext userContext = new UserContext();
        userContext.setUserId(context.getUserId());
        userContext.setUsername(context.getUsername());
        userContext.setRealName(context.getRealName());
        userContext.setOrgId(context.getOrgId());
        userContext.setTenantId(context.getTenantId());
        UserContext.set(userContext);
    }

    private String generateAreaCoefficientRecalcTaskNo(Long dictId) {
        return "ACR" + System.currentTimeMillis() + (dictId == null ? "" : dictId);
    }

    private long toLongValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(4, RoundingMode.HALF_UP);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }

    private String resolveCurrentOperatorName() {
        return StrUtil.blankToDefault(UserContext.getRealName(), UserContext.getUsername());
    }

    private Map<String, Object> buildAreaCoefficientCorrectionAuditData(
            SysDictAreaCoefficientCorrection correction,
            boolean beforeSnapshot) {
        Map<String, Object> data = new HashMap<>();
        data.put("correctionId", correction.getId());
        data.put("correctionVersion", correction.getCorrectionVersion());
        if (beforeSnapshot) {
            data.put("areaCoefficient", correction.getOldAreaCoefficient());
            data.put("areaCoefficientSource", correction.getOldAreaCoefficientSource());
            return data;
        }
        data.put("areaCoefficient", correction.getNewAreaCoefficient());
        data.put("areaCoefficientSource", correction.getNewAreaCoefficientSource());
        data.put("impactScope", correction.getImpactScope());
        data.put("recalcStatus", correction.getRecalcStatus());
        return data;
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "—";
        }
        return value.toString().replace('T', ' ');
    }

    private String shortenText(String value, int maxLength) {
        String normalized = StrUtil.blankToDefault(value, "");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(maxLength - 3, 0)) + "...";
    }

    private MaterialCategoryFieldState resolveMaterialCategoryFieldState(
            DictCategoryType categoryType,
            BigDecimal areaCoefficient,
            String areaCoefficientSource,
            BigDecimal aiSuggestedAreaCoefficient,
            String aiSuggestionReason,
            LocalDateTime aiSuggestionGeneratedAt) {
        if (!isMaterialCategory(categoryType)) {
            return MaterialCategoryFieldState.empty();
        }

        BigDecimal normalizedAreaCoefficient = normalizeRequiredAreaCoefficient(areaCoefficient);
        BigDecimal normalizedAiSuggestedAreaCoefficient = normalizeOptionalAreaCoefficient(aiSuggestedAreaCoefficient, "AI建议面积系数");
        String normalizedAreaCoefficientSource = normalizeAreaCoefficientSource(areaCoefficientSource);
        String normalizedAiSuggestionReason = normalizeAiSuggestionReason(aiSuggestionReason);
        LocalDateTime normalizedAiSuggestionGeneratedAt = aiSuggestionGeneratedAt;

        if ("system".equals(normalizedAreaCoefficientSource)) {
            normalizedAreaCoefficientSource = "manual";
        }
        if ("ai".equals(normalizedAreaCoefficientSource) && normalizedAiSuggestedAreaCoefficient == null) {
            normalizedAiSuggestedAreaCoefficient = normalizedAreaCoefficient;
        }
        if ("ai".equals(normalizedAreaCoefficientSource)
                && normalizedAiSuggestedAreaCoefficient != null
                && normalizedAreaCoefficient.compareTo(normalizedAiSuggestedAreaCoefficient) != 0) {
            normalizedAreaCoefficientSource = "manual";
        }
        if (StrUtil.isBlank(normalizedAreaCoefficientSource)) {
            normalizedAreaCoefficientSource = "manual";
        }
        if (normalizedAiSuggestedAreaCoefficient != null && normalizedAiSuggestionGeneratedAt == null) {
            normalizedAiSuggestionGeneratedAt = LocalDateTime.now();
        }
        if (normalizedAiSuggestedAreaCoefficient != null && StrUtil.isBlank(normalizedAiSuggestionReason)) {
            normalizedAiSuggestionReason = DEFAULT_AI_REASON;
        }

        return new MaterialCategoryFieldState(
                normalizedAreaCoefficient,
                normalizedAreaCoefficientSource,
                normalizedAiSuggestedAreaCoefficient,
                normalizedAiSuggestionReason,
                normalizedAiSuggestionGeneratedAt
        );
    }

    private void applyMaterialCategoryFields(SysDictItem item, MaterialCategoryFieldState state) {
        item.setAreaCoefficient(state.getAreaCoefficient());
        item.setAreaCoefficientSource(state.getAreaCoefficientSource());
        item.setAiSuggestedAreaCoefficient(state.getAiSuggestedAreaCoefficient());
        item.setAiSuggestionReason(state.getAiSuggestionReason());
        item.setAiSuggestionGeneratedAt(state.getAiSuggestionGeneratedAt());
    }

    private BigDecimal normalizeRequiredAreaCoefficient(BigDecimal value) {
        if (value == null) {
            throw BizException.validationFailed("请输入物料类别统一面积系数（㎡/单件）");
        }
        return normalizeAreaCoefficient(value, "物料类别统一面积系数（㎡/单件）");
    }

    private BigDecimal normalizeOptionalAreaCoefficient(BigDecimal value, String fieldName) {
        if (value == null) {
            return null;
        }
        return normalizeAreaCoefficient(value, fieldName);
    }

    private BigDecimal normalizeAreaCoefficient(BigDecimal value, String fieldName) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.validationFailed(fieldName + "必须大于0");
        }
        if (value.scale() > 4) {
            throw BizException.validationFailed(fieldName + "最多支持4位小数");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private String normalizeAreaCoefficientSource(String value) {
        String result = StrUtil.emptyToNull(StrUtil.trim(value));
        if (result == null) {
            return null;
        }
        if (!Objects.equals(result, "system")
                && !Objects.equals(result, "manual")
                && !Objects.equals(result, "ai")) {
            throw BizException.validationFailed("面积系数来源值不合法");
        }
        return result;
    }

    private String normalizeAiSuggestionReason(String value) {
        return StrUtil.emptyToNull(StrUtil.trim(value));
    }

    private AreaSuggestion resolveAreaSuggestion(String dictName) {
        String normalizedName = StrUtil.trimToEmpty(dictName);
        String upperName = normalizedName.toUpperCase();
        for (BuiltinItem builtinItem : BUILTIN_ITEM_MAP.getOrDefault(DictCategoryType.MATERIAL_CATEGORY, Collections.emptyList())) {
            if ((StrUtil.equalsIgnoreCase(builtinItem.getCode(), upperName) || StrUtil.equals(builtinItem.getName(), normalizedName))
                    && builtinItem.getAreaCoefficient() != null) {
                return new AreaSuggestion(
                        builtinItem.getAreaCoefficient().setScale(4, RoundingMode.HALF_UP),
                        builtinItem.getAreaReason()
                );
            }
        }
        if (containsAnyIgnoreCase(normalizedName, "蔬菜", "叶菜", "根茎", "菌菇", "豆苗", "VEGETABLE")) {
            return new AreaSuggestion(decimal("0.2500"), "该类别通常采用周转筐平码暂存，占地较分散，建议统一面积系数按0.2500㎡/单件配置");
        }
        if (containsAnyIgnoreCase(normalizedName, "肉类", "鲜肉", "禽肉", "牛肉", "猪肉", "羊肉", "鸡肉", "鸭肉", "MEAT")) {
            return new AreaSuggestion(decimal("0.1800"), "该类别多采用冷鲜周转箱或保温箱短时存放，行业常见占用面积相对集中，建议按0.1800㎡/单件配置");
        }
        if (containsAnyIgnoreCase(normalizedName, "海鲜", "贝类", "虾类", "蟹类", "OCEAN_FOOD")) {
            return new AreaSuggestion(decimal("0.2400"), "该类别通常需要冰鲜保温与防渗摆放，单件周转占地略高于普通水产，建议按0.2400㎡/单件配置");
        }
        if (containsAnyIgnoreCase(normalizedName, "水产", "鱼类", "河鲜", "淡水", "SEAFOOD")) {
            return new AreaSuggestion(decimal("0.2200"), "该类别多采用带水或带冰周转筐存放，需要保留操作间距，建议按0.2200㎡/单件配置");
        }
        if (containsAnyIgnoreCase(normalizedName, "调料", "香辛料", "酱料", "干调", "佐料", "SEASONING")) {
            return new AreaSuggestion(decimal("0.0500"), "该类别通常为小规格瓶袋装堆放，占用面积较小，建议按0.0500㎡/单件配置");
        }
        if (containsAnyIgnoreCase(normalizedName, "粮油", "米面", "杂粮", "食用油", "GRAIN_OIL")) {
            return new AreaSuggestion(decimal("0.1200"), "该类别多采用整箱或整袋平码堆放，周转规则稳定，建议按0.1200㎡/单件配置");
        }
        if (containsAnyIgnoreCase(normalizedName, "乳制品", "奶制品", "牛奶", "酸奶", "DAIRY")) {
            return new AreaSuggestion(decimal("0.1600"), "该类别通常为箱装冷藏存放，需兼顾通风与冷链周转空间，建议按0.1600㎡/单件配置");
        }
        if (containsAnyIgnoreCase(normalizedName, "水果", "鲜果", "果品", "FRUIT")) {
            return new AreaSuggestion(decimal("0.2800"), "该类别多采用筐装或托盘分层摆放，为防压损通常预留更大周转空间，建议按0.2800㎡/单件配置");
        }
        if (containsAnyIgnoreCase(normalizedName, "蛋类", "禽蛋", "鸡蛋", "鸭蛋", "EGG")) {
            return new AreaSuggestion(decimal("0.1500"), "该类别通常为蛋托或箱装防震存放，占地适中且需保留搬运缓冲，建议按0.1500㎡/单件配置");
        }
        return new AreaSuggestion(decimal("0.2000"), "AI未命中明确行业大类时，按通用箱筐存放经验给出0.2000㎡/单件的参考值，建议结合实际堆码方式确认");
    }

    private boolean containsAnyIgnoreCase(String text, String... keywords) {
        if (StrUtil.isBlank(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (StrUtil.isNotBlank(keyword) && StrUtil.containsIgnoreCase(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMaterialCategory(DictCategoryType categoryType) {
        return Objects.equals(categoryType, DictCategoryType.MATERIAL_CATEGORY);
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(4, RoundingMode.HALF_UP);
    }

    private Long getCurrentTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L;
    }

    private Long getCurrentOrgId() {
        return UserContext.getOrgId() != null ? UserContext.getOrgId() : 0L;
    }

    private Long getTenantSharedOrgId() {
        return 0L;
    }

    private Map<String, Object> buildResult(Long id, String dictCode, String dictName, String categoryType) {
        return Map.of(
                "id", id,
                "dictCode", dictCode,
                "dictName", dictName,
                "categoryType", categoryType
        );
    }

    private static Map<DictCategoryType, List<BuiltinItem>> buildBuiltinItemMap() {
        Map<DictCategoryType, List<BuiltinItem>> map = new EnumMap<>(DictCategoryType.class);
        map.put(DictCategoryType.RECIPE_CATEGORY, Arrays.asList(
                new BuiltinItem("STAPLE", "主食", 10),
                new BuiltinItem("MAIN_DISH", "荤菜", 20),
                new BuiltinItem("SOUP", "汤品", 30),
                new BuiltinItem("SIDE_DISH", "素菜", 40),
                new BuiltinItem("DESSERT", "甜点", 50)
        ));
        map.put(DictCategoryType.WAREHOUSE_TYPE, Arrays.asList(
                new BuiltinItem("normal", "常温库", 10),
                new BuiltinItem("cold", "冷藏库", 20),
                new BuiltinItem("freeze", "冷冻库", 30),
                new BuiltinItem("dry", "干货库", 40)
        ));
        map.put(DictCategoryType.MATERIAL_CATEGORY, Arrays.asList(
                new BuiltinItem("VEGETABLE", "蔬菜", 10, decimal("0.2500"), "系统预设：蔬菜通常采用周转筐平码存放，占地较分散，统一按0.2500㎡/单件复用"),
                new BuiltinItem("MEAT", "肉类", 20, decimal("0.1800"), "系统预设：肉类多为冷鲜周转箱集中摆放，统一按0.1800㎡/单件复用"),
                new BuiltinItem("SEAFOOD", "水产", 30, decimal("0.2200"), "系统预设：水产通常带冰或带水暂存，需要预留操作间距，统一按0.2200㎡/单件复用"),
                new BuiltinItem("SEASONING", "调料", 40, decimal("0.0500"), "系统预设：调料多为小规格瓶袋装，占地较小，统一按0.0500㎡/单件复用"),
                new BuiltinItem("GRAIN_OIL", "粮油", 50, decimal("0.1200"), "系统预设：粮油常见为整袋或整箱平码堆放，统一按0.1200㎡/单件复用"),
                new BuiltinItem("DAIRY", "乳制品", 60, decimal("0.1600"), "系统预设：乳制品通常箱装冷藏并需留出冷链周转空间，统一按0.1600㎡/单件复用"),
                new BuiltinItem("FRUIT", "水果", 70, decimal("0.2800"), "系统预设：水果为防压损通常采用分层筐装，周转占地较大，统一按0.2800㎡/单件复用"),
                new BuiltinItem("OCEAN_FOOD", "海鲜", 80, decimal("0.2400"), "系统预设：海鲜通常需要冰鲜保温与防渗摆放，占地略高于普通水产，统一按0.2400㎡/单件复用"),
                new BuiltinItem("EGG", "蛋类", 90, decimal("0.1500"), "系统预设：蛋类多为蛋托或箱装防震存放，统一按0.1500㎡/单件复用")
        ));
        map.put(DictCategoryType.SUPPLIER_TYPE, Arrays.asList(
                new BuiltinItem("VEGETABLE", "蔬菜", 10),
                new BuiltinItem("MEAT", "肉类", 20),
                new BuiltinItem("SEAFOOD", "水产", 30),
                new BuiltinItem("SEASONING", "调料", 40),
                new BuiltinItem("GRAIN_OIL", "粮油", 50),
                new BuiltinItem("DAIRY", "乳制品", 60),
                new BuiltinItem("DRINK", "饮料", 70),
                new BuiltinItem("FROZEN_FOOD", "冷冻食品", 80)
        ));
        map.put(DictCategoryType.DEVICE_TYPE, Arrays.asList(
                new BuiltinItem("camera", "监控摄像头", 10),
                new BuiltinItem("sensor", "温湿度传感器", 20),
                new BuiltinItem("scale", "食材检测设备", 30),
                new BuiltinItem("gas_detector", "气体监测设备", 40),
                new BuiltinItem("sample_terminal", "智能留样设备", 50),
                new BuiltinItem("health_terminal", "智能晨检设备", 60),
                new BuiltinItem("temperature_humidity", "温湿度探头", 70)
        ));
        map.put(DictCategoryType.ORG_TYPE, Arrays.asList(
                new BuiltinItem("group", "集团", 10),
                new BuiltinItem("company", "分公司", 20),
                new BuiltinItem("canteen", "食堂", 30),
                new BuiltinItem("dept", "部门", 40)
        ));
        map.put(DictCategoryType.EMPLOYEE_POSITION, Arrays.asList(
                new BuiltinItem("chef", "厨师", 10),
                new BuiltinItem("cookworker", "厨工", 20),
                new BuiltinItem("manager", "店长", 30),
                new BuiltinItem("purchaser", "采购员", 40)
        ));
        return map;
    }

    @Getter
    @AllArgsConstructor
    private static class MaterialCategoryFieldState {
        private final BigDecimal areaCoefficient;
        private final String areaCoefficientSource;
        private final BigDecimal aiSuggestedAreaCoefficient;
        private final String aiSuggestionReason;
        private final LocalDateTime aiSuggestionGeneratedAt;

        private static MaterialCategoryFieldState empty() {
            return new MaterialCategoryFieldState(null, null, null, null, null);
        }
    }

    @Getter
    @AllArgsConstructor
    private static class AsyncOperatorContext {
        private final Long userId;
        private final String username;
        private final String realName;
        private final Long orgId;
        private final Long tenantId;
    }

    @Getter
    @AllArgsConstructor
    private static class AggregateStats {
        private final long affectedCount;
        private final BigDecimal quantityTotal;
    }

    @Getter
    @AllArgsConstructor
    private static class RecalcStepDefinition {
        private final String code;
        private final String name;
        private final String type;
        private final String sql;
    }

    @Getter
    @AllArgsConstructor
    private static class BuiltinItem {
        private final String code;
        private final String name;
        private final Integer sortOrder;
        private final BigDecimal areaCoefficient;
        private final String areaReason;

        private BuiltinItem(String code, String name, Integer sortOrder) {
            this(code, name, sortOrder, null, null);
        }
    }

    @Getter
    @AllArgsConstructor
    private static class AreaSuggestion {
        private final BigDecimal areaCoefficient;
        private final String reason;
    }
}
