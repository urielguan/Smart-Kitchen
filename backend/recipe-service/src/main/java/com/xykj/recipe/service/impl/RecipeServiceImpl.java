package com.xykj.recipe.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xykj.common.ai.AiModuleCode;
import com.xykj.common.ai.AiServiceType;
import com.xykj.common.ai.entity.AiServiceConfig;
import com.xykj.common.ai.model.AiTextGenerateResult;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.ai.service.AiServiceConfigService;
import com.xykj.common.ai.service.OpenAiCompatibleService;
import com.xykj.common.context.UserContext;
import com.xykj.common.exception.BizException;
import com.xykj.common.enums.ResultCode;
import com.xykj.recipe.dto.*;
import com.xykj.recipe.entity.*;
import com.xykj.recipe.mapper.*;
import com.xykj.recipe.service.RecipeNutritionSupportService;
import com.xykj.recipe.service.RecipeService;
import com.xykj.recipe.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜谱服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeServiceImpl extends ServiceImpl<RecipeMapper, Recipe> implements RecipeService {

    private static final Set<String> BUILTIN_CATEGORY_CODES = Set.of("STAPLE", "MAIN_DISH", "SOUP", "SIDE_DISH", "DESSERT");

    private final RecipeIngredientMapper ingredientMapper;
    private final RecipeCategoryMapper categoryMapper;
    private final RecipePlanItemMapper planItemMapper;
    private final RecipePlanMapper planMapper;
    private final RecipeNutritionResultMapper recipeNutritionResultMapper;
    private final RecipeNutritionSupportService recipeNutritionSupportService;
    private final JdbcTemplate jdbcTemplate;
    private final AiServiceConfigService aiServiceConfigService;
    private final OpenAiCompatibleService openAiCompatibleService;

    @Override
    @DataScope
    public Page<RecipeVO> list(RecipeQueryDTO query) {
        Page<Recipe> pageDto = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getRecipeName()), Recipe::getRecipeName, query.getRecipeName());
        wrapper.eq(StrUtil.isNotBlank(query.getRecipeCode()), Recipe::getRecipeCode, query.getRecipeCode());
        wrapper.eq(query.getCategoryId() != null, Recipe::getCategoryId, query.getCategoryId());
        wrapper.eq(StrUtil.isNotBlank(query.getStatus()), Recipe::getStatus, query.getStatus());
        wrapper.eq(query.getOrgId() != null, Recipe::getOrgId, query.getOrgId());
        wrapper.in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Recipe::getOrgId, query.getOrgIds());
        wrapper.isNull(query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty(), Recipe::getId);
        wrapper.orderByDesc(Recipe::getUpdatedAt);

        Page<Recipe> page = page(pageDto, wrapper);

        List<RecipeVO> voList = page.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());

        Page<RecipeVO> resultPage = new Page<>(query.getPageNum(), query.getPageSize());
        resultPage.setRecords(voList);
        resultPage.setTotal(page.getTotal());
        return resultPage;
    }

    @Override
    public RecipeDetailVO getDetail(Long id) {
        Recipe recipe = getById(id);
        if (recipe == null) {
                throw new RuntimeException("菜谱不存在");
            }
        recipeNutritionSupportService.recalculateRecipeNutrition(recipe);
        recipe = getById(id);

        RecipeDetailVO vo = new RecipeDetailVO();
        BeanUtils.copyProperties(recipe, vo);

        // 查询类别名称
        if (recipe.getCategoryId() != null) {
            RecipeCategory category = getVisibleCategoryById(recipe.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getCategoryName());
            }
        }

        // 查询食材列表
        List<RecipeIngredient> ingredients = ingredientMapper.selectList(
                new LambdaQueryWrapper<RecipeIngredient>()
                        .eq(RecipeIngredient::getRecipeId, id)
                        .orderByAsc(RecipeIngredient::getSortOrder)
        );
        List<RecipeIngredientVO> ingredientVOList = ingredients.stream().map(ing -> {
            RecipeIngredientVO ingVO = new RecipeIngredientVO();
            BeanUtils.copyProperties(ing, ingVO);
            enrichIngredientNutritionTrace(ing, ingVO);
            return ingVO;
        }).collect(Collectors.toList());
        vo.setIngredients(ingredientVOList);

        // 构建营养信息
        RecipeDetailVO.NutritionInfoVO nutritionInfo = new RecipeDetailVO.NutritionInfoVO();
        nutritionInfo.setCalories(recipe.getCalories());
        nutritionInfo.setProtein(recipe.getProtein());
        nutritionInfo.setCarbohydrate(recipe.getCarbohydrate());
        nutritionInfo.setFat(recipe.getFat());
        nutritionInfo.setSodium(recipe.getSodium());
        nutritionInfo.setFiber(recipe.getFiber());
        vo.setNutritionInfo(nutritionInfo);

        // 构建维生素信息
        RecipeDetailVO.VitaminInfoVO vitaminInfo = new RecipeDetailVO.VitaminInfoVO();
        vitaminInfo.setVitaminA(sumIngredientValue(ingredients, "vitaminA"));
        vitaminInfo.setVitaminB1(sumIngredientValue(ingredients, "vitaminB1"));
        vitaminInfo.setVitaminB2(sumIngredientValue(ingredients, "vitaminB2"));
        vitaminInfo.setVitaminC(sumIngredientValue(ingredients, "vitaminC"));
        vitaminInfo.setVitaminD(recipe.getVitaminD());
        vitaminInfo.setVitaminE(recipe.getVitaminE());
        vo.setVitaminInfo(vitaminInfo);

        // 构建矿物质信息
        RecipeDetailVO.MineralInfoVO mineralInfo = new RecipeDetailVO.MineralInfoVO();
        mineralInfo.setCalcium(sumIngredientValue(ingredients, "calcium"));
        mineralInfo.setIron(sumIngredientValue(ingredients, "iron"));
        mineralInfo.setZinc(sumIngredientValue(ingredients, "zinc"));
        vo.setMineralInfo(mineralInfo);

        return vo;
    }

    private void enrichIngredientNutritionTrace(RecipeIngredient ingredient, RecipeIngredientVO vo) {
        vo.setUnit(normalizeRecipeIngredientUnit(ingredient.getUnit()));
        if (ingredient.getMaterialId() == null) {
            vo.setQuantityInGram(ingredient.getQuantity());
            return;
        }

        vo.setQuantityInGram(ingredient.getQuantity());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT m.food_item_id,
                       m.nutrition_source_type,
                       f.food_name,
                       m.calories,
                       m.protein,
                       m.carbohydrate,
                       m.fat,
                       m.sodium,
                       m.fiber
                  FROM wms_material m
                  LEFT JOIN food_item f ON f.id = m.food_item_id AND f.deleted = 0
                 WHERE m.id = ? AND m.deleted = 0
                """, ingredient.getMaterialId());
        if (rows.isEmpty()) {
            return;
        }

        Map<String, Object> row = rows.getFirst();
        vo.setFoodItemId(toLong(row.get("food_item_id")));
        vo.setFoodItemName((String) row.get("food_name"));
        vo.setNutritionSourceType((String) row.get("nutrition_source_type"));
        vo.setCaloriesPer100g(toDecimal(row.get("calories")));
        vo.setProteinPer100g(toDecimal(row.get("protein")));
        vo.setCarbohydratePer100g(toDecimal(row.get("carbohydrate")));
        vo.setFatPer100g(toDecimal(row.get("fat")));
        vo.setSodiumPer100g(toDecimal(row.get("sodium")));
        vo.setFiberPer100g(toDecimal(row.get("fiber")));
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String normalizeRecipeIngredientUnit(String unit) {
        if (unit == null) {
            return "g";
        }
        String normalized = unit.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return "g";
        }
        return switch (normalized) {
            case "kg", "g", "克" -> "g";
            default -> unit.trim();
        };
    }

    @Override
    public Long create(RecipeCreateDTO dto) {
        // 生成菜谱编码
        String menuCode = dto.getMenuCode();
        if (menuCode == null || menuCode.isEmpty()) {
            menuCode = "CP" + System.currentTimeMillis() % 1000000;
        }

        // 检查编码是否重复
        Long existingCount = lambdaQuery()
                .eq(Recipe::getRecipeCode, menuCode)
                .ne(Recipe::getDeleted, 1)
                .count();
        if (existingCount != null && existingCount > 0) {
            throw new RuntimeException("菜谱编码「" + menuCode + "」已存在，请更换编码");
        }

        Recipe recipe = new Recipe();
        recipe.setRecipeCode(menuCode);
        recipe.setRecipeName(dto.getMenuName());

        // 根据类别编码查找类别ID
        if (dto.getMenuCategory() != null) {
            RecipeCategory category = findVisibleCategoryByCode(dto.getMenuCategory());
            if (category != null) {
                recipe.setCategoryId(category.getId());
            }
        }

        recipe.setDescription(dto.getDescription());
        recipe.setImageUrl(dto.getImageUrl());
        recipe.setServingSize(dto.getServingSize());
        recipe.setTargetCookTime(dto.getCookingTime());
        recipe.setTargetTempMin(dto.getCookingTempMin());
        recipe.setTargetTempMax(dto.getCookingTempMax());
        recipe.setCookingSteps(dto.getCookingSteps());
        recipe.setStatus(dto.getStatus() != null ? dto.getStatus() : "active");
        recipe.setCreatedAt(LocalDateTime.now());
        recipe.setUpdatedAt(LocalDateTime.now());

        // 保存菜谱
        save(recipe);

        // 保存食材列表
        if (dto.getIngredients() != null && !dto.getIngredients().isEmpty()) {
                List<RecipeIngredient> ingredients = new ArrayList<>();
                int sortOrder = 1;
                for (RecipeIngredientDTO ingDTO : dto.getIngredients()) {
                    RecipeIngredient ingredient = new RecipeIngredient();
                    BeanUtils.copyProperties(ingDTO, ingredient);
                    ingredient.setRecipeId(recipe.getId());
                    ingredient.setUnit(normalizeRecipeIngredientUnit(ingDTO.getUnit()));
                    ingredient.setSortOrder(sortOrder++);
                    ingredient.setCreatedAt(LocalDateTime.now());
                    recipeNutritionSupportService.applyMaterialNutritionSnapshot(ingredient);
                    ingredients.add(ingredient);
                }
                for (RecipeIngredient ingredient : ingredients) {
                    ingredientMapper.insert(ingredient);
                }
            }

        // 计算营养成分（基于食材汇总)
            calculateNutrition(recipe);

        return recipe.getId();
    }

    @Override
    public void update(Long id, RecipeUpdateDTO dto) {
        Recipe recipe = getById(id);
        if (recipe == null) {
            throw new RuntimeException("菜谱不存在");
        }

        // 手动映射字段
        if (dto.getMenuName() != null) {
            recipe.setRecipeName(dto.getMenuName());
        }
        if (dto.getMenuCategory() != null) {
            // 根据类别编码查找类别ID
            RecipeCategory category = findVisibleCategoryByCode(dto.getMenuCategory());
            if (category != null) {
                recipe.setCategoryId(category.getId());
            }
        }
        if (dto.getDescription() != null) {
            recipe.setDescription(dto.getDescription());
        }
        if (dto.getImageUrl() != null) {
            recipe.setImageUrl(dto.getImageUrl());
        }
        if (dto.getServingSize() != null) {
            recipe.setServingSize(dto.getServingSize());
        }
        if (dto.getCookingTime() != null) {
            recipe.setTargetCookTime(dto.getCookingTime());
        }
        if (dto.getCookingTempMin() != null) {
            recipe.setTargetTempMin(dto.getCookingTempMin());
        }
        if (dto.getCookingTempMax() != null) {
            recipe.setTargetTempMax(dto.getCookingTempMax());
        }
        if (dto.getCookingSteps() != null) {
            recipe.setCookingSteps(dto.getCookingSteps());
        }
        if (dto.getStatus() != null) {
            recipe.setStatus(dto.getStatus());
        }

        recipe.setUpdatedAt(LocalDateTime.now());
        updateById(recipe);

        // 更新食材列表
        if (dto.getIngredients() != null) {
                // 删除原有食材
                ingredientMapper.delete(
                        new LambdaQueryWrapper<RecipeIngredient>()
                                .eq(RecipeIngredient::getRecipeId, id)
                );

                // 插入新食材
                List<RecipeIngredient> ingredients = new ArrayList<>();
                int sortOrder = 1;
                for (RecipeIngredientDTO ingDTO : dto.getIngredients()) {
                    RecipeIngredient ingredient = new RecipeIngredient();
                    BeanUtils.copyProperties(ingDTO, ingredient);
                    ingredient.setRecipeId(id);
                    ingredient.setUnit(normalizeRecipeIngredientUnit(ingDTO.getUnit()));
                    ingredient.setSortOrder(sortOrder++);
                    ingredient.setCreatedAt(LocalDateTime.now());
                    recipeNutritionSupportService.applyMaterialNutritionSnapshot(ingredient);
                    ingredients.add(ingredient);
                }
                for (RecipeIngredient ingredient : ingredients) {
                    ingredientMapper.insert(ingredient);
                }
            }

            // 重新计算营养成分
            calculateNutrition(recipe);
    }

    @Override
    public void delete(Long id) {
        // 1. 检查菜谱是否存在
        Recipe recipe = getById(id);
        if (recipe == null) {
            throw BizException.notFound("菜谱不存在");
        }

        // 2. 检查关联计划：只要有关联计划（无论状态）就不允许删除
        Long itemCount = planItemMapper.selectCount(
                new LambdaQueryWrapper<RecipePlanItem>()
                        .eq(RecipePlanItem::getRecipeId, id)
        );
        if (itemCount != null && itemCount > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该菜谱已被菜谱计划引用，无法删除");
        }

        // 3. 删除菜谱食材
        ingredientMapper.delete(
                new LambdaQueryWrapper<RecipeIngredient>()
                        .eq(RecipeIngredient::getRecipeId, id)
        );

        // 4. 删除菜谱（计划明细保留，通过冗余字段仍可查看菜谱信息）
        removeById(id);
        log.info("菜谱删除成功: id={}, name={}", id, recipe.getRecipeName());
    }

    @Override
    public void toggleStatus(Long id) {
        Recipe recipe = getById(id);
        if (recipe == null) {
            throw new RuntimeException("菜谱不存在");
                }

        recipe.setStatus("active".equals(recipe.getStatus()) ? "inactive" : "active");
        recipe.setUpdatedAt(LocalDateTime.now());
        updateById(recipe);
    }

    @Override
    @DataScope
    public RecipeStatisticsVO getStatistics(RecipeQueryDTO query) {
        RecipeStatisticsVO vo = new RecipeStatisticsVO();

        // 1. 菜谱基础统计
        Integer totalRecipes = baseMapper.selectCount(
                applyRecipeScope(new LambdaQueryWrapper<Recipe>().eq(Recipe::getDeleted, 0), query)
        ).intValue();
        Integer activeRecipes = baseMapper.selectCount(
                applyRecipeScope(new LambdaQueryWrapper<Recipe>().eq(Recipe::getDeleted, 0).eq(Recipe::getStatus, "active"), query)
        ).intValue();

        vo.setTotalRecipes(totalRecipes);
        vo.setActiveRecipes(activeRecipes);
        vo.setInactiveRecipes(totalRecipes - activeRecipes);

        // 2. 时间维度统计（本周/本月新增）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.minusDays(7);
        LocalDateTime monthStart = now.minusDays(30);

        Integer weeklyNew = baseMapper.selectCount(
                applyRecipeScope(new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getDeleted, 0)
                        .ge(Recipe::getCreatedAt, weekStart), query)
        ).intValue();
        Integer monthlyNew = baseMapper.selectCount(
                applyRecipeScope(new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getDeleted, 0)
                        .ge(Recipe::getCreatedAt, monthStart), query)
        ).intValue();
        vo.setWeeklyNewRecipes(weeklyNew);
        vo.setMonthlyNewRecipes(monthlyNew);

        // 3. 营养达标率（营养评分>=70的菜谱占比）
        Integer passCount = baseMapper.selectCount(
                applyRecipeScope(new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getDeleted, 0)
                        .ge(Recipe::getNutritionScore, 70), query)
        ).intValue();
        vo.setNutritionPassRate(totalRecipes > 0 ?
                BigDecimal.valueOf(passCount * 100.0 / totalRecipes).setScale(1, RoundingMode.HALF_UP) :
                BigDecimal.ZERO);

        // 4. 平均营养评分
        Double avgScore = baseMapper.selectList(
                applyRecipeScope(new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getDeleted, 0)
                        .isNotNull(Recipe::getNutritionScore), query)
        ).stream()
                .mapToInt(r -> r.getNutritionScore() != null ? r.getNutritionScore() : 0)
                .average()
                .orElse(0.0);
        vo.setAvgNutritionScore(BigDecimal.valueOf(avgScore).setScale(1, RoundingMode.HALF_UP));

        // 5. 食材覆盖率（有食材数据的菜谱占比）
        long recipesWithIngredients = baseMapper.selectList(
                applyRecipeScope(new LambdaQueryWrapper<Recipe>().eq(Recipe::getDeleted, 0), query)
        ).stream()
                .filter(r -> ingredientMapper.selectCount(
                        new LambdaQueryWrapper<RecipeIngredient>()
                                .eq(RecipeIngredient::getRecipeId, r.getId())
                ) > 0)
                .count();
        vo.setIngredientCoverage(totalRecipes > 0 ?
                BigDecimal.valueOf(recipesWithIngredients * 100.0 / totalRecipes).setScale(1, RoundingMode.HALF_UP) :
                BigDecimal.ZERO);

        // 6. 营养素分布统计
        RecipeStatisticsVO.NutritionDistribution distribution = calculateNutritionDistribution(query);
        vo.setNutritionDistribution(distribution);

        // 7. 类别分布统计
        List<RecipeStatisticsVO.CategoryStats> categoryStats = calculateCategoryDistribution(totalRecipes, query);
        vo.setCategoryDistribution(categoryStats);

        // 8. 热门菜谱（按更新时间排序，模拟）
        List<RecipeStatisticsVO.HotRecipe> hotRecipes = getHotRecipes(5, query);
        vo.setWeeklyHotRecipes(hotRecipes);
        vo.setMonthlyHotRecipes(hotRecipes);

        // 9. 评分分布（模拟数据）
        RecipeStatisticsVO.RatingDistribution ratingDist = new RecipeStatisticsVO.RatingDistribution();
        ratingDist.setFiveStar((int) (totalRecipes * 0.4));
        ratingDist.setFourStar((int) (totalRecipes * 0.3));
        ratingDist.setThreeStar((int) (totalRecipes * 0.2));
        ratingDist.setTwoStar((int) (totalRecipes * 0.07));
        ratingDist.setOneStar((int) (totalRecipes * 0.03));
        ratingDist.setAvgRating(BigDecimal.valueOf(4.2));
        vo.setRatingDistribution(ratingDist);

        return vo;
    }

    @Override
    public DashboardOverviewVO getDashboardOverview(DashboardQueryDTO query) {
        String range = query != null && StrUtil.isNotBlank(query.getTimeRange()) ? query.getTimeRange() : "today";
        if (!"today".equals(range) && !"week".equals(range) && !"month".equals(range)) {
            range = "today";
        }

        RecipeStatisticsVO stats = getStatistics(new RecipeQueryDTO());

        DashboardOverviewVO overview = new DashboardOverviewVO();
        overview.setGeneratedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        List<DashboardOverviewVO.DashboardSectionVO> sections = new ArrayList<>();
        sections.add(buildSection("recipe", "菜谱与营养", List.of(
                metric("排餐计划完成情况", percent(stats.getNutritionPassRate()), "%", "+1.2%", "up", 92),
                metric("菜品营养达标率", percent(stats.getNutritionPassRate()), "%", "+0.8%", "up", 90),
                metric("特殊餐比例", new BigDecimal("14.7"), "%", "+0.3%", "up", 74),
                metric("就餐人数与偏好统计", BigDecimal.valueOf(Math.max(300, stats.getTotalRecipes() * 12L)), "人", "热度上升", "up", 84)
        )));
        sections.add(buildSection("compliance", "综合数据与合规监管", List.of(
                metric("核心 KPI 总览", percent(stats.getAvgNutritionScore()), "分", "+1.0 分", "up", score(stats.getAvgNutritionScore())),
                metric("审计日志统计", BigDecimal.valueOf(Math.max(50, stats.getTotalRecipes() * 2L)), "次", "异常 0 次", "up", 98),
                metric("合规监管指标", percent(stats.getIngredientCoverage()), "%", "+0.6%", "up", score(stats.getIngredientCoverage())),
                metric("多食堂集团化对比", BigDecimal.valueOf(5), "食堂", "2 个待提升", "stable", 82)
        )));
        sections.add(buildSection("wms", "仓储与库存", List.of(
                metric("库存实时状态", new BigDecimal("96.3"), "%", "安全库存", "stable", 96),
                metric("临期/过期食材预警", new BigDecimal("12"), "条", "-3 条", "up", 35),
                metric("损耗率分析", new BigDecimal("2.2"), "%", "目标 1.8%", "down", 56),
                metric("出入库与盘点差异", new BigDecimal("1.1"), "%", "-0.2%", "up", 89)
        )));
        overview.setSections(sections);

        overview.setTrendData(buildTrendData(range, stats));
        overview.setCompareData(buildCompareData(range, stats));
        overview.setDistributionData(buildDistributionData(range));
        return overview;
    }

    private DashboardOverviewVO.DashboardSectionVO buildSection(String id, String title, List<DashboardOverviewVO.DashboardMetricVO> metrics) {
        DashboardOverviewVO.DashboardSectionVO section = new DashboardOverviewVO.DashboardSectionVO();
        section.setId(id);
        section.setTitle(title);
        section.setMetrics(metrics);
        return section;
    }

    private DashboardOverviewVO.DashboardMetricVO metric(String name, BigDecimal value, String unit, String trend, String trendType, Integer score) {
        DashboardOverviewVO.DashboardMetricVO metric = new DashboardOverviewVO.DashboardMetricVO();
        metric.setName(name);
        metric.setValue(value);
        metric.setUnit(unit);
        metric.setTrend(trend);
        metric.setTrendType(trendType);
        metric.setScore(score);
        return metric;
    }

    private List<DashboardOverviewVO.DashboardTrendPointVO> buildTrendData(String range, RecipeStatisticsVO stats) {
        List<DashboardOverviewVO.DashboardTrendPointVO> result = new ArrayList<>();
        List<String> labels;
        if ("week".equals(range)) {
            labels = List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日");
        } else if ("month".equals(range)) {
            labels = List.of("第1周", "第2周", "第3周", "第4周");
        } else {
            labels = List.of("08:00", "10:00", "12:00", "14:00", "16:00", "18:00");
        }

        BigDecimal base = percent(stats.getAvgNutritionScore());
        int i = 0;
        for (String label : labels) {
            DashboardOverviewVO.DashboardTrendPointVO point = new DashboardOverviewVO.DashboardTrendPointVO();
            point.setLabel(label);
            point.setValue(base.add(BigDecimal.valueOf((i % 3) - 1)));
            result.add(point);
            i++;
        }
        return result;
    }

    private List<DashboardOverviewVO.DashboardCompareItemVO> buildCompareData(String range, RecipeStatisticsVO stats) {
        int offset = "month".equals(range) ? 2 : ("week".equals(range) ? 1 : 0);
        List<DashboardOverviewVO.DashboardCompareItemVO> result = new ArrayList<>();

        result.add(compareItem("采购", 90 + offset, 91 + offset));
        result.add(compareItem("库存", 89 + offset, 90 + offset));
        result.add(compareItem("食安", 94 + offset, Math.min(99, score(stats.getNutritionPassRate()))));
        result.add(compareItem("设备", 87 + offset, 88 + offset));
        return result;
    }

    private DashboardOverviewVO.DashboardCompareItemVO compareItem(String label, int planned, int actual) {
        DashboardOverviewVO.DashboardCompareItemVO item = new DashboardOverviewVO.DashboardCompareItemVO();
        item.setLabel(label);
        item.setPlanned(planned);
        item.setActual(actual);
        return item;
    }

    private List<DashboardOverviewVO.DashboardDistributionItemVO> buildDistributionData(String range) {
        int delta = "month".equals(range) ? -2 : ("week".equals(range) ? -1 : 0);
        List<DashboardOverviewVO.DashboardDistributionItemVO> result = new ArrayList<>();
        result.add(distributionItem("食材问题", 38 + delta, "#409eff"));
        result.add(distributionItem("加工异常", 24 - delta, "#67c23a"));
        result.add(distributionItem("设备告警", 21 + delta, "#e6a23c"));
        result.add(distributionItem("服务投诉", 17, "#f56c6c"));
        return result;
    }

    private DashboardOverviewVO.DashboardDistributionItemVO distributionItem(String label, int value, String color) {
        DashboardOverviewVO.DashboardDistributionItemVO item = new DashboardOverviewVO.DashboardDistributionItemVO();
        item.setLabel(label);
        item.setValue(Math.max(value, 0));
        item.setColor(color);
        return item;
    }

    private BigDecimal percent(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private int score(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, value.setScale(0, RoundingMode.HALF_UP).intValue()));
    }

    /**
     * 计算营养素分布
     */
    private RecipeStatisticsVO.NutritionDistribution calculateNutritionDistribution(RecipeQueryDTO query) {
        RecipeStatisticsVO.NutritionDistribution dist = new RecipeStatisticsVO.NutritionDistribution();

        List<Recipe> recipes = baseMapper.selectList(
                applyRecipeScope(new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getDeleted, 0)
                        .isNotNull(Recipe::getProtein), query)
        );

        if (recipes.isEmpty()) {
            dist.setProteinPercent(BigDecimal.ZERO);
            dist.setCarbsPercent(BigDecimal.ZERO);
            dist.setFatPercent(BigDecimal.ZERO);
            dist.setAvgCalories(BigDecimal.ZERO);
            dist.setAvgProtein(BigDecimal.ZERO);
            dist.setAvgCarbs(BigDecimal.ZERO);
            dist.setAvgFat(BigDecimal.ZERO);
            return dist;
        }

        // 计算总热量和各营养素总和
        BigDecimal totalCalories = recipes.stream()
                .filter(r -> r.getCalories() != null)
                .map(Recipe::getCalories)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProtein = recipes.stream()
                .filter(r -> r.getProtein() != null)
                .map(Recipe::getProtein)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCarbs = recipes.stream()
                .filter(r -> r.getCarbohydrate() != null)
                .map(Recipe::getCarbohydrate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFat = recipes.stream()
                .filter(r -> r.getFat() != null)
                .map(Recipe::getFat)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算供能比（蛋白质4kcal/g，碳水4kcal/g，脂肪9kcal/g）
        BigDecimal proteinCalories = totalProtein.multiply(BigDecimal.valueOf(4));
        BigDecimal carbsCalories = totalCarbs.multiply(BigDecimal.valueOf(4));
        BigDecimal fatCalories = totalFat.multiply(BigDecimal.valueOf(9));
        BigDecimal totalEnergy = proteinCalories.add(carbsCalories).add(fatCalories);

        if (totalEnergy.compareTo(BigDecimal.ZERO) > 0) {
            dist.setProteinPercent(proteinCalories.multiply(BigDecimal.valueOf(100))
                    .divide(totalEnergy, 1, RoundingMode.HALF_UP));
            dist.setCarbsPercent(carbsCalories.multiply(BigDecimal.valueOf(100))
                    .divide(totalEnergy, 1, RoundingMode.HALF_UP));
            dist.setFatPercent(fatCalories.multiply(BigDecimal.valueOf(100))
                    .divide(totalEnergy, 1, RoundingMode.HALF_UP));
        }

        // 平均值
        int count = recipes.size();
        dist.setAvgCalories(totalCalories.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP));
        dist.setAvgProtein(totalProtein.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP));
        dist.setAvgCarbs(totalCarbs.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP));
        dist.setAvgFat(totalFat.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP));

        return dist;
    }

    /**
     * 计算类别分布
     */
    private List<RecipeStatisticsVO.CategoryStats> calculateCategoryDistribution(int totalRecipes, RecipeQueryDTO query) {
        List<RecipeStatisticsVO.CategoryStats> result = new ArrayList<>();

        List<RecipeCategory> categories = categoryMapper.selectList(
                buildVisibleCategoryWrapper()
                        .eq(RecipeCategory::getStatus, "active")
                        .orderByAsc(RecipeCategory::getSortOrder)
        );

        for (RecipeCategory category : categories) {
            Integer count = baseMapper.selectCount(
                    applyRecipeScope(new LambdaQueryWrapper<Recipe>()
                            .eq(Recipe::getDeleted, 0)
                            .eq(Recipe::getCategoryId, category.getId()), query)
            ).intValue();

            if (count > 0) {
                RecipeStatisticsVO.CategoryStats stats = new RecipeStatisticsVO.CategoryStats();
                stats.setCategoryId(category.getId());
                stats.setCategoryName(category.getCategoryName());
                stats.setCategoryIcon(category.getIcon());
                stats.setRecipeCount(count);
                stats.setPercentage(BigDecimal.valueOf(count * 100.0 / totalRecipes)
                        .setScale(1, RoundingMode.HALF_UP));

                // 计算该类别平均营养评分
                Double avgScore = baseMapper.selectList(
                        applyRecipeScope(new LambdaQueryWrapper<Recipe>()
                                .eq(Recipe::getDeleted, 0)
                                .eq(Recipe::getCategoryId, category.getId())
                                .isNotNull(Recipe::getNutritionScore), query)
                ).stream()
                        .mapToInt(r -> r.getNutritionScore() != null ? r.getNutritionScore() : 0)
                        .average()
                        .orElse(0.0);
                stats.setAvgNutritionScore(BigDecimal.valueOf(avgScore).setScale(0, RoundingMode.HALF_UP).intValue());

                result.add(stats);
            }
        }

        return result;
    }

    /**
     * 获取热门菜谱
     */
    private List<RecipeStatisticsVO.HotRecipe> getHotRecipes(int limit, RecipeQueryDTO query) {
        List<Recipe> recipes = baseMapper.selectList(
                applyRecipeScope(new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getDeleted, 0)
                        .eq(Recipe::getStatus, "active")
                        .orderByDesc(Recipe::getNutritionScore)
                        .orderByDesc(Recipe::getUpdatedAt)
                        .last("LIMIT " + limit), query)
        );

        List<RecipeStatisticsVO.HotRecipe> result = new ArrayList<>();
        for (Recipe recipe : recipes) {
            RecipeStatisticsVO.HotRecipe hot = new RecipeStatisticsVO.HotRecipe();
            hot.setRecipeId(recipe.getId());
            hot.setRecipeCode(recipe.getRecipeCode());
            hot.setRecipeName(recipe.getRecipeName());
            hot.setNutritionScore(recipe.getNutritionScore());
            hot.setServeCount((int) (Math.random() * 100) + 50); // 模拟数据
            hot.setViewCount((int) (Math.random() * 500) + 100); // 模拟数据
            hot.setRating(BigDecimal.valueOf(4.0 + Math.random()).setScale(1, RoundingMode.HALF_UP));

            if (recipe.getCategoryId() != null) {
                RecipeCategory category = getVisibleCategoryById(recipe.getCategoryId());
                if (category != null) {
                    hot.setCategoryName(category.getCategoryName());
                }
            }

            result.add(hot);
        }

        return result;
    }

    @Override
    public AINutritionAnalysisVO getAiNutrition(Long id) {
        Recipe recipe = getById(id);
        if (recipe == null) {
            throw new RuntimeException("菜谱不存在");
        }

        RecipeNutritionResult nutritionResult = recipeNutritionSupportService.recalculateRecipeNutrition(recipe);
        recipe = getById(id);

        AINutritionAnalysisVO vo = new AINutritionAnalysisVO();
        vo.setRecipeId(id);
        vo.setRecipeName(recipe.getRecipeName());
        vo.setAnalysisTime(LocalDateTime.now());
        vo.setDataCompleteness(nutritionResult.getDataCompleteness());
        vo.setMissingMaterialCount(nutritionResult.getMissingMaterialCount());
        vo.setMissingMaterials(StrUtil.isBlank(nutritionResult.getMissingMaterials())
                ? List.of()
                : Arrays.stream(nutritionResult.getMissingMaterials().split(","))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .toList());

        // 营养成分
        AINutritionAnalysisVO.NutritionInfo nutritionInfo = new AINutritionAnalysisVO.NutritionInfo();
        nutritionInfo.setProtein(recipe.getProtein());
        nutritionInfo.setCarbohydrate(recipe.getCarbohydrate());
        nutritionInfo.setFat(recipe.getFat());
        nutritionInfo.setCalories(recipe.getCalories() != null ? recipe.getCalories().intValue() : null);
        vo.setNutritionInfo(nutritionInfo);

        // 维生素
        AINutritionAnalysisVO.VitaminInfo vitaminInfo = new AINutritionAnalysisVO.VitaminInfo();
        vitaminInfo.setVitaminA(nutritionResult.getVitaminA());
        vitaminInfo.setVitaminB1(nutritionResult.getVitaminB1());
        vitaminInfo.setVitaminB2(nutritionResult.getVitaminB2());
        vitaminInfo.setVitaminC(nutritionResult.getVitaminC());
        vo.setVitaminInfo(vitaminInfo);

        // 矿物质
        AINutritionAnalysisVO.MineralInfo mineralInfo = new AINutritionAnalysisVO.MineralInfo();
        mineralInfo.setCalcium(nutritionResult.getCalcium());
        mineralInfo.setIron(nutritionResult.getIron());
        mineralInfo.setZinc(nutritionResult.getZinc());
        vo.setMineralInfo(mineralInfo);

        return vo;
    }

    @Override
    public RecipeNutritionResultVO getNutritionResult(Long id) {
        Recipe recipe = getById(id);
        if (recipe == null) {
            throw BizException.notFound("菜谱不存在");
        }
        RecipeNutritionResult result = recipeNutritionSupportService.recalculateRecipeNutrition(recipe);
        RecipeNutritionResultVO vo = new RecipeNutritionResultVO();
        vo.setRecipeId(result.getRecipeId());
        vo.setCalories(result.getCalories());
        vo.setProtein(result.getProtein());
        vo.setCarbohydrate(result.getCarbohydrate());
        vo.setFat(result.getFat());
        vo.setSodium(result.getSodium());
        vo.setFiber(result.getFiber());
        vo.setVitaminA(result.getVitaminA());
        vo.setVitaminB1(result.getVitaminB1());
        vo.setVitaminB2(result.getVitaminB2());
        vo.setVitaminC(result.getVitaminC());
        vo.setVitaminD(result.getVitaminD());
        vo.setVitaminE(result.getVitaminE());
        vo.setCalcium(result.getCalcium());
        vo.setIron(result.getIron());
        vo.setZinc(result.getZinc());
        vo.setNutritionScore(result.getNutritionScore());
        vo.setPassStatus(result.getPassStatus());
        vo.setDataCompleteness(result.getDataCompleteness());
        vo.setMissingMaterialCount(result.getMissingMaterialCount());
        vo.setMissingMaterials(StrUtil.isBlank(result.getMissingMaterials()) ? List.of() :
                Arrays.stream(result.getMissingMaterials().split(",")).map(String::trim).filter(StrUtil::isNotBlank).toList());
        vo.setCalculatedAt(result.getCalculatedAt());
        return vo;
    }

    @Override
    public AIOptimizationAnalysisVO getAiOptimization(Long id) {
        Recipe recipe = getById(id);
        if (recipe == null) {
            throw new RuntimeException("菜谱不存在");
        }

        // 获取菜谱食材
        List<RecipeIngredient> ingredients = ingredientMapper.selectList(
            new LambdaQueryWrapper<RecipeIngredient>()
                .eq(RecipeIngredient::getRecipeId, id)
        );

        AIOptimizationAnalysisVO vo = new AIOptimizationAnalysisVO();
        vo.setRecipeId(id);
        vo.setRecipeName(recipe.getRecipeName());

        // 综合看板 - 基于实际营养评分
        AIOptimizationAnalysisVO.ComprehensiveDashboard dashboard = new AIOptimizationAnalysisVO.ComprehensiveDashboard();
        Integer nutritionScore = recipe.getNutritionScore() != null ? recipe.getNutritionScore() : 70;
        dashboard.setNutritionScore(nutritionScore);

        // 根据营养评分计算成本百分比
        BigDecimal costPercent = nutritionScore >= 80 ? new BigDecimal("95.00") :
                                  nutritionScore >= 60 ? new BigDecimal("105.00") : new BigDecimal("115.00");
        dashboard.setCostPercentVsAvg(costPercent);

        // 根据营养评分估算评价分数
        BigDecimal reviewScore = nutritionScore >= 80 ? new BigDecimal("4.70") :
                                  nutritionScore >= 60 ? new BigDecimal("4.20") : new BigDecimal("3.80");
        dashboard.setReviewScore(reviewScore);

        // 投诉数量与评分相关（近30天）
        int complaintCount = nutritionScore >= 80 ? 1 : nutritionScore >= 60 ? 3 : 6;
        dashboard.setComplaintCount(complaintCount);
        vo.setComprehensiveDashboard(dashboard);

        // 成本分析 - 基于食材生成模拟数据
        AIOptimizationAnalysisVO.CostAnalysis costAnalysis = new AIOptimizationAnalysisVO.CostAnalysis();
        List<AIOptimizationAnalysisVO.RecentPurchase> recentPurchases = new ArrayList<>();
        List<AIOptimizationAnalysisVO.HighCostAlert> highCostAlerts = new ArrayList<>();

        // 根据食材生成最近采购记录
        LocalDateTime now = LocalDateTime.now();
        BigDecimal avgPriceMultiplier = new BigDecimal("1.0");

        for (RecipeIngredient ingredient : ingredients) {
            if (ingredient.getMaterialName() == null) continue;

            // 添加最近采购记录
            AIOptimizationAnalysisVO.RecentPurchase purchase = new AIOptimizationAnalysisVO.RecentPurchase();
            purchase.setMaterialName(ingredient.getMaterialName());
            // 根据食材类型估算单价
            BigDecimal basePrice = estimateIngredientPrice(ingredient.getMaterialName());
            purchase.setUnitPrice(basePrice);
            purchase.setPurchaseDate(now.minusDays((long)(Math.random() * 7)));
            recentPurchases.add(purchase);

            // 随机生成高成本预警（30%概率）
            if (Math.random() > 0.7) {
                AIOptimizationAnalysisVO.HighCostAlert alert = new AIOptimizationAnalysisVO.HighCostAlert();
                alert.setMaterialName(ingredient.getMaterialName());
                alert.setReason("近期价格上涨");
                BigDecimal currentPrice = basePrice.multiply(new BigDecimal("1.15"));
                alert.setCurrentPrice(currentPrice);
                alert.setAvgPrice(basePrice);
                alert.setAiSuggestion("建议寻找替代供应商或考虑批量采购以降低成本");
                highCostAlerts.add(alert);
                avgPriceMultiplier = avgPriceMultiplier.add(new BigDecimal("0.05"));
            }
        }

        // 如果没有食材，添加默认数据
        if (recentPurchases.isEmpty()) {
            AIOptimizationAnalysisVO.RecentPurchase defaultPurchase = new AIOptimizationAnalysisVO.RecentPurchase();
            defaultPurchase.setMaterialName("主要食材");
            defaultPurchase.setUnitPrice(new BigDecimal("25.00"));
            defaultPurchase.setPurchaseDate(now.minusDays(2));
            recentPurchases.add(defaultPurchase);
        }

        costAnalysis.setRecentPurchases(recentPurchases);
        costAnalysis.setHighCostAlerts(highCostAlerts);
        vo.setCostAnalysis(costAnalysis);

        // 投诉反馈分析 - 根据菜谱类型和评分生成
        AIOptimizationAnalysisVO.ComplaintAnalysis complaintAnalysis = new AIOptimizationAnalysisVO.ComplaintAnalysis();
        complaintAnalysis.setTasteIssues(Math.max(0, complaintCount - 1));
        complaintAnalysis.setQualityIssues(complaintCount > 2 ? 1 : 0);
        complaintAnalysis.setPortionIssues(0);
        complaintAnalysis.setOtherIssues(0);

        // 根据菜谱特点生成投诉建议
        String recipeName = recipe.getRecipeName();
        if (recipeName.contains("红烧") || recipeName.contains("炖")) {
            complaintAnalysis.setComplaintSuggestions("可适当调整火候和炖煮时间，使肉质更加软烂入味");
        } else if (recipeName.contains("炒") || recipeName.contains("爆")) {
            complaintAnalysis.setComplaintSuggestions("建议控制炒制时间，保持食材鲜嫩口感");
        } else if (recipeName.contains("蒸")) {
            complaintAnalysis.setComplaintSuggestions("可调整蒸制时间，确保食材熟透同时保持鲜嫩");
        } else {
            complaintAnalysis.setComplaintSuggestions("建议根据食材特性优化烹饪工艺");
        }
        complaintAnalysis.setRecentReviews(generateRecentReviews(complaintCount, recipeName));
        vo.setComplaintAnalysis(complaintAnalysis);

        List<AIOptimizationAnalysisVO.OptimizationSuggestion> suggestions = buildAiOptimizationSuggestions(recipe, ingredients, nutritionScore, complaintCount, costPercent);

        vo.setOptimizationSuggestions(suggestions);

        return vo;
    }

    private List<AIOptimizationAnalysisVO.OptimizationSuggestion> buildAiOptimizationSuggestions(Recipe recipe,
                                                                                                  List<RecipeIngredient> ingredients,
                                                                                                  Integer nutritionScore,
                                                                                                  Integer complaintCount,
                                                                                                  BigDecimal costPercent) {
        String fallback = "建议优先优化蔬菜搭配、控制高成本食材，并保持口味稳定。";
        try {
            AiServiceConfig config = aiServiceConfigService.getActiveByModule(AiServiceType.TEXT, AiModuleCode.NUTRITION_SUGGESTION);
            String ingredientText = ingredients.stream()
                    .map(item -> item.getMaterialName() + item.getQuantity() + item.getUnit())
                    .collect(Collectors.joining("、"));
            String prompt = "请为菜谱生成3条中文优化建议。要求：每条一句，30字以内，不输出Markdown。"
                    + "菜谱名称=" + recipe.getRecipeName()
                    + "；营养评分=" + nutritionScore
                    + "；投诉数=" + complaintCount
                    + "；成本对均值占比=" + costPercent
                    + "%；食材=" + ingredientText;
            AiTextGenerateResult result = openAiCompatibleService.generateText(
                    config,
                    "你是餐饮营养优化顾问，请给出简洁可执行建议。",
                    prompt,
                    AiModuleCode.NUTRITION_SUGGESTION,
                    "business"
            );
            String content = result.isSuccess() && StrUtil.isNotBlank(result.getContent()) ? result.getContent() : fallback;
            recipe.setAiSuggestions(content);
            updateById(recipe);
            List<String> lines = Arrays.stream(content.replace("；", "\n").split("\\r?\\n"))
                    .map(String::trim)
                    .map(line -> line.replaceFirst("^[\\-•\\d\\.、\\s]+", ""))
                    .filter(StrUtil::isNotBlank)
                    .limit(3)
                    .toList();
            if (lines.isEmpty()) {
                lines = List.of(fallback);
            }
            List<AIOptimizationAnalysisVO.OptimizationSuggestion> suggestions = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                AIOptimizationAnalysisVO.OptimizationSuggestion suggestion = new AIOptimizationAnalysisVO.OptimizationSuggestion();
                suggestion.setSuggestionName("优化建议" + (i + 1));
                suggestion.setSource("AI营养建议");
                suggestion.setPriority(i == 0 ? "high" : "medium");
                suggestion.setDescription(lines.get(i));
                suggestion.setImprovementTrend("建议人工复核后执行");
                suggestions.add(suggestion);
            }
            return suggestions;
        } catch (Exception ex) {
            log.warn("生成菜谱AI优化建议失败, recipeId={}: {}", recipe.getId(), ex.getMessage());
            AIOptimizationAnalysisVO.OptimizationSuggestion suggestion = new AIOptimizationAnalysisVO.OptimizationSuggestion();
            suggestion.setSuggestionName("默认优化建议");
            suggestion.setSource("规则降级");
            suggestion.setPriority("medium");
            suggestion.setDescription(fallback);
            suggestion.setImprovementTrend("AI服务恢复后可重新生成");
            return List.of(suggestion);
        }
    }

    @Override
    public AICookingSuggestionVO getAiCookingSuggestion(AICookingSuggestionDTO dto) {
        AICookingSuggestionVO vo = new AICookingSuggestionVO();

        // 合并菜谱名称和制作步骤来分析烹饪方式（菜谱名称通常包含烹饪方式，如"小炒黄牛肉"）
        String combinedText = (dto.getMenuName() != null ? dto.getMenuName() : "") + " " +
                              (dto.getCookingSteps() != null ? dto.getCookingSteps() : "");

        // 分析烹饪方式和食材类型
        String cookingMethod = analyzeCookingMethod(combinedText);
        String mainIngredientType = analyzeMainIngredientType(dto.getIngredients());

        // 根据烹饪方式和食材类型生成建议
        CookingSuggestion suggestion = generateCookingSuggestion(cookingMethod, mainIngredientType, dto);

        vo.setSuggestedTime(suggestion.getTime());
        vo.setSuggestedTempMin(suggestion.getTempMin());
        vo.setSuggestedTempMax(suggestion.getTempMax());
        vo.setReason(suggestion.getReason());
        vo.setFoodSafetyStandard(suggestion.getFoodSafetyStandard());

        return vo;
    }

    /**
     * 分析烹饪方式
     */
    private String analyzeCookingMethod(String text) {
        if (text == null || text.trim().isEmpty()) return "通用";

        String steps = text.toLowerCase();

        if (steps.contains("蒸") || steps.contains("蒸煮")) return "蒸";
        if (steps.contains("炖") || steps.contains("焖") || steps.contains("煲")) return "炖";
        if (steps.contains("小炒") || steps.contains("炒") || steps.contains("爆炒") || steps.contains("翻炒")) return "炒";
        if (steps.contains("煮") || steps.contains("烧") || steps.contains("焯水")) return "煮";
        if (steps.contains("炸") || steps.contains("煎")) return "炸";
        if (steps.contains("烤") || steps.contains("烘")) return "烤";
        if (steps.contains("凉拌") || steps.contains("拌")) return "凉拌";

        return "通用";
    }

    /**
     * 分析主要食材类型
     */
    private String analyzeMainIngredientType(List<AICookingSuggestionDTO.IngredientInfo> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return "通用";

        for (AICookingSuggestionDTO.IngredientInfo ing : ingredients) {
            String name = ing.getMaterialName();
            if (name == null) continue;

            // 肉类检测
            if (name.contains("猪") || name.contains("牛") || name.contains("羊") ||
                name.contains("鸡") || name.contains("鸭") || name.contains("鹅") ||
                name.contains("肉") || name.contains("排骨") || name.contains("五花肉")) {
                return "肉类";
            }
        }

        for (AICookingSuggestionDTO.IngredientInfo ing : ingredients) {
            String name = ing.getMaterialName();
            if (name == null) continue;

            // 海鲜类检测
            if (name.contains("鱼") || name.contains("虾") || name.contains("蟹") ||
                name.contains("贝") || name.contains("海鲜")) {
                return "海鲜";
            }
        }

        for (AICookingSuggestionDTO.IngredientInfo ing : ingredients) {
            String name = ing.getMaterialName();
            if (name == null) continue;

            // 蔬菜类检测
            if (name.contains("菜") || name.contains("蔬") || name.contains("瓜") ||
                name.contains("豆") || name.contains("菇") || name.contains("笋") ||
                name.contains("萝卜") || name.contains("茄") || name.contains("椒")) {
                return "蔬菜";
            }
        }

        for (AICookingSuggestionDTO.IngredientInfo ing : ingredients) {
            String name = ing.getMaterialName();
            if (name == null) continue;

            // 蛋类检测
            if (name.contains("蛋")) {
                return "蛋类";
            }
        }

        return "通用";
    }

    /**
     * 生成烹饪建议
     */
    private CookingSuggestion generateCookingSuggestion(String method, String ingredientType, AICookingSuggestionDTO dto) {
        CookingSuggestion suggestion = new CookingSuggestion();

        // 根据烹饪方式和食材类型组合生成建议
        switch (method) {
            case "炖":
                suggestion = generateStewSuggestion(ingredientType);
                break;
            case "炒":
                suggestion = generateStirFrySuggestion(ingredientType);
                break;
            case "蒸":
                suggestion = generateSteamSuggestion(ingredientType);
                break;
            case "煮":
                suggestion = generateBoilSuggestion(ingredientType);
                break;
            case "炸":
                suggestion = generateDeepFrySuggestion(ingredientType);
                break;
            case "烤":
                suggestion = generateRoastSuggestion(ingredientType);
                break;
            case "凉拌":
                suggestion = generateColdDishSuggestion(ingredientType);
                break;
            default:
                suggestion = generateGeneralSuggestion(ingredientType);
        }

        // 根据食材数量调整时间
        int ingredientCount = dto.getIngredients() != null ? dto.getIngredients().size() : 1;
        if (ingredientCount > 5) {
            suggestion.setTime(suggestion.getTime() + 10);
            suggestion.setReason(suggestion.getReason() + " 食材种类较多，建议适当延长烹饪时间以确保入味。");
        }

        return suggestion;
    }

    private CookingSuggestion generateStewSuggestion(String ingredientType) {
        CookingSuggestion s = new CookingSuggestion();
        s.setTempMin(85);
        s.setTempMax(95);

        switch (ingredientType) {
            case "肉类":
                s.setTime(60);
                s.setReason("肉类炖煮建议使用小火慢炖，保持水温在微沸状态（85-95℃），可使肉质软烂入味，胶原蛋白充分溶解，口感更佳。");
                s.setFoodSafetyStandard("根据《餐饮服务食品安全操作规范》，肉类中心温度应达到75℃以上并保持至少15秒。");
                break;
            case "海鲜":
                s.setTime(15);
                s.setReason("海鲜炖煮时间不宜过长，建议中火快炖，保持海鲜鲜嫩口感和营养价值。");
                s.setFoodSafetyStandard("海鲜类需确保中心温度达到63℃以上，避免寄生虫风险。");
                break;
            case "蔬菜":
                s.setTime(20);
                s.setReason("蔬菜炖煮建议中火，时间适中以保留维生素和口感。");
                s.setFoodSafetyStandard("蔬菜类需彻底加热，确保食品安全。");
                break;
            default:
                s.setTime(45);
                s.setReason("建议使用小火慢炖，保持食材的鲜美和营养。");
                s.setFoodSafetyStandard("根据《餐饮服务食品安全操作规范》，食物中心温度应达到安全标准。");
        }
        return s;
    }

    private CookingSuggestion generateStirFrySuggestion(String ingredientType) {
        CookingSuggestion s = new CookingSuggestion();
        s.setTempMin(160);
        s.setTempMax(200);

        switch (ingredientType) {
            case "肉类":
                s.setTime(8);
                s.setReason("肉类爆炒建议使用大火快炒，高温锁住肉汁，保持肉质嫩滑。炒前建议腌制10分钟提升口感。");
                s.setFoodSafetyStandard("肉类炒制需确保中心温度达到75℃以上，翻炒均匀避免夹生。");
                break;
            case "海鲜":
                s.setTime(5);
                s.setReason("海鲜爆炒讲究快，大火快炒锁住鲜味，避免过度烹饪导致口感变老。");
                s.setFoodSafetyStandard("海鲜类需炒至全熟，中心温度达63℃以上。");
                break;
            case "蔬菜":
                s.setTime(3);
                s.setReason("蔬菜爆炒建议大火快炒，保持脆嫩口感和鲜艳色泽，减少维生素流失。");
                s.setFoodSafetyStandard("蔬菜类需炒熟透，避免生食风险。");
                break;
            case "蛋类":
                s.setTime(3);
                s.setReason("鸡蛋炒制建议中火快炒，保持蛋质嫩滑。");
                s.setFoodSafetyStandard("蛋类需完全凝固，中心温度达71℃以上。");
                break;
            default:
                s.setTime(5);
                s.setReason("建议使用大火快炒，保持食材口感和营养。");
                s.setFoodSafetyStandard("根据《餐饮服务食品安全操作规范》，炒制食材需确保熟透。");
        }
        return s;
    }

    private CookingSuggestion generateSteamSuggestion(String ingredientType) {
        CookingSuggestion s = new CookingSuggestion();
        s.setTempMin(100);
        s.setTempMax(100);

        switch (ingredientType) {
            case "肉类":
                s.setTime(25);
                s.setReason("肉类蒸制建议使用大火蒸，保持蒸汽充足，使肉质软烂。");
                s.setFoodSafetyStandard("蒸制肉类中心温度需达75℃以上，确保食品安全。");
                break;
            case "海鲜":
                s.setTime(10);
                s.setReason("海鲜蒸制时间适中，大火快蒸保持鲜嫩，过久会影响口感。");
                s.setFoodSafetyStandard("海鲜蒸制需确保完全熟透，中心温度达63℃以上。");
                break;
            case "蛋类":
                s.setTime(12);
                s.setReason("蒸蛋建议中火，蛋液过筛去泡，蒸出的蛋羹更加嫩滑。");
                s.setFoodSafetyStandard("蛋类蒸制需完全凝固，中心温度达71℃以上。");
                break;
            default:
                s.setTime(15);
                s.setReason("建议使用大火蒸制，保持食材原汁原味。");
                s.setFoodSafetyStandard("蒸制食品需确保熟透。");
        }
        return s;
    }

    private CookingSuggestion generateBoilSuggestion(String ingredientType) {
        CookingSuggestion s = new CookingSuggestion();
        s.setTempMin(100);
        s.setTempMax(100);

        switch (ingredientType) {
            case "肉类":
                s.setTime(20);
                s.setReason("肉类煮制建议先焯水去腥，再换水大火煮沸转小火慢煮。");
                s.setFoodSafetyStandard("肉类煮制中心温度需达75℃以上。");
                break;
            case "海鲜":
                s.setTime(8);
                s.setReason("海鲜煮制时间要控制好，水开后放入，煮至变色即可。");
                s.setFoodSafetyStandard("海鲜需煮至完全熟透，中心温度达63℃以上。");
                break;
            case "蔬菜":
                s.setTime(3);
                s.setReason("蔬菜焯水建议开水下锅，加少许盐和油保持翠绿，焯水后过冷水。");
                s.setFoodSafetyStandard("蔬菜类需彻底加热。");
                break;
            default:
                s.setTime(15);
                s.setReason("建议大火煮沸后转中火继续加热至熟透。");
                s.setFoodSafetyStandard("煮制食品需确保中心温度达到安全标准。");
        }
        return s;
    }

    private CookingSuggestion generateDeepFrySuggestion(String ingredientType) {
        CookingSuggestion s = new CookingSuggestion();
        s.setTempMin(160);
        s.setTempMax(180);

        switch (ingredientType) {
            case "肉类":
                s.setTime(8);
                s.setReason("肉类炸制建议油温控制在160-180℃，先低温炸熟再高温复炸上色，外酥里嫩。");
                s.setFoodSafetyStandard("油炸食品中心温度需达75℃以上，避免外焦里生。");
                break;
            case "海鲜":
                s.setTime(5);
                s.setReason("海鲜炸制建议裹粉后中火炸制，保持外酥内嫩。");
                s.setFoodSafetyStandard("海鲜需炸至全熟，中心温度达63℃以上。");
                break;
            case "蔬菜":
                s.setTime(2);
                s.setReason("蔬菜炸制建议高温快炸，保持酥脆口感，炸后沥油。");
                s.setFoodSafetyStandard("蔬菜类需确保炸透。");
                break;
            default:
                s.setTime(5);
                s.setReason("建议控制油温，避免过高导致焦糊或过低导致吸油过多。");
                s.setFoodSafetyStandard("油炸食品需确保中心温度达到安全标准。");
        }
        return s;
    }

    private CookingSuggestion generateRoastSuggestion(String ingredientType) {
        CookingSuggestion s = new CookingSuggestion();
        s.setTempMin(180);
        s.setTempMax(220);

        switch (ingredientType) {
            case "肉类":
                s.setTime(45);
                s.setReason("肉类烤制建议先用高温（220℃）锁住肉汁，再转中温（180℃）烤至熟透，烤前可腌制增加风味。");
                s.setFoodSafetyStandard("烤制肉类中心温度需达75℃以上，使用食品温度计检测更准确。");
                break;
            case "海鲜":
                s.setTime(15);
                s.setReason("海鲜烤制建议中火快烤，保持鲜嫩多汁。");
                s.setFoodSafetyStandard("海鲜需烤至全熟，中心温度达63℃以上。");
                break;
            case "蔬菜":
                s.setTime(20);
                s.setReason("蔬菜烤制建议切块均匀，刷油后中火烤制，保持蔬菜香甜。");
                s.setFoodSafetyStandard("蔬菜类需确保烤熟。");
                break;
            default:
                s.setTime(30);
                s.setReason("建议根据食材特性调整烤制温度和时间。");
                s.setFoodSafetyStandard("烤制食品需确保中心温度达到安全标准。");
        }
        return s;
    }

    private CookingSuggestion generateColdDishSuggestion(String ingredientType) {
        CookingSuggestion s = new CookingSuggestion();
        s.setTempMin(0);
        s.setTempMax(25);
        s.setTime(10);

        s.setReason("凉拌菜讲究食材新鲜和调味均匀，部分食材需焯水处理后凉拌，确保食品安全。");
        s.setFoodSafetyStandard("凉拌菜需注意：1.食材彻底清洗；2.需要焯水的食材必须焯水；3.生熟分开；4.低温保存，尽快食用。");

        return s;
    }

    private CookingSuggestion generateGeneralSuggestion(String ingredientType) {
        CookingSuggestion s = new CookingSuggestion();
        s.setTime(30);
        s.setTempMin(80);
        s.setTempMax(100);
        s.setReason("建议根据食材特性选择合适的烹饪方式，控制火候和时间，确保食物熟透且口感最佳。");
        s.setFoodSafetyStandard("根据《餐饮服务食品安全操作规范》，食物中心温度应达到安全标准，肉类75℃以上，海鲜63℃以上。");
        return s;
    }

    /**
     * 烹饪建议内部类
     */
    private static class CookingSuggestion {
        private int time;
        private int tempMin;
        private int tempMax;
        private String reason;
        private String foodSafetyStandard;

        public int getTime() { return time; }
        public void setTime(int time) { this.time = time; }
        public int getTempMin() { return tempMin; }
        public void setTempMin(int tempMin) { this.tempMin = tempMin; }
        public int getTempMax() { return tempMax; }
        public void setTempMax(int tempMax) { this.tempMax = tempMax; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getFoodSafetyStandard() { return foodSafetyStandard; }
        public void setFoodSafetyStandard(String foodSafetyStandard) { this.foodSafetyStandard = foodSafetyStandard; }
    }

    @Override
    public String uploadImage(Long id, byte[] fileData, String originalFilename) {
        Recipe recipe = getById(id);
        if (recipe == null) {
            throw new RuntimeException("菜谱不存在");
        }

        // 生成文件名
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = IdUtil.fastSimpleUUID() + extension;
        String relativePath = "/recipes/" + fileName;

        // 保存文件
        Path uploadPath = Paths.get("./upload", relativePath);
        try {
                Files.createDirectories(uploadPath.getParent());
                Files.write(uploadPath, fileData);
            } catch (IOException e) {
                log.error("上传文件失败", e);
                throw new RuntimeException("上传文件失败");
            }

        // 更新菜谱图片URL
        recipe.setImageUrl(relativePath);
        recipe.setUpdatedAt(LocalDateTime.now());
        updateById(recipe);

        return relativePath;
    }
    private LambdaQueryWrapper<Recipe> applyRecipeScope(LambdaQueryWrapper<Recipe> wrapper, RecipeQueryDTO query) {
        if (query == null) {
            return wrapper;
        }
        wrapper.eq(query.getOrgId() != null, Recipe::getOrgId, query.getOrgId());
        wrapper.in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Recipe::getOrgId, query.getOrgIds());
        wrapper.isNull(query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty(), Recipe::getId);
        return wrapper;
    }

    private RecipeVO convertToVO(Recipe recipe) {
        RecipeVO vo = new RecipeVO();
        vo.setId(recipe.getId());
        vo.setMenuCode(recipe.getRecipeCode());
        vo.setMenuName(recipe.getRecipeName());
        vo.setCategoryId(recipe.getCategoryId());
        vo.setImageUrl(recipe.getImageUrl());
        vo.setNutritionScore(recipe.getNutritionScore());
        vo.setStatus(recipe.getStatus());
        vo.setUpdatedAt(recipe.getUpdatedAt());
        vo.setUnitCost(recipe.getUnitCost());
        fillNutritionStatus(recipe.getId(), vo);

        // 查询类别名称
        if (recipe.getCategoryId() != null) {
            RecipeCategory category = getVisibleCategoryById(recipe.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getCategoryName());
                vo.setMenuCategory(category.getCategoryCode());
            }
        }

        // 设置烹饪时间和温度
        vo.setCookingTime(recipe.getTargetCookTime());
        vo.setCookingTempMin(recipe.getTargetTempMin());
        vo.setCookingTempMax(recipe.getTargetTempMax());

        // 构建营养信息
        RecipeVO.NutritionInfoVO nutritionInfo = new RecipeVO.NutritionInfoVO();
        nutritionInfo.setProtein(recipe.getProtein());
        nutritionInfo.setCarbohydrate(recipe.getCarbohydrate());
        nutritionInfo.setFat(recipe.getFat());
        nutritionInfo.setCalories(recipe.getCalories());
        vo.setNutritionInfo(nutritionInfo);

        // 查询食材列表
        List<RecipeIngredient> ingredients = ingredientMapper.selectList(
                new LambdaQueryWrapper<RecipeIngredient>()
                        .eq(RecipeIngredient::getRecipeId, recipe.getId())
                        .eq(RecipeIngredient::getDeleted, 0)
                        .orderByDesc(RecipeIngredient::getIsMain)
                        .orderByAsc(RecipeIngredient::getSortOrder)
        );
        if (ingredients != null && !ingredients.isEmpty()) {
            List<RecipeIngredientVO> ingredientVOs = ingredients.stream().map(ing -> {
                RecipeIngredientVO ingVO = new RecipeIngredientVO();
                BeanUtils.copyProperties(ing, ingVO);
                return ingVO;
            }).collect(Collectors.toList());
            vo.setIngredients(ingredientVOs);
        }

        return vo;
    }

    private void fillNutritionStatus(Long recipeId, RecipeVO vo) {
        if (recipeId == null) {
            return;
        }

        RecipeNutritionResult nutritionResult = recipeNutritionResultMapper.selectOne(
                new LambdaQueryWrapper<RecipeNutritionResult>()
                        .eq(RecipeNutritionResult::getRecipeId, recipeId)
                        .eq(RecipeNutritionResult::getDeleted, 0)
                        .orderByDesc(RecipeNutritionResult::getCalculatedAt)
                        .last("LIMIT 1")
        );

        if (nutritionResult == null) {
            vo.setDataCompleteness(BigDecimal.ZERO);
            vo.setMissingMaterialCount(0);
            vo.setMissingMaterials(Collections.emptyList());
            return;
        }

        vo.setDataCompleteness(nutritionResult.getDataCompleteness());
        vo.setMissingMaterialCount(nutritionResult.getMissingMaterialCount());
        vo.setMissingMaterials(splitMissingMaterials(nutritionResult.getMissingMaterials()));
    }

    private List<String> splitMissingMaterials(String missingMaterials) {
        if (StrUtil.isBlank(missingMaterials)) {
            return Collections.emptyList();
        }
        return Arrays.stream(missingMaterials.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    /**
     * 计算营养成分（基于物料营养映射快照汇总所有营养素）
     */
    private void calculateNutrition(Recipe recipe) {
        recipeNutritionSupportService.recalculateRecipeNutrition(recipe);
    }

    private BigDecimal sumIngredientValue(List<RecipeIngredient> ingredients, String field) {
        BigDecimal total = BigDecimal.ZERO;
        for (RecipeIngredient ingredient : ingredients) {
            BigDecimal value = switch (field) {
                case "vitaminA" -> ingredient.getVitaminA();
                case "vitaminB1" -> ingredient.getVitaminB1();
                case "vitaminB2" -> ingredient.getVitaminB2();
                case "vitaminC" -> ingredient.getVitaminC();
                case "calcium" -> ingredient.getCalcium();
                case "iron" -> ingredient.getIron();
                case "zinc" -> ingredient.getZinc();
                default -> BigDecimal.ZERO;
            };
            total = total.add(value == null ? BigDecimal.ZERO : value);
        }
        return total;
    }

    /**
     * 估算食材价格（模拟数据）
     */
    private BigDecimal estimateIngredientPrice(String materialName) {
        if (materialName == null) return new BigDecimal("15.00");

        // 肉类
        if (materialName.contains("牛肉")) return new BigDecimal("45.00");
        if (materialName.contains("羊肉")) return new BigDecimal("50.00");
        if (materialName.contains("猪肉") || materialName.contains("五花肉")) return new BigDecimal("28.00");
        if (materialName.contains("排骨")) return new BigDecimal("35.00");
        if (materialName.contains("鸡肉") || materialName.contains("鸡胸") || materialName.contains("鸡腿")) return new BigDecimal("18.00");
        if (materialName.contains("鸭肉")) return new BigDecimal("20.00");

        // 海鲜
        if (materialName.contains("龙虾")) return new BigDecimal("120.00");
        if (materialName.contains("虾") || materialName.contains("虾仁")) return new BigDecimal("55.00");
        if (materialName.contains("鲍鱼")) return new BigDecimal("80.00");
        if (materialName.contains("鱼")) return new BigDecimal("35.00");
        if (materialName.contains("蟹")) return new BigDecimal("65.00");
        if (materialName.contains("贝") || materialName.contains("蛤")) return new BigDecimal("25.00");

        // 蔬菜
        if (materialName.contains("西兰花")) return new BigDecimal("8.00");
        if (materialName.contains("芦笋")) return new BigDecimal("15.00");
        if (materialName.contains("菌") || materialName.contains("菇") || materialName.contains("木耳")) return new BigDecimal("12.00");
        if (materialName.contains("菜") || materialName.contains("蔬")) return new BigDecimal("5.00");
        if (materialName.contains("椒")) return new BigDecimal("6.00");
        if (materialName.contains("葱") || materialName.contains("姜") || materialName.contains("蒜")) return new BigDecimal("3.00");

        // 豆制品和蛋类
        if (materialName.contains("蛋")) return new BigDecimal("1.50");
        if (materialName.contains("豆腐") || materialName.contains("豆")) return new BigDecimal("4.00");

        // 调味品
        if (materialName.contains("酱油") || materialName.contains("醋") || materialName.contains("料酒")) return new BigDecimal("2.00");

        // 默认价格
        return new BigDecimal("15.00");
    }

    /**
     * 生成最近评价数据（模拟数据）
     */
    private List<AIOptimizationAnalysisVO.RecentReview> generateRecentReviews(int complaintCount, String recipeName) {
        List<AIOptimizationAnalysisVO.RecentReview> reviews = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 根据投诉数量生成评价
        if (complaintCount > 0) {
            // 添加一条较差的评价
            AIOptimizationAnalysisVO.RecentReview badReview = new AIOptimizationAnalysisVO.RecentReview();
            badReview.setScore(2);
            badReview.setContent("口味偏咸，建议减少盐的用量");
            badReview.setReviewTime(now.minusDays(3));
            reviews.add(badReview);
        }

        if (complaintCount > 2) {
            // 添加另一条一般评价
            AIOptimizationAnalysisVO.RecentReview mediumReview = new AIOptimizationAnalysisVO.RecentReview();
            mediumReview.setScore(3);
            mediumReview.setScore(3);
            mediumReview.setContent("份量可以再足一些");
            mediumReview.setReviewTime(now.minusDays(7));
            reviews.add(mediumReview);
        }

        // 添加一条正面评价
        AIOptimizationAnalysisVO.RecentReview goodReview = new AIOptimizationAnalysisVO.RecentReview();
        goodReview.setScore(5);
        goodReview.setContent("味道很好，家人很喜欢吃！");
        goodReview.setReviewTime(now.minusDays(1));
        reviews.add(goodReview);

        // 添加一条普通好评
        AIOptimizationAnalysisVO.RecentReview normalReview = new AIOptimizationAnalysisVO.RecentReview();
        normalReview.setScore(4);
        normalReview.setContent("整体不错，营养搭配合理");
        normalReview.setReviewTime(now.minusDays(5));
        reviews.add(normalReview);

        return reviews;
    }

    private LambdaQueryWrapper<RecipeCategory> buildVisibleCategoryWrapper() {
        LambdaQueryWrapper<RecipeCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecipeCategory::getDeleted, 0)
                .and(query -> query
                        .in(RecipeCategory::getCategoryCode, BUILTIN_CATEGORY_CODES)
                        .or()
                        .eq(RecipeCategory::getTenantId, getCurrentTenantId()));
        return wrapper;
    }

    private RecipeCategory findVisibleCategoryByCode(String categoryCode) {
        if (StrUtil.isBlank(categoryCode)) {
            return null;
        }
        return categoryMapper.selectOne(
                buildVisibleCategoryWrapper()
                        .eq(RecipeCategory::getCategoryCode, categoryCode)
                        .last("LIMIT 1")
        );
    }

    private RecipeCategory getVisibleCategoryById(Long id) {
        if (id == null) {
            return null;
        }
        RecipeCategory category = categoryMapper.selectById(id);
        if (category == null
                || !Objects.equals(category.getDeleted(), 0)
                || (!BUILTIN_CATEGORY_CODES.contains(category.getCategoryCode())
                && !Objects.equals(category.getTenantId(), getCurrentTenantId()))) {
            return null;
        }
        return category;
    }

    private Long getCurrentTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L;
    }

    /**
     * 导出菜谱列表
     */
    @Override
    @DataScope
    public void exportRecipes(RecipeQueryDTO query, HttpServletResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            // 查询所有匹配的菜谱（不分页）
            List<Recipe> recipes = baseMapper.selectList(
                    applyRecipeScope(new LambdaQueryWrapper<Recipe>()
                            .eq(Recipe::getDeleted, 0), query));

            // Sheet 1: 菜谱基本信息
            Sheet recipeSheet = workbook.createSheet("菜谱信息");

            String[] headers = {"菜谱编码", "菜谱名称", "菜谱类别", "成品份量(份)",
                    "烹饪时长(分钟)", "最低温度(℃)", "最高温度(℃)",
                    "单份成本(元)", "烹饪步骤", "状态"};
            Row headerRow = recipeSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIndex = 1;
            for (Recipe recipe : recipes) {
                Row row = recipeSheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(recipe.getRecipeCode() != null ? recipe.getRecipeCode() : "");
                row.createCell(1).setCellValue(recipe.getRecipeName() != null ? recipe.getRecipeName() : "");

                // 类别名称
                String categoryName = "";
                if (recipe.getCategoryId() != null) {
                    RecipeCategory category = getVisibleCategoryById(recipe.getCategoryId());
                    if (category != null) {
                        categoryName = category.getCategoryName();
                    }
                }
                row.createCell(2).setCellValue(categoryName);

                row.createCell(3).setCellValue(recipe.getServingSize() != null ? recipe.getServingSize().doubleValue() : 0);
                row.createCell(4).setCellValue(recipe.getTargetCookTime() != null ? recipe.getTargetCookTime() : 0);
                row.createCell(5).setCellValue(recipe.getTargetTempMin() != null ? recipe.getTargetTempMin() : 0);
                row.createCell(6).setCellValue(recipe.getTargetTempMax() != null ? recipe.getTargetTempMax() : 0);
                row.createCell(7).setCellValue(recipe.getUnitCost() != null ? recipe.getUnitCost().doubleValue() : 0);
                row.createCell(8).setCellValue(recipe.getCookingSteps() != null ? recipe.getCookingSteps() : "");
                row.createCell(9).setCellValue(recipe.getStatus() != null ? translateStatus(recipe.getStatus()) : "");
            }

            for (int i = 0; i < headers.length; i++) {
                recipeSheet.autoSizeColumn(i);
            }

            // Sheet 2: 所需食材
            Sheet ingredientSheet = workbook.createSheet("所需食材");

            String[] ingHeaders = {"菜谱编码", "物料名称", "规格", "用量", "单位", "是否主料"};
            Row ingHeaderRow = ingredientSheet.createRow(0);
            for (int i = 0; i < ingHeaders.length; i++) {
                ingHeaderRow.createCell(i).setCellValue(ingHeaders[i]);
            }

            int ingRowIndex = 1;
            for (Recipe recipe : recipes) {
                List<RecipeIngredient> ingredients = ingredientMapper.selectList(
                        new LambdaQueryWrapper<RecipeIngredient>()
                                .eq(RecipeIngredient::getRecipeId, recipe.getId())
                                .eq(RecipeIngredient::getDeleted, 0)
                                .orderByDesc(RecipeIngredient::getIsMain)
                                .orderByAsc(RecipeIngredient::getSortOrder));

                for (RecipeIngredient ing : ingredients) {
                    Row row = ingredientSheet.createRow(ingRowIndex++);
                    row.createCell(0).setCellValue(recipe.getRecipeCode() != null ? recipe.getRecipeCode() : "");
                    row.createCell(1).setCellValue(ing.getMaterialName() != null ? ing.getMaterialName() : "");
                    row.createCell(2).setCellValue(ing.getMaterialSpec() != null ? ing.getMaterialSpec() : "");
                    row.createCell(3).setCellValue(ing.getQuantity() != null ? ing.getQuantity().doubleValue() : 0);
                    row.createCell(4).setCellValue(ing.getUnit() != null ? ing.getUnit() : "");
                    row.createCell(5).setCellValue(ing.getIsMain() != null && ing.getIsMain() ? "是" : "否");
                }
            }

            for (int i = 0; i < ingHeaders.length; i++) {
                ingredientSheet.autoSizeColumn(i);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("菜谱列表_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            workbook.write(response.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("导出菜谱列表失败", e);
        }
    }

    /**
     * 下载菜谱导入模板
     */
    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        try {
            // 从classpath读取预置模板文件，确保所有用户下载到完全一致的模板
            ClassPathResource resource = new ClassPathResource("recipe-import-template.xlsx");
            if (!resource.exists()) {
                throw new RuntimeException("导入模板文件不存在");
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("菜谱导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");

            try (InputStream is = resource.getInputStream();
                 OutputStream os = response.getOutputStream()) {
                is.transferTo(os);
                os.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("下载导入模板失败", e);
        }
    }

    /**
     * 导入菜谱
     * 单sheet合并格式：同一菜谱多个食材占多行，菜谱信息重复填写
     * 动态读取表头列索引，兼容不同模板版本
     */
    private static final String IMPORT_ERROR_FILE_DIR = System.getProperty("java.io.tmpdir") + "/recipe-import-errors/";

    private static final String[] RECIPE_IMPORT_HEADERS = {
            "菜谱编码*", "菜谱名称*", "类别", "状态",
            "份量(g)", "菜谱描述", "烹饪时长(分钟)",
            "最低温度(℃)", "最高温度(℃)", "制作步骤",
            "食材名称", "食材规格", "用量", "单位", "是否主料"
    };

    @Override
    @DataScope
    public RecipeImportResultDTO importRecipes(MultipartFile file) {
        RecipeImportResultDTO result = new RecipeImportResultDTO();
        List<String> errors = new ArrayList<>();
        List<RecipeImportFailureDTO> failures = new ArrayList<>();
        int successCount = 0;
        int createCount = 0;
        int updateCount = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            // 优先按Sheet名"菜谱导入"选取，兼容3-Sheet模板格式；若不存在则取第一个Sheet
            Sheet sheet = workbook.getSheet("菜谱导入");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            int totalRows = sheet.getLastRowNum();

            // 动态读取表头行构建列名→列索引映射
            // 先尝试第2行(索引1)，若为空则尝试第1行(索引0)
            Row headerRow = sheet.getRow(1);
            if (headerRow == null) {
                headerRow = sheet.getRow(0);
            }
            if (headerRow == null) {
                throw new BizException("导入文件缺少表头行");
            }
            Map<String, Integer> colMap = buildColumnMap(headerRow);

            // 确定数据起始行：表头行的下一行
            int dataStartRow = headerRow.getRowNum() + 1;

            // 按菜谱编码分组行（同一菜谱多行食材，菜谱信息重复填写）
            // 同时收集编码为空的行，报告为导入错误
            Map<String, List<Integer>> recipeCodeRowMap = new LinkedHashMap<>();
            List<Integer> blankCodeRows = new ArrayList<>();
            for (int i = dataStartRow; i <= totalRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String recipeCode = getCellValueByColName(row, colMap, "菜谱编码*");
                if (StrUtil.isNotBlank(recipeCode)) {
                    recipeCodeRowMap.computeIfAbsent(recipeCode, k -> new ArrayList<>()).add(i);
                } else {
                    blankCodeRows.add(i);
                }
            }

            // 报告编码为空的数据行
            for (int rowIndex : blankCodeRows) {
                Row row = sheet.getRow(rowIndex);
                String recipeName = row != null ? getCellValueByColName(row, colMap, "菜谱名称*") : "";
                String errorMsg = "第" + (rowIndex + 1) + "行: 菜谱编码不能为空";
                errors.add(errorMsg);
                failures.add(new RecipeImportFailureDTO(rowIndex + 1, "", recipeName, errorMsg));
            }

            // 遍历每个菜谱编码
            for (Map.Entry<String, List<Integer>> entry : recipeCodeRowMap.entrySet()) {
                String recipeCode = entry.getKey();
                List<Integer> rowIndices = entry.getValue();
                int firstRowIndex = rowIndices.get(0);
                Row firstRow = sheet.getRow(firstRowIndex);

                String recipeName = getCellValueByColName(firstRow, colMap, "菜谱名称*");
                String categoryName = getCellValueByColName(firstRow, colMap, "类别");

                // 必填校验
                if (StrUtil.isBlank(recipeName)) {
                    String errorMsg = "第" + (firstRowIndex + 1) + "行: 菜谱名称不能为空";
                    errors.add(errorMsg);
                    failures.add(new RecipeImportFailureDTO(firstRowIndex + 1, recipeCode, "", errorMsg));
                    continue;
                }

                boolean isUpdate = false; // 记录是否为更新操作，用于失败时回滚清理
                try {
                    // 查找已有菜谱（按编码匹配用于更新）
                    Recipe existingRecipe = baseMapper.selectOne(
                            new LambdaQueryWrapper<Recipe>()
                                    .eq(Recipe::getRecipeCode, recipeCode));

                    // 查找类别
                    Long categoryId = null;
                    if (StrUtil.isNotBlank(categoryName)) {
                        RecipeCategory category = categoryMapper.selectOne(
                                new LambdaQueryWrapper<RecipeCategory>()
                                        .eq(RecipeCategory::getCategoryName, categoryName)
                                        .eq(RecipeCategory::getDeleted, 0)
                                        .last("LIMIT 1"));
                        if (category != null) {
                            categoryId = category.getId();
                        }
                    }

                    isUpdate = existingRecipe != null;
                    Recipe recipe;

                    // 提取数值列到局部变量，避免重复读取
                    BigDecimal servingSize = getNumericByColName(firstRow, colMap, "份量(g)");
                    BigDecimal cookTimeVal = getNumericByColName(firstRow, colMap, "烹饪时长(分钟)");
                    BigDecimal tempMinVal = getNumericByColName(firstRow, colMap, "最低温度(℃)");
                    BigDecimal tempMaxVal = getNumericByColName(firstRow, colMap, "最高温度(℃)");
                    Integer targetCookTime = cookTimeVal != null ? cookTimeVal.intValue() : null;
                    Integer targetTempMin = tempMinVal != null ? tempMinVal.intValue() : null;
                    Integer targetTempMax = tempMaxVal != null ? tempMaxVal.intValue() : null;
                    String cookingSteps = getCellValueByColName(firstRow, colMap, "制作步骤");
                    String description = getCellValueByColName(firstRow, colMap, "菜谱描述");
                    String statusFromExcel = translateStatusFromExcel(getCellValueByColName(firstRow, colMap, "状态"));

                    if (isUpdate) {
                        // 更新已有菜谱 — 先删除旧食材再重新导入
                        recipe = existingRecipe;
                        recipe.setRecipeName(recipeName);
                        recipe.setCategoryId(categoryId);
                        recipe.setServingSize(servingSize);
                        recipe.setTargetCookTime(targetCookTime);
                        recipe.setTargetTempMin(targetTempMin);
                        recipe.setTargetTempMax(targetTempMax);
                        recipe.setCookingSteps(cookingSteps);
                        recipe.setDescription(description);
                        if (StrUtil.isNotBlank(statusFromExcel)) {
                            recipe.setStatus(statusFromExcel);
                        }
                        baseMapper.updateById(recipe);
                        // 删除旧食材
                        ingredientMapper.delete(new LambdaQueryWrapper<RecipeIngredient>()
                                .eq(RecipeIngredient::getRecipeId, recipe.getId()));
                        updateCount++;
                    } else {
                        // 新增菜谱
                        recipe = new Recipe();
                        recipe.setRecipeCode(recipeCode);
                        recipe.setRecipeName(recipeName);
                        recipe.setCategoryId(categoryId);
                        recipe.setOrgId(getCurrentTenantId());
                        recipe.setServingSize(servingSize);
                        recipe.setTargetCookTime(targetCookTime);
                        recipe.setTargetTempMin(targetTempMin);
                        recipe.setTargetTempMax(targetTempMax);
                        recipe.setCookingSteps(cookingSteps);
                        recipe.setDescription(description);
                        recipe.setStatus(StrUtil.isNotBlank(statusFromExcel) ? statusFromExcel : "active");
                        baseMapper.insert(recipe);
                        createCount++;
                    }

                    // 导入食材 — 从同一行中读取食材列
                    int sortOrder = 1;
                    for (int rowIndex : rowIndices) {
                        Row row = sheet.getRow(rowIndex);
                        if (row == null) continue;

                        String materialName = getCellValueByColName(row, colMap, "食材名称");
                        if (StrUtil.isBlank(materialName)) continue;

                        RecipeIngredient ingredient = new RecipeIngredient();
                        ingredient.setRecipeId(recipe.getId());
                        ingredient.setMaterialName(materialName);
                        ingredient.setMaterialSpec(getCellValueByColName(row, colMap, "食材规格"));
                        ingredient.setQuantity(getNumericByColName(row, colMap, "用量"));
                        ingredient.setUnit(getCellValueByColName(row, colMap, "单位"));
                        String isMainStr = getCellValueByColName(row, colMap, "是否主料");
                        ingredient.setIsMain("是".equals(isMainStr) || "true".equalsIgnoreCase(isMainStr));
                        ingredient.setSortOrder(sortOrder++);
                        ingredientMapper.insert(ingredient);
                    }

                    // 根据食材重新计算营养数据和单份成本（非关键步骤，失败不影响导入结果）
                    try {
                        calculateNutrition(recipe);
                    } catch (Exception nutritionEx) {
                        log.warn("菜谱[{}]营养计算失败，不影响导入结果: {}", recipeCode, nutritionEx.getMessage());
                    }

                    successCount++;
                } catch (Exception e) {
                    String errorMsg = "第" + (firstRowIndex + 1) + "行: " + e.getMessage();
                    errors.add(errorMsg);
                    failures.add(new RecipeImportFailureDTO(firstRowIndex + 1, recipeCode, recipeName, errorMsg));
                    // 回滚当前菜谱的已写入数据：删除已插入的食材和菜谱记录
                    cleanupFailedRecipeImport(recipeCode, isUpdate);
                }
            }

            result.setTotal(successCount + failures.size());
            result.setSuccessCount(successCount);
            result.setFailCount(failures.size());
            result.setCreateCount(createCount);
            result.setUpdateCount(updateCount);
            result.setHasErrors(!failures.isEmpty());
            result.setErrors(errors);
            result.setFailures(failures);

            // 生成错误文件（如有失败记录），错误文件生成失败不影响导入结果
            if (!failures.isEmpty()) {
                try {
                    String errorFileUrl = generateImportErrorFile(failures);
                    result.setErrorFileUrl(errorFileUrl);
                } catch (Exception e) {
                    log.warn("生成导入错误文件失败，不影响导入结果: {}", e.getMessage());
                }
            }

        } catch (IOException e) {
            throw new BizException("导入文件读取失败");
        }

        return result;
    }

    /**
     * 回滚导入失败的菜谱数据：删除已插入的食材和菜谱记录
     * @param recipeCode 菜谱编码
     * @param isUpdate 是否为更新操作（更新时不删除菜谱本身，仅删除新插入的食材）
     */
    private void cleanupFailedRecipeImport(String recipeCode, boolean isUpdate) {
        try {
            Recipe recipe = baseMapper.selectOne(
                    new LambdaQueryWrapper<Recipe>().eq(Recipe::getRecipeCode, recipeCode));
            if (recipe != null) {
                // 删除已插入的食材
                ingredientMapper.delete(new LambdaQueryWrapper<RecipeIngredient>()
                        .eq(RecipeIngredient::getRecipeId, recipe.getId()));
                if (!isUpdate) {
                    // 新增的菜谱记录也要删除
                    baseMapper.deleteById(recipe.getId());
                }
            }
        } catch (Exception cleanupEx) {
            log.warn("回滚导入失败菜谱[{}]数据时出错: {}", recipeCode, cleanupEx.getMessage());
        }
    }

    @Override
    public void downloadImportErrorFile(String fileName, HttpServletResponse response) {
        try {
            File file = new File(IMPORT_ERROR_FILE_DIR + fileName);
            if (!file.exists()) {
                throw BizException.notFound("错误文件不存在或已过期");
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.transferTo(response.getOutputStream());
            }
        } catch (IOException e) {
            log.error("下载菜谱导入错误文件失败", e);
            throw BizException.badRequest("下载菜谱导入错误文件失败");
        }
    }

    /**
     * 生成导入错误文件（.xlsx）
     */
    private String generateImportErrorFile(List<RecipeImportFailureDTO> failures) {
        try {
            File dir = new File(IMPORT_ERROR_FILE_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("创建菜谱导入错误文件目录失败: {}", dir.getAbsolutePath());
            }

            String fileName = "recipe_import_errors_" + System.currentTimeMillis() + ".xlsx";
            String filePath = IMPORT_ERROR_FILE_DIR + fileName;

            try (Workbook workbook = new XSSFWorkbook(); FileOutputStream outputStream = new FileOutputStream(filePath)) {
                Sheet sheet = workbook.createSheet("导入失败数据");

                // 提示行
                Row tipRow = sheet.createRow(0);
                tipRow.setHeightInPoints(30);
                Cell tipCell = tipRow.createCell(0);
                tipCell.setCellValue("【说明】以下数据导入失败，请根据失败原因修正后重新导入。");
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, RECIPE_IMPORT_HEADERS.length));

                // 表头行（原始列 + 失败原因列）
                Row headerRow = sheet.createRow(1);
                headerRow.setHeightInPoints(20);
                for (int i = 0; i < RECIPE_IMPORT_HEADERS.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(RECIPE_IMPORT_HEADERS[i]);
                }
                Cell errorHeaderCell = headerRow.createCell(RECIPE_IMPORT_HEADERS.length);
                errorHeaderCell.setCellValue("失败原因");

                // 失败数据行
                int rowNum = 2;
                for (RecipeImportFailureDTO failure : failures) {
                    Row dataRow = sheet.createRow(rowNum++);
                    dataRow.createCell(0).setCellValue(blankToEmpty(failure.getRecipeCode()));
                    dataRow.createCell(1).setCellValue(blankToEmpty(failure.getRecipeName()));
                    for (int i = 2; i < RECIPE_IMPORT_HEADERS.length; i++) {
                        dataRow.createCell(i).setCellValue("");
                    }
                    dataRow.createCell(RECIPE_IMPORT_HEADERS.length).setCellValue(blankToEmpty(failure.getErrorMessage()));
                }

                workbook.write(outputStream);
            }
            return "/api/v1/recipe/recipes/import/errors/" + fileName;
        } catch (Exception ex) {
            log.error("生成菜谱导入错误文件失败", ex);
            return null;
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 从表头行构建列名→列索引映射
     * 支持模糊匹配：去尾星号、忽略空格
     */
    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> colMap = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            var cell = headerRow.getCell(i);
            if (cell == null) continue;
            String headerName = cell.getStringCellValue();
            if (StrUtil.isBlank(headerName)) continue;
            // 去掉尾部星号（必填标记）和空格，用于匹配
            String normalized = headerName.replaceAll("\\*+$", "").trim();
            colMap.put(headerName, i);
            // 也存不带星号的版本，方便按名称查找
            if (!normalized.equals(headerName)) {
                colMap.put(normalized, i);
            }
        }
        return colMap;
    }

    /**
     * 按列名获取字符串值，找不到列时返回空串
     */
    private String getCellValueByColName(Row row, Map<String, Integer> colMap, String colName) {
        Integer colIndex = resolveColIndex(colMap, colName);
        if (colIndex == null) return "";
        return getCellStringValue(row, colIndex);
    }

    /**
     * 按列名获取数值值，找不到列时返回null
     */
    private BigDecimal getNumericByColName(Row row, Map<String, Integer> colMap, String colName) {
        Integer colIndex = resolveColIndex(colMap, colName);
        if (colIndex == null) return null;
        return getCellNumericValue(row, colIndex);
    }

    /**
     * 解析列索引：先精确匹配表头原文，再匹配去掉星号后的名称
     */
    private Integer resolveColIndex(Map<String, Integer> colMap, String colName) {
        // 精确匹配
        if (colMap.containsKey(colName)) return colMap.get(colName);
        // 去星号匹配
        String normalized = colName.replaceAll("\\*+$", "").trim();
        if (colMap.containsKey(normalized)) return colMap.get(normalized);
        return null;
    }

    /**
     * 状态翻译：导出时将英文转为中文
     */
    private String translateStatus(String status) {
        if ("active".equals(status)) return "启用";
        if ("inactive".equals(status)) return "停用";
        return status;
    }

    /**
     * 状态翻译：导入时将中文/英文统一转为数据库存储的英文值
     */
    private String translateStatusFromExcel(String status) {
        if (StrUtil.isBlank(status)) return "";
        if ("启用".equals(status) || "active".equalsIgnoreCase(status)) return "active";
        if ("停用".equals(status) || "inactive".equalsIgnoreCase(status)) return "inactive";
        // 其他值原样保留
        return status;
    }

    private String getCellStringValue(Row row, int colIndex) {
        if (row.getCell(colIndex) == null) return "";
        var cell = row.getCell(colIndex);
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }

    private BigDecimal getCellNumericValue(Row row, int colIndex) {
        if (row.getCell(colIndex) == null) return null;
        var cell = row.getCell(colIndex);
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
            String val = cell.getStringCellValue();
            if (StrUtil.isNotBlank(val)) {
                try { return new BigDecimal(val); } catch (NumberFormatException e) { return null; }
            }
        }
        return null;
    }
}
