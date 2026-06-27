package com.xykj.recipe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.result.PageResult;
import com.xykj.recipe.dto.CookTaskCompleteDTO;
import com.xykj.recipe.dto.CookTaskQueryDTO;
import com.xykj.recipe.dto.CookTaskStartDTO;
import com.xykj.recipe.entity.CookTask;
import com.xykj.recipe.entity.Recipe;
import com.xykj.recipe.entity.RecipeIngredient;
import com.xykj.recipe.entity.RecipePlan;
import com.xykj.recipe.entity.RecipePlanItem;
import com.xykj.recipe.mapper.CookTaskMapper;
import com.xykj.recipe.mapper.RecipeIngredientMapper;
import com.xykj.recipe.mapper.RecipeMapper;
import com.xykj.recipe.mapper.RecipePlanItemMapper;
import com.xykj.recipe.mapper.RecipePlanMapper;
import com.xykj.recipe.service.CookTaskService;
import com.xykj.recipe.vo.CookAIMonitorVO;
import com.xykj.recipe.vo.CookDashboardVO;
import com.xykj.recipe.vo.CookTaskDetailVO;
import com.xykj.recipe.vo.CookTaskVO;
import com.xykj.recipe.vo.CookTemperaturePointVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 烹饪任务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CookTaskServiceImpl implements CookTaskService {

    private static final DateTimeFormatter TASK_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CookTaskMapper cookTaskMapper;
    private final RecipePlanMapper planMapper;
    private final RecipePlanItemMapper planItemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RecipeMapper recipeMapper;
    private final RecipeIngredientMapper ingredientMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CookTask> generateTasksFromPlan(Long planId) {
        log.info("开始为计划[{}]生成烹饪任务", planId);

        RecipePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            log.warn("计划[{}]不存在", planId);
            return List.of();
        }

        List<RecipePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<RecipePlanItem>()
                        .eq(RecipePlanItem::getPlanId, planId)
                        .orderByAsc(RecipePlanItem::getSortOrder)
                        .orderByAsc(RecipePlanItem::getId)
        );
        if (items.isEmpty()) {
            log.info("计划[{}]没有菜谱明细，无需生成任务", planId);
            return List.of();
        }

        // 解析实施日期范围：周期计划(startDate~endDate)拆分为每天，单日计划(planDate)
        List<LocalDate> taskDates = resolveTaskDates(plan);
        log.info("计划[{}]实施日期范围: {}~{}，共{}天", planId,
                plan.getStartDate(), plan.getEndDate(), taskDates.size());

        List<CookTask> existingTasks = cookTaskMapper.selectByPlanId(planId);
        // 用 (menuId + taskDate) 组合做去重，避免重复创建
        Set<String> existingKeys = existingTasks.stream()
                .filter(task -> !"cancelled".equals(task.getStatus()))
                .map(task -> task.getMenuId() + "_" + (task.getTaskDate() != null ? task.getTaskDate().toString() : "null"))
                .collect(Collectors.toSet());

        List<CookTask> createdTasks = new ArrayList<>();
        for (LocalDate taskDate : taskDates) {
            for (RecipePlanItem item : items) {
                String compositeKey = item.getRecipeId() + "_" + taskDate.toString();
                if (existingKeys.contains(compositeKey)) {
                    continue;
                }

                CookTask task = new CookTask();
                task.setTaskNo(generateTaskNo());
                task.setPlanId(planId);
                task.setMenuId(item.getRecipeId());
                task.setMenuName(item.getRecipeName());
                task.setTaskDate(taskDate);
                task.setPlannedQty(item.getPlannedServings());
                task.setActualQty(0);
                task.setStatus("pending");
                task.setRemark(item.getRemark());
                task.setOrgId(plan.getOrgId());
                task.setTenantId(plan.getTenantId() != null ? plan.getTenantId() : 1L);
                task.setDeleted(0);

                cookTaskMapper.insert(task);
                createdTasks.add(task);
                log.info("生成烹饪任务[{}]，菜谱[{}]，日期[{}]，份数[{}]",
                        task.getTaskNo(), item.getRecipeName(), taskDate, item.getPlannedServings());
            }
        }

        log.info("计划[{}]新增{}个烹饪任务", planId, createdTasks.size());
        return createdTasks;
    }

    /**
     * 解析计划的实施日期列表
     * 周期计划: startDate~endDate 拆分为逐天列表
     * 单日计划: 返回 [planDate]
     */
    private List<LocalDate> resolveTaskDates(RecipePlan plan) {
        if (plan.getStartDate() != null && plan.getEndDate() != null) {
            List<LocalDate> dates = new ArrayList<>();
            LocalDate current = plan.getStartDate();
            while (!current.isAfter(plan.getEndDate())) {
                dates.add(current);
                current = current.plusDays(1);
            }
            return dates;
        }
        if (plan.getPlanDate() != null) {
            return List.of(plan.getPlanDate());
        }
        // fallback: 无法确定日期时返回空，不生成任务
        log.warn("计划[{}]缺少日期信息，无法生成任务", plan.getId());
        return List.of();
    }

    @Override
    public List<CookTask> getTasksByPlanId(Long planId) {
        return cookTaskMapper.selectByPlanId(planId);
    }

    @Override
    public CookDashboardVO getDashboard(CookTaskQueryDTO query) {
        List<CookTaskVO> tasks = filterTasks(query);
        long total = tasks.size();
        long pending = tasks.stream().filter(task -> "pending".equals(task.getStatus())).count();
        long inProgress = tasks.stream().filter(task -> "in_progress".equals(task.getStatus())).count();
        long completed = tasks.stream().filter(task -> "completed".equals(task.getStatus())).count();
        long abnormal = tasks.stream().filter(task -> Boolean.TRUE.equals(task.getTemperatureAbnormal())).count();
        BigDecimal completionRate = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        return CookDashboardVO.builder()
                .totalDishes(total)
                .pendingCount(pending)
                .inProgressCount(inProgress)
                .completedCount(completed)
                .abnormalTemperatureCount(abnormal)
                .completionRate(completionRate)
                .build();
    }

    @Override
    public PageResult<CookTaskVO> getTaskPage(CookTaskQueryDTO query) {
        List<CookTaskVO> tasks = filterTasks(query);
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1L : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20L : query.getPageSize();
        int fromIndex = (int) Math.min((pageNum - 1) * pageSize, tasks.size());
        int toIndex = (int) Math.min(fromIndex + pageSize, tasks.size());
        List<CookTaskVO> pageList = tasks.subList(fromIndex, toIndex);
        return PageResult.of(pageList, pageNum, pageSize, (long) tasks.size());
    }

    @Override
    public CookTaskDetailVO getTaskDetail(Long id) {
        CookTask task = getRequiredTask(id);
        RecipePlan plan = getRequiredPlan(task.getPlanId());
        Recipe recipe = recipeMapper.selectById(task.getMenuId());

        CookTaskDetailVO detail = new CookTaskDetailVO();
        detail.setId(task.getId());
        detail.setTaskNo(task.getTaskNo());
        detail.setPlanId(plan.getId());
        detail.setPlanCode(plan.getPlanCode());
        detail.setTaskDate(task.getTaskDate());
        detail.setPlanDate(plan.getPlanDate());
        detail.setMealType(plan.getMealType());
        detail.setRecipeId(task.getMenuId());
        detail.setMenuName(task.getMenuName());
        detail.setStatus(task.getStatus());
        detail.setPlannedQty(task.getPlannedQty());
        detail.setActualQty(task.getActualQty());
        detail.setChefName(task.getAssignedChefName());
        detail.setActualDuration(task.getCookingDuration());
        detail.setCurrentTemp(getCurrentTemperature(task));
        detail.setTemperatureAbnormal(isTemperatureAbnormal(task, recipe));
        detail.setAiViolationCount(defaultInt(task.getAiViolationCount()));
        detail.setQualityScore(task.getQualityScore());
        detail.setRemark(task.getRemark());
        detail.setStartTime(task.getStartTime());
        detail.setEndTime(task.getEndTime());
        detail.setTemperatureRecords(getTemperatureRecords(id));
        detail.setAiMonitorRecords(getAiMonitorRecords(id));

        if (recipe != null) {
            detail.setRecipeCode(recipe.getRecipeCode());
            detail.setRecipeDescription(recipe.getDescription());
            detail.setCookingSteps(recipe.getCookingSteps());
            detail.setStandardDuration(recipe.getTargetCookTime());
            detail.setTargetTempMin(recipe.getTargetTempMin());
            detail.setTargetTempMax(recipe.getTargetTempMax());
        }

        List<RecipeIngredient> ingredients = ingredientMapper.selectByRecipeId(task.getMenuId());
        detail.setIngredients(ingredients.stream().map(ingredient -> {
            CookTaskDetailVO.CookIngredientVO ingredientVO = new CookTaskDetailVO.CookIngredientVO();
            ingredientVO.setMaterialId(ingredient.getMaterialId());
            ingredientVO.setMaterialName(ingredient.getMaterialName());
            ingredientVO.setMaterialSpec(ingredient.getMaterialSpec());
            ingredientVO.setQuantity(ingredient.getQuantity());
            ingredientVO.setUnit(ingredient.getUnit());
            ingredientVO.setMain(Boolean.TRUE.equals(ingredient.getIsMain()));
            return ingredientVO;
        }).collect(Collectors.toList()));

        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CookTaskVO startTask(Long id, CookTaskStartDTO dto) {
        CookTask task = getRequiredTask(id);
        if (!"pending".equals(task.getStatus())) {
            throw new RuntimeException("当前任务状态不允许开始烹饪");
        }

        task.setStatus("in_progress");
        task.setStartTime(LocalDateTime.now());
        task.setAssignedChefId(dto.getChefId());
        task.setAssignedChefName(isBlank(dto.getChefName()) ? defaultChefName(task) : dto.getChefName());
        if (!isBlank(dto.getRemark())) {
            task.setRemark(dto.getRemark());
        }
        cookTaskMapper.updateById(task);
        syncPlanItemStatus(task.getPlanId(), task.getMenuId(), RecipePlanItem.STATUS_COOKING, null);
        return buildTaskVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CookTaskVO completeTask(Long id, CookTaskCompleteDTO dto) {
        CookTask task = getRequiredTask(id);
        // 允许pending或in_progress状态的任务完成
        if (!"pending".equals(task.getStatus()) && !"in_progress".equals(task.getStatus())) {
            throw new RuntimeException("当前任务状态不允许完成烹饪");
        }

        LocalDateTime endTime = LocalDateTime.now();
        // 如果任务还是pending状态,自动设置开始时间
        if ("pending".equals(task.getStatus())) {
            task.setStartTime(endTime);
            task.setAssignedChefName("系统");
        }
        task.setStatus("completed");
        task.setEndTime(endTime);
        task.setActualQty(dto.getActualQty() != null ? dto.getActualQty() : task.getPlannedQty());
        task.setCookingDuration(task.getStartTime() == null ? 0 : (int) Duration.between(task.getStartTime(), endTime).toMinutes());
        if (!isBlank(dto.getRemark())) {
            task.setRemark(dto.getRemark());
        }
        cookTaskMapper.updateById(task);
        syncPlanItemStatus(task.getPlanId(), task.getMenuId(), RecipePlanItem.STATUS_COMPLETED, task.getActualQty());
        return buildTaskVO(task);
    }

    @Override
    public List<CookTemperaturePointVO> getTemperatureRecords(Long id) {
        CookTask task = getRequiredTask(id);
        return parseTemperatureRecords(task.getTemperatureRecords());
    }

    @Override
    public List<CookAIMonitorVO> getAiMonitorRecords(Long id) {
        CookTask task = getRequiredTask(id);
        return parseAiMonitorRecords(task.getViolationDetails());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelPendingFutureTasksByPlanId(Long planId) {
        LocalDate today = LocalDate.now();
        log.info("取消计划[{}]在[{}]之后的未执行烹饪任务", planId, today);
        List<CookTask> tasks = cookTaskMapper.selectByPlanId(planId);
        int cancelledCount = 0;
        for (CookTask task : tasks) {
            LocalDate taskDate = task.getTaskDate();
            if (!"pending".equals(task.getStatus()) || taskDate == null || !taskDate.isAfter(today)) {
                continue;
            }
            task.setStatus("cancelled");
            cookTaskMapper.updateById(task);
            cancelledCount++;
            log.info("取消未来烹饪任务[{}]，日期[{}]", task.getTaskNo(), taskDate);

            try {
                int voided = jdbcTemplate.update(
                    "UPDATE sample_record SET status = 'voided', supplement_remark = CONCAT(COALESCE(supplement_remark, ''), ' [系统级联作废: 未来烹饪任务取消]') " +
                    "WHERE task_id = ? AND status IN ('pending_sample') AND deleted = 0",
                    task.getId());
                if (voided > 0) {
                    log.info("未来烹饪任务[{}]取消，级联作废{}条待留样记录", task.getTaskNo(), voided);
                }
            } catch (Exception e) {
                log.warn("级联作废未来烹饪任务[{}]的待留样记录失败: {}", task.getTaskNo(), e.getMessage());
            }
        }
        return cancelledCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTasksByPlanId(Long planId) {
        log.info("取消计划[{}]的所有烹饪任务", planId);
        List<CookTask> tasks = cookTaskMapper.selectByPlanId(planId);
        for (CookTask task : tasks) {
            if (!"completed".equals(task.getStatus()) && !"cancelled".equals(task.getStatus())) {
                task.setStatus("cancelled");
                cookTaskMapper.updateById(task);
                log.info("取消烹饪任务[{}]", task.getTaskNo());

                // 级联作废该任务下的留样记录
                try {
                    int voided = jdbcTemplate.update(
                        "UPDATE sample_record SET status = 'voided', remark = CONCAT(COALESCE(remark, ''), ' [系统级联作废: 烹饪任务取消]') " +
                        "WHERE task_id = ? AND status NOT IN ('voided', 'disposed', 'archived') AND deleted = 0",
                        task.getId());
                    if (voided > 0) {
                        log.info("烹饪任务[{}]取消，级联作废{}条留样记录", task.getTaskNo(), voided);
                    }
                } catch (Exception e) {
                    log.warn("级联作废烹饪任务[{}]的留样记录失败: {}", task.getTaskNo(), e.getMessage());
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustTasksForPlan(Long planId) {
        log.info("调整计划[{}]的烹饪任务", planId);

        List<RecipePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<RecipePlanItem>()
                        .eq(RecipePlanItem::getPlanId, planId)
        );
        Map<Long, RecipePlanItem> itemMap = items.stream()
                .collect(Collectors.toMap(RecipePlanItem::getRecipeId, item -> item, (left, right) -> right, LinkedHashMap::new));

        List<CookTask> existingTasks = cookTaskMapper.selectByPlanId(planId);
        for (CookTask task : existingTasks) {
            RecipePlanItem currentItem = itemMap.get(task.getMenuId());
            if (currentItem == null) {
                if (!"completed".equals(task.getStatus()) && !"cancelled".equals(task.getStatus())) {
                    task.setStatus("cancelled");
                    cookTaskMapper.updateById(task);
                }
                continue;
            }

            if ("pending".equals(task.getStatus())) {
                task.setMenuName(currentItem.getRecipeName());
                task.setPlannedQty(currentItem.getPlannedServings());
                task.setRemark(currentItem.getRemark());
                cookTaskMapper.updateById(task);
            }
        }

        generateTasksFromPlan(planId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelPendingFutureTasksByPlanIdAndRecipeIds(Long planId, Set<Long> recipeIds, LocalDate effectiveDate) {
        if (planId == null || recipeIds == null || recipeIds.isEmpty()) {
            return 0;
        }
        LocalDate startDate = effectiveDate == null ? LocalDate.now().plusDays(1) : effectiveDate;
        List<CookTask> tasks = cookTaskMapper.selectByPlanId(planId);
        int cancelledCount = 0;
        for (CookTask task : tasks) {
            if (!recipeIds.contains(task.getMenuId())) {
                continue;
            }
            LocalDate taskDate = task.getTaskDate();
            if (!"pending".equals(task.getStatus()) || taskDate == null || taskDate.isBefore(startDate)) {
                continue;
            }
            task.setStatus("cancelled");
            cookTaskMapper.updateById(task);
            cancelledCount++;
            try {
                int voided = jdbcTemplate.update(
                        "UPDATE sample_record SET status = 'voided', supplement_remark = CONCAT(COALESCE(supplement_remark, ''), ' [系统级联作废: 未来烹饪任务取消]') " +
                                "WHERE task_id = ? AND status IN ('pending_sample') AND deleted = 0",
                        task.getId());
                if (voided > 0) {
                    log.info("未来烹饪任务[{}]取消，级联作废{}条待留样记录", task.getTaskNo(), voided);
                }
            } catch (Exception e) {
                log.warn("级联作废未来烹饪任务[{}]的待留样记录失败: {}", task.getTaskNo(), e.getMessage());
            }
        }
        return cancelledCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncPendingFutureTasksForPlanItems(Long planId, Map<Long, RecipePlanItem> targetItems, LocalDate effectiveDate) {
        if (planId == null || targetItems == null || targetItems.isEmpty()) {
            return;
        }
        LocalDate startDate = effectiveDate == null ? LocalDate.now().plusDays(1) : effectiveDate;
        List<CookTask> tasks = cookTaskMapper.selectByPlanId(planId);
        for (CookTask task : tasks) {
            LocalDate taskDate = task.getTaskDate();
            if (!"pending".equals(task.getStatus()) || taskDate == null || taskDate.isBefore(startDate)) {
                continue;
            }
            RecipePlanItem targetItem = targetItems.get(task.getMenuId());
            if (targetItem == null) {
                continue;
            }
            task.setMenuName(targetItem.getRecipeName());
            task.setPlannedQty(targetItem.getPlannedServings());
            task.setRemark(targetItem.getRemark());
            cookTaskMapper.updateById(task);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CookTask> generateFutureTasksForPlan(Long planId, LocalDate effectiveDate, Set<Long> recipeIds) {
        if (planId == null || recipeIds == null || recipeIds.isEmpty()) {
            return List.of();
        }
        LocalDate startDate = effectiveDate == null ? LocalDate.now().plusDays(1) : effectiveDate;
        RecipePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            return List.of();
        }
        List<LocalDate> taskDates = resolveTaskDates(plan).stream()
                .filter(date -> !date.isBefore(startDate))
                .toList();
        if (taskDates.isEmpty()) {
            return List.of();
        }
        List<RecipePlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<RecipePlanItem>()
                        .eq(RecipePlanItem::getPlanId, planId)
                        .in(RecipePlanItem::getRecipeId, recipeIds)
                        .orderByAsc(RecipePlanItem::getSortOrder)
                        .orderByAsc(RecipePlanItem::getId)
        );
        if (items.isEmpty()) {
            return List.of();
        }
        List<CookTask> existingTasks = cookTaskMapper.selectByPlanId(planId);
        Set<String> existingKeys = existingTasks.stream()
                .filter(task -> !"cancelled".equals(task.getStatus()))
                .map(task -> task.getMenuId() + "_" + (task.getTaskDate() != null ? task.getTaskDate().toString() : "null"))
                .collect(Collectors.toSet());
        List<CookTask> createdTasks = new ArrayList<>();
        for (LocalDate taskDate : taskDates) {
            for (RecipePlanItem item : items) {
                String compositeKey = item.getRecipeId() + "_" + taskDate;
                if (existingKeys.contains(compositeKey)) {
                    continue;
                }
                CookTask task = new CookTask();
                task.setTaskNo(generateTaskNo());
                task.setPlanId(planId);
                task.setMenuId(item.getRecipeId());
                task.setMenuName(item.getRecipeName());
                task.setTaskDate(taskDate);
                task.setPlannedQty(item.getPlannedServings());
                task.setActualQty(0);
                task.setStatus("pending");
                task.setRemark(item.getRemark());
                task.setOrgId(plan.getOrgId());
                task.setTenantId(plan.getTenantId() != null ? plan.getTenantId() : 1L);
                task.setDeleted(0);
                cookTaskMapper.insert(task);
                createdTasks.add(task);
                existingKeys.add(compositeKey);
            }
        }
        return createdTasks;
    }

    private List<CookTaskVO> filterTasks(CookTaskQueryDTO query) {
        LambdaQueryWrapper<CookTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getOrgId() != null, CookTask::getOrgId, query.getOrgId())
                .eq(!isBlank(query.getStatus()), CookTask::getStatus, query.getStatus())
                .like(!isBlank(query.getChefName()), CookTask::getAssignedChefName, query.getChefName())
                .ne(CookTask::getStatus, "cancelled")
                .orderByDesc(CookTask::getCreatedAt)
                .orderByDesc(CookTask::getId);

        List<CookTask> tasks = cookTaskMapper.selectList(wrapper);
        return tasks.stream()
                .map(this::buildTaskVO)
                .filter(task -> query.getPlanDate() == null || Objects.equals(task.getPlanDate(), query.getPlanDate()))
                .filter(task -> isBlank(query.getMealType()) || Objects.equals(task.getMealType(), query.getMealType()))
                .sorted(Comparator.comparing(CookTaskVO::getId).reversed())
                .collect(Collectors.toList());
    }

    private CookTaskVO buildTaskVO(CookTask task) {
        RecipePlan plan = getRequiredPlan(task.getPlanId());
        Recipe recipe = recipeMapper.selectById(task.getMenuId());
        List<RecipeIngredient> ingredients = ingredientMapper.selectByRecipeId(task.getMenuId());

        CookTaskVO vo = new CookTaskVO();
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setPlanId(task.getPlanId());
        vo.setTaskDate(task.getTaskDate());
        vo.setPlanDate(plan.getPlanDate());
        vo.setMealType(plan.getMealType());
        vo.setMenuName(task.getMenuName());
        vo.setStatus(task.getStatus());
        vo.setPlannedQty(task.getPlannedQty());
        vo.setActualQty(task.getActualQty());
        vo.setChefName(task.getAssignedChefName());
        vo.setActualDuration(task.getCookingDuration());
        vo.setCurrentTemp(getCurrentTemperature(task));
        vo.setTemperatureAbnormal(isTemperatureAbnormal(task, recipe));
        vo.setAiViolationCount(defaultInt(task.getAiViolationCount()));
        vo.setQualityScore(task.getQualityScore());
        vo.setStartTime(task.getStartTime());
        vo.setEndTime(task.getEndTime());
        vo.setRemark(task.getRemark());
        vo.setIngredients(ingredients.stream().map(RecipeIngredient::getMaterialName).filter(Objects::nonNull).limit(5).collect(Collectors.toList()));

        if (recipe != null) {
            vo.setStandardDuration(recipe.getTargetCookTime());
            vo.setTargetTemp(resolveTargetTemp(recipe));
        }
        return vo;
    }

    private RecipePlan getRequiredPlan(Long planId) {
        RecipePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("菜谱计划不存在");
        }
        return plan;
    }

    private CookTask getRequiredTask(Long id) {
        CookTask task = cookTaskMapper.selectById(id);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new RuntimeException("烹饪任务不存在");
        }
        return task;
    }

    private Integer resolveTargetTemp(Recipe recipe) {
        if (recipe == null) {
            return null;
        }
        if (recipe.getTargetTempMax() != null) {
            return recipe.getTargetTempMax();
        }
        return recipe.getTargetTempMin();
    }

    private Integer getCurrentTemperature(CookTask task) {
        List<CookTemperaturePointVO> records = parseTemperatureRecords(task.getTemperatureRecords());
        if (records.isEmpty()) {
            return null;
        }
        return records.get(records.size() - 1).getTemperature();
    }

    private boolean isTemperatureAbnormal(CookTask task, Recipe recipe) {
        List<CookTemperaturePointVO> records = parseTemperatureRecords(task.getTemperatureRecords());
        if (records.stream().anyMatch(point -> Boolean.TRUE.equals(point.getAbnormal()))) {
            return true;
        }
        if (recipe == null || records.isEmpty()) {
            return false;
        }
        Integer currentTemperature = records.get(records.size() - 1).getTemperature();
        if (currentTemperature == null) {
            return false;
        }
        if (recipe.getTargetTempMin() != null && currentTemperature < recipe.getTargetTempMin()) {
            return true;
        }
        return recipe.getTargetTempMax() != null && currentTemperature > recipe.getTargetTempMax();
    }

    private List<CookTemperaturePointVO> parseTemperatureRecords(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rawList = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<CookTemperaturePointVO> records = new ArrayList<>();
            for (Map<String, Object> item : rawList) {
                CookTemperaturePointVO point = new CookTemperaturePointVO();
                point.setRecordTime(parseDateTime(item.get("recordTime"), item.get("time"), item.get("timestamp")));
                point.setTemperature(parseInteger(item.get("temperature"), item.get("temp"), item.get("value")));
                point.setAbnormal(parseBoolean(item.get("abnormal"), item.get("isAbnormal")));
                point.setRemark(parseString(item.get("remark"), item.get("note")));
                records.add(point);
            }
            records.sort(Comparator.comparing(CookTemperaturePointVO::getRecordTime, Comparator.nullsLast(LocalDateTime::compareTo)));
            return records;
        } catch (Exception e) {
            log.warn("解析温度记录失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<CookAIMonitorVO> parseAiMonitorRecords(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rawList = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<CookAIMonitorVO> records = new ArrayList<>();
            for (Map<String, Object> item : rawList) {
                CookAIMonitorVO vo = new CookAIMonitorVO();
                vo.setViolationType(parseString(item.get("violationType"), item.get("type"), item.get("code")));
                vo.setViolationName(parseString(item.get("violationName"), item.get("name"), item.get("title")));
                vo.setLevel(parseString(item.get("level"), item.get("severity")));
                vo.setDescription(parseString(item.get("description"), item.get("detail"), item.get("message")));
                vo.setSuggestion(parseString(item.get("suggestion"), item.get("advice"), item.get("action")));
                vo.setSnapshotTime(parseString(item.get("snapshotTime"), item.get("time"), item.get("timestamp")));
                records.add(vo);
            }
            return records;
        } catch (Exception e) {
            log.warn("解析AI监控记录失败: {}", e.getMessage());
            return List.of();
        }
    }

    private void syncPlanItemStatus(Long planId, Long recipeId, String status, Integer cookedServings) {
        RecipePlanItem item = planItemMapper.selectOne(
                new LambdaQueryWrapper<RecipePlanItem>()
                        .eq(RecipePlanItem::getPlanId, planId)
                        .eq(RecipePlanItem::getRecipeId, recipeId)
                        .last("LIMIT 1")
        );
        if (item == null) {
            return;
        }
        item.setStatus(status);
        if (cookedServings != null) {
            item.setCookedServings(cookedServings);
        }
        planItemMapper.updateById(item);
    }

    private String defaultChefName(CookTask task) {
        return isBlank(task.getAssignedChefName()) ? "待指派" : task.getAssignedChefName();
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String parseString(Object... values) {
        for (Object value : values) {
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private Integer parseInteger(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Boolean parseBoolean(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            String text = String.valueOf(value);
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                return Boolean.parseBoolean(text);
            }
        }
        return false;
    }

    private LocalDateTime parseDateTime(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof LocalDateTime time) {
                return time;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                return LocalDateTime.parse(text);
            } catch (Exception ignored) {
            }
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 生成任务编号
     */
    private String generateTaskNo() {
        String timestamp = LocalDateTime.now().format(TASK_NO_FORMATTER);
        String random = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "CT" + timestamp + random;
    }
}
