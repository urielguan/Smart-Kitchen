package com.xykj.recipe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xykj.recipe.config.NutritionTargetConfig;
import com.xykj.recipe.entity.Recipe;
import com.xykj.recipe.entity.RecipeIngredient;
import com.xykj.recipe.entity.RecipeNutritionResult;
import com.xykj.recipe.mapper.RecipeIngredientMapper;
import com.xykj.recipe.mapper.RecipeMapper;
import com.xykj.recipe.mapper.RecipeNutritionResultMapper;
import com.xykj.recipe.service.RecipeNutritionSupportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeNutritionSupportServiceImpl implements RecipeNutritionSupportService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Map<String, String> MATERIAL_NAME_ALIASES = Map.ofEntries(
            Map.entry("五花肉", "猪肉"),
            Map.entry("猪里脊", "猪肉"),
            Map.entry("前腿肉", "猪肉"),
            Map.entry("后腿肉", "猪肉"),
            Map.entry("鸡胸", "鸡胸肉"),
            Map.entry("鸡腿", "鸡腿肉"),
            Map.entry("酱油", "生抽")
    );

    private final RecipeMapper recipeMapper;
    private final RecipeIngredientMapper ingredientMapper;
    private final RecipeNutritionResultMapper nutritionResultMapper;
    private final JdbcTemplate jdbcTemplate;
    private final NutritionTargetConfig nutritionTargetConfig;

    @Override
    public void applyMaterialNutritionSnapshot(RecipeIngredient ingredient) {
        applyMaterialNutritionSnapshot(null, ingredient);
    }

    private void applyMaterialNutritionSnapshot(Recipe recipe, RecipeIngredient ingredient) {
        clearNutritionSnapshot(ingredient);
        if (ingredient.getQuantity() == null) {
            return;
        }

        Map<String, Object> materialNutrition = findIngredientNutritionSource(recipe, ingredient);
        if (materialNutrition == null) {
            return;
        }

        BigDecimal ratio = ingredient.getQuantity().divide(HUNDRED, 4, RoundingMode.HALF_UP);
        ingredient.setCalories(scale(multiply(materialNutrition.get("calories"), ratio)));
        ingredient.setProtein(scale(multiply(materialNutrition.get("protein"), ratio)));
        ingredient.setCarbohydrate(scale(multiply(materialNutrition.get("carbohydrate"), ratio)));
        ingredient.setFat(scale(multiply(materialNutrition.get("fat"), ratio)));
        ingredient.setSodium(scale(multiply(materialNutrition.get("sodium"), ratio)));
        ingredient.setFiber(scale(multiply(materialNutrition.get("fiber"), ratio)));
        ingredient.setVitaminA(scale(multiply(materialNutrition.get("vitamin_a"), ratio)));
        ingredient.setVitaminB1(scale(multiply(materialNutrition.get("vitamin_b1"), ratio)));
        ingredient.setVitaminB2(scale(multiply(materialNutrition.get("vitamin_b2"), ratio)));
        ingredient.setVitaminC(scale(multiply(materialNutrition.get("vitamin_c"), ratio)));
        ingredient.setVitaminD(scale(multiply(materialNutrition.get("vitamin_d"), ratio)));
        ingredient.setVitaminE(scale(multiply(materialNutrition.get("vitamin_e"), ratio)));
        ingredient.setCalcium(scale(multiply(materialNutrition.get("calcium"), ratio)));
        ingredient.setIron(scale(multiply(materialNutrition.get("iron"), ratio)));
        ingredient.setZinc(scale(multiply(materialNutrition.get("zinc"), ratio)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecipeNutritionResult recalculateRecipeNutrition(Recipe recipe) {
        List<RecipeIngredient> ingredients = ingredientMapper.selectList(
                new LambdaQueryWrapper<RecipeIngredient>()
                        .eq(RecipeIngredient::getRecipeId, recipe.getId())
                        .eq(RecipeIngredient::getDeleted, 0)
        );

        BigDecimal totalCalories = BigDecimal.ZERO;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalCarbs = BigDecimal.ZERO;
        BigDecimal totalFat = BigDecimal.ZERO;
        BigDecimal totalSodium = BigDecimal.ZERO;
        BigDecimal totalFiber = BigDecimal.ZERO;
        BigDecimal totalVitaminA = BigDecimal.ZERO;
        BigDecimal totalVitaminB1 = BigDecimal.ZERO;
        BigDecimal totalVitaminB2 = BigDecimal.ZERO;
        BigDecimal totalVitaminC = BigDecimal.ZERO;
        BigDecimal totalVitaminD = BigDecimal.ZERO;
        BigDecimal totalVitaminE = BigDecimal.ZERO;
        BigDecimal totalCalcium = BigDecimal.ZERO;
        BigDecimal totalIron = BigDecimal.ZERO;
        BigDecimal totalZinc = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        List<String> missingMaterials = new ArrayList<>();
        int resolvedCount = 0;

        for (RecipeIngredient ingredient : ingredients) {
            applyMaterialNutritionSnapshot(recipe, ingredient);
            persistIngredientNutritionSnapshot(ingredient);

            if (hasNutritionSnapshot(ingredient)) {
                resolvedCount++;
            } else {
                missingMaterials.add(ingredient.getMaterialName() == null ? "未命名物料" : ingredient.getMaterialName());
            }

            totalCalories = totalCalories.add(value(ingredient.getCalories()));
            totalProtein = totalProtein.add(value(ingredient.getProtein()));
            totalCarbs = totalCarbs.add(value(ingredient.getCarbohydrate()));
            totalFat = totalFat.add(value(ingredient.getFat()));
            totalSodium = totalSodium.add(value(ingredient.getSodium()));
            totalFiber = totalFiber.add(value(ingredient.getFiber()));
            totalVitaminA = totalVitaminA.add(value(ingredient.getVitaminA()));
            totalVitaminB1 = totalVitaminB1.add(value(ingredient.getVitaminB1()));
            totalVitaminB2 = totalVitaminB2.add(value(ingredient.getVitaminB2()));
            totalVitaminC = totalVitaminC.add(value(ingredient.getVitaminC()));
            totalVitaminD = totalVitaminD.add(value(ingredient.getVitaminD()));
            totalVitaminE = totalVitaminE.add(value(ingredient.getVitaminE()));
            totalCalcium = totalCalcium.add(value(ingredient.getCalcium()));
            totalIron = totalIron.add(value(ingredient.getIron()));
            totalZinc = totalZinc.add(value(ingredient.getZinc()));
            totalCost = totalCost.add(estimateIngredientCost(ingredient));
        }

        BigDecimal estimatedServings = calculateEstimatedServings(ingredients, recipe.getServingSize());
        BigDecimal perServingCalories = safeDivide(totalCalories, estimatedServings);
        BigDecimal perServingProtein = safeDivide(totalProtein, estimatedServings);
        BigDecimal perServingCarbs = safeDivide(totalCarbs, estimatedServings);
        BigDecimal perServingFat = safeDivide(totalFat, estimatedServings);
        BigDecimal perServingSodium = safeDivide(totalSodium, estimatedServings);
        BigDecimal perServingFiber = safeDivide(totalFiber, estimatedServings);
        NutritionTargetConfig.GroupNutritionTarget target = nutritionTargetConfig.getPerMealTarget("adult");

        recipe.setCalories(scale(totalCalories));
        recipe.setProtein(scale(totalProtein));
        recipe.setCarbohydrate(scale(totalCarbs));
        recipe.setFat(scale(totalFat));
        recipe.setSodium(scale(totalSodium));
        recipe.setFiber(scale(totalFiber));
        recipe.setVitaminA(scale(totalVitaminA));
        recipe.setVitaminB1(scale(totalVitaminB1));
        recipe.setVitaminB2(scale(totalVitaminB2));
        recipe.setVitaminC(scale(totalVitaminC));
        recipe.setVitaminD(scale(totalVitaminD));
        recipe.setVitaminE(scale(totalVitaminE));
        recipe.setCalcium(scale(totalCalcium));
        recipe.setIron(scale(totalIron));
        recipe.setZinc(scale(totalZinc));
        recipe.setNutritionScore(calculateNutritionScore(
                perServingCalories, perServingProtein, perServingCarbs, perServingFat, perServingFiber, perServingSodium, target
        ));
        recipe.setUnitCost(scale(totalCost));
        recipeMapper.updateById(recipe);

        RecipeNutritionResult result = nutritionResultMapper.selectOne(new LambdaQueryWrapper<RecipeNutritionResult>()
                .eq(RecipeNutritionResult::getRecipeId, recipe.getId())
                .eq(RecipeNutritionResult::getDeleted, 0));
        if (result == null) {
            result = new RecipeNutritionResult();
            result.setRecipeId(recipe.getId());
            result.setCalcVersion(1);
            result.setCreatedAt(LocalDateTime.now());
        } else {
            result.setCalcVersion(result.getCalcVersion() == null ? 1 : result.getCalcVersion() + 1);
        }
        result.setCalories(recipe.getCalories());
        result.setProtein(recipe.getProtein());
        result.setCarbohydrate(recipe.getCarbohydrate());
        result.setFat(recipe.getFat());
        result.setSodium(recipe.getSodium());
        result.setFiber(recipe.getFiber());
        result.setVitaminA(recipe.getVitaminA());
        result.setVitaminB1(recipe.getVitaminB1());
        result.setVitaminB2(recipe.getVitaminB2());
        result.setVitaminC(recipe.getVitaminC());
        result.setVitaminD(recipe.getVitaminD());
        result.setVitaminE(recipe.getVitaminE());
        result.setCalcium(recipe.getCalcium());
        result.setIron(recipe.getIron());
        result.setZinc(recipe.getZinc());
        result.setNutritionScore(recipe.getNutritionScore());
        result.setPassStatus(determinePassStatus(
                perServingCalories, perServingProtein, perServingCarbs, perServingFat, perServingFiber, perServingSodium,
                target, missingMaterials.isEmpty()
        ));
        result.setDataCompleteness(ingredients.isEmpty()
                ? new BigDecimal("0.00")
                : BigDecimal.valueOf(resolvedCount * 100.0 / ingredients.size()).setScale(2, RoundingMode.HALF_UP));
        result.setMissingMaterialCount(missingMaterials.size());
        result.setMissingMaterials(String.join(", ", missingMaterials));
        result.setCalculatedAt(LocalDateTime.now());
        result.setUpdatedAt(LocalDateTime.now());
        if (result.getId() == null) {
            nutritionResultMapper.insert(result);
        } else {
            nutritionResultMapper.updateById(result);
        }
        return result;
    }

    @Override
    public RecipeNutritionResult getRecipeNutritionResult(Long recipeId) {
        return nutritionResultMapper.selectOne(new LambdaQueryWrapper<RecipeNutritionResult>()
                .eq(RecipeNutritionResult::getRecipeId, recipeId)
                .eq(RecipeNutritionResult::getDeleted, 0));
    }

    private Map<String, Object> findIngredientNutritionSource(Recipe recipe, RecipeIngredient ingredient) {
        if (ingredient.getMaterialId() != null) {
            Map<String, Object> materialNutrition = findMaterialNutrition(ingredient.getMaterialId());
            if (materialNutrition != null) {
                return materialNutrition;
            }
        }
        return findMaterialNutritionByName(recipe, ingredient.getMaterialName());
    }

    private Map<String, Object> findMaterialNutrition(Long materialId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT calories, protein, carbohydrate, fat, sodium, fiber,
                       vitamin_a, vitamin_b1, vitamin_b2, vitamin_c, vitamin_e, calcium, iron, zinc
                  FROM wms_material
                 WHERE id = ? AND deleted = 0
                """, materialId);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.getFirst();
    }

    private Map<String, Object> findMaterialNutritionByName(Recipe recipe, String materialName) {
        if (recipe == null || !StringUtils.hasText(materialName)) {
            return null;
        }

        for (String candidateName : buildMaterialNameCandidates(materialName)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT calories, protein, carbohydrate, fat, sodium, fiber,
                           vitamin_a, vitamin_b1, vitamin_b2, vitamin_c, vitamin_e, calcium, iron, zinc
                      FROM wms_material
                     WHERE deleted = 0
                       AND material_name = ?
                       AND tenant_id = ?
                       AND (org_id = ? OR org_id IS NULL OR org_id = 0)
                     ORDER BY CASE WHEN org_id = ? THEN 0 ELSE 1 END, id ASC
                     LIMIT 1
                    """,
                    candidateName,
                    recipe.getTenantId() != null ? recipe.getTenantId() : 1L,
                    recipe.getOrgId(),
                    recipe.getOrgId());
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
        }

        return null;
    }

    private Set<String> buildMaterialNameCandidates(String materialName) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalizedName = materialName.trim();
        candidates.add(normalizedName);

        String alias = MATERIAL_NAME_ALIASES.get(normalizedName);
        if (StringUtils.hasText(alias)) {
            candidates.add(alias);
        }

        return candidates;
    }

    private boolean hasNutritionSnapshot(RecipeIngredient ingredient) {
        return ingredient.getCalories() != null
                || ingredient.getProtein() != null
                || ingredient.getCarbohydrate() != null
                || ingredient.getFat() != null
                || ingredient.getSodium() != null
                || ingredient.getFiber() != null
                || ingredient.getVitaminA() != null
                || ingredient.getVitaminB1() != null
                || ingredient.getVitaminB2() != null
                || ingredient.getVitaminC() != null
                || ingredient.getVitaminD() != null
                || ingredient.getVitaminE() != null
                || ingredient.getCalcium() != null
                || ingredient.getIron() != null
                || ingredient.getZinc() != null;
    }

    private void persistIngredientNutritionSnapshot(RecipeIngredient ingredient) {
        jdbcTemplate.update("""
                UPDATE recipe_ingredient
                   SET calories = ?,
                       protein = ?,
                       carbohydrate = ?,
                       fat = ?,
                       sodium = ?,
                       fiber = ?,
                       vitamin_a = ?,
                       vitamin_b1 = ?,
                       vitamin_b2 = ?,
                       vitamin_c = ?,
                       vitamin_d = ?,
                       vitamin_e = ?,
                       calcium = ?,
                       iron = ?,
                       zinc = ?
                 WHERE id = ? AND deleted = 0
                """,
                ingredient.getCalories(),
                ingredient.getProtein(),
                ingredient.getCarbohydrate(),
                ingredient.getFat(),
                ingredient.getSodium(),
                ingredient.getFiber(),
                ingredient.getVitaminA(),
                ingredient.getVitaminB1(),
                ingredient.getVitaminB2(),
                ingredient.getVitaminC(),
                ingredient.getVitaminD(),
                ingredient.getVitaminE(),
                ingredient.getCalcium(),
                ingredient.getIron(),
                ingredient.getZinc(),
                ingredient.getId()
        );
    }

    private void clearNutritionSnapshot(RecipeIngredient ingredient) {
        ingredient.setCalories(null);
        ingredient.setProtein(null);
        ingredient.setCarbohydrate(null);
        ingredient.setFat(null);
        ingredient.setSodium(null);
        ingredient.setFiber(null);
        ingredient.setVitaminA(null);
        ingredient.setVitaminB1(null);
        ingredient.setVitaminB2(null);
        ingredient.setVitaminC(null);
        ingredient.setVitaminD(null);
        ingredient.setVitaminE(null);
        ingredient.setCalcium(null);
        ingredient.setIron(null);
        ingredient.setZinc(null);
    }

    private BigDecimal multiply(Object raw, BigDecimal ratio) {
        if (raw == null) {
            return null;
        }
        return decimal(raw).multiply(ratio);
    }

    private BigDecimal calculateEstimatedServings(List<RecipeIngredient> ingredients, BigDecimal servingSize) {
        if (servingSize == null || servingSize.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal totalWeight = ingredients.stream()
                .map(RecipeIngredient::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal servings = totalWeight.divide(servingSize, 4, RoundingMode.HALF_UP);
        return servings.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : servings;
    }

    private BigDecimal safeDivide(BigDecimal value, BigDecimal divisor) {
        if (value == null || divisor == null || divisor.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return value.divide(divisor, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal estimateIngredientCost(RecipeIngredient ingredient) {
        if (ingredient.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal pricePerUnit = estimateIngredientPrice(ingredient.getMaterialName());
        return pricePerUnit.multiply(ingredient.getQuantity()).divide(new BigDecimal("500"), 2, RoundingMode.HALF_UP);
    }

    private int calculateNutritionScore(BigDecimal calories, BigDecimal protein, BigDecimal carbohydrate,
                                        BigDecimal fat, BigDecimal fiber, BigDecimal sodium,
                                        NutritionTargetConfig.GroupNutritionTarget target) {
        BigDecimal proteinRatio = macroRatio(protein, carbohydrate, fat, protein);
        BigDecimal carbRatio = macroRatio(protein, carbohydrate, fat, carbohydrate);
        BigDecimal fatRatio = macroRatio(protein, carbohydrate, fat, fat, true);

        int score = 0;
        score += scoreRangeMetric(calories, target.getCalories(), new BigDecimal("0.80"), new BigDecimal("1.20"), 20);
        score += scoreRangeMetric(protein, target.getProtein(), new BigDecimal("0.80"), new BigDecimal("1.20"), 20);
        score += scoreRangeMetric(carbohydrate, target.getCarbohydrate(), new BigDecimal("0.80"), new BigDecimal("1.20"), 15);
        score += scoreRangeMetric(fat, target.getFat(), new BigDecimal("0.80"), new BigDecimal("1.20"), 15);
        score += scoreMinimumMetric(fiber, target.getFiber(), new BigDecimal("0.70"), 10);
        score += scoreMaximumMetric(sodium, target.getSodium(), BigDecimal.ONE, 10);
        score += scoreMacroRatio(proteinRatio, target.getProteinRange(), 3);
        score += scoreMacroRatio(carbRatio, target.getCarbRange(), 3);
        score += scoreMacroRatio(fatRatio, target.getFatRange(), 4);
        return Math.min(score, 100);
    }

    private String determinePassStatus(BigDecimal calories, BigDecimal protein, BigDecimal carbohydrate,
                                       BigDecimal fat, BigDecimal fiber, BigDecimal sodium,
                                       NutritionTargetConfig.GroupNutritionTarget target, boolean noMissingMaterials) {
        if (!noMissingMaterials) {
            return "warn";
        }
        boolean passed = isWithinRange(calories, target.getCalories(), new BigDecimal("0.80"), new BigDecimal("1.20"))
                && isWithinRange(protein, target.getProtein(), new BigDecimal("0.80"), new BigDecimal("1.20"))
                && isWithinRange(carbohydrate, target.getCarbohydrate(), new BigDecimal("0.80"), new BigDecimal("1.20"))
                && isWithinRange(fat, target.getFat(), new BigDecimal("0.80"), new BigDecimal("1.20"))
                && meetsMinimum(fiber, target.getFiber(), new BigDecimal("0.70"))
                && meetsMaximum(sodium, target.getSodium(), BigDecimal.ONE)
                && macroRatioPass(protein, carbohydrate, fat, target.getProteinRange(), false)
                && macroRatioPass(protein, carbohydrate, fat, target.getCarbRange(), false, "carb")
                && macroRatioPass(protein, carbohydrate, fat, target.getFatRange(), true);
        return passed ? "pass" : "warn";
    }

    private boolean isWithinRange(BigDecimal actual, BigDecimal target, BigDecimal lowerFactor, BigDecimal upperFactor) {
        if (actual == null || target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal lower = target.multiply(lowerFactor);
        BigDecimal upper = target.multiply(upperFactor);
        return actual.compareTo(lower) >= 0 && actual.compareTo(upper) <= 0;
    }

    private boolean meetsMinimum(BigDecimal actual, BigDecimal target, BigDecimal factor) {
        if (actual == null || target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return actual.compareTo(target.multiply(factor)) >= 0;
    }

    private boolean meetsMaximum(BigDecimal actual, BigDecimal target, BigDecimal factor) {
        if (actual == null || target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return actual.compareTo(target.multiply(factor)) <= 0;
    }

    private int scoreRangeMetric(BigDecimal actual, BigDecimal target, BigDecimal lowerFactor, BigDecimal upperFactor, int weight) {
        if (actual == null || target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        BigDecimal ratio = actual.divide(target, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(lowerFactor) >= 0 && ratio.compareTo(upperFactor) <= 0) {
            return weight;
        }
        if (ratio.compareTo(new BigDecimal("0.60")) >= 0 && ratio.compareTo(new BigDecimal("1.40")) <= 0) {
            return Math.max(weight / 2, 1);
        }
        return 0;
    }

    private int scoreMinimumMetric(BigDecimal actual, BigDecimal target, BigDecimal factor, int weight) {
        if (actual == null || target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        BigDecimal ratio = actual.divide(target, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(factor) >= 0) {
            return weight;
        }
        if (ratio.compareTo(new BigDecimal("0.50")) >= 0) {
            return Math.max(weight / 2, 1);
        }
        return 0;
    }

    private int scoreMaximumMetric(BigDecimal actual, BigDecimal target, BigDecimal factor, int weight) {
        if (actual == null || target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        BigDecimal ratio = actual.divide(target, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(factor) <= 0) {
            return weight;
        }
        if (ratio.compareTo(new BigDecimal("1.20")) <= 0) {
            return Math.max(weight / 2, 1);
        }
        return 0;
    }

    private int scoreMacroRatio(BigDecimal actualRatio, String configuredRange, int weight) {
        if (actualRatio == null || configuredRange == null || configuredRange.isBlank()) {
            return 0;
        }
        BigDecimal[] range = parseRange(configuredRange);
        if (range == null) {
            return 0;
        }
        if (actualRatio.compareTo(range[0]) >= 0 && actualRatio.compareTo(range[1]) <= 0) {
            return weight;
        }
        BigDecimal relaxedLower = range[0].subtract(new BigDecimal("5"));
        BigDecimal relaxedUpper = range[1].add(new BigDecimal("5"));
        if (actualRatio.compareTo(relaxedLower) >= 0 && actualRatio.compareTo(relaxedUpper) <= 0) {
            return Math.max(weight / 2, 1);
        }
        return 0;
    }

    private boolean macroRatioPass(BigDecimal protein, BigDecimal carbohydrate, BigDecimal fat, String configuredRange, boolean fatMetric) {
        return macroRatioPass(protein, carbohydrate, fat, configuredRange, fatMetric, "protein");
    }

    private boolean macroRatioPass(BigDecimal protein, BigDecimal carbohydrate, BigDecimal fat, String configuredRange, boolean fatMetric, String metric) {
        BigDecimal actualRatio;
        if (fatMetric) {
            actualRatio = macroRatio(protein, carbohydrate, fat, fat, true);
        } else if ("carb".equals(metric)) {
            actualRatio = macroRatio(protein, carbohydrate, fat, carbohydrate);
        } else {
            actualRatio = macroRatio(protein, carbohydrate, fat, protein);
        }
        BigDecimal[] range = parseRange(configuredRange);
        if (range == null) {
            return false;
        }
        return actualRatio.compareTo(range[0]) >= 0 && actualRatio.compareTo(range[1]) <= 0;
    }

    private BigDecimal macroRatio(BigDecimal protein, BigDecimal carbohydrate, BigDecimal fat, BigDecimal metric) {
        return macroRatio(protein, carbohydrate, fat, metric, false);
    }

    private BigDecimal macroRatio(BigDecimal protein, BigDecimal carbohydrate, BigDecimal fat, BigDecimal metric, boolean fatMetric) {
        BigDecimal proteinCalories = value(protein).multiply(new BigDecimal("4"));
        BigDecimal carbCalories = value(carbohydrate).multiply(new BigDecimal("4"));
        BigDecimal fatCalories = value(fat).multiply(new BigDecimal("9"));
        BigDecimal totalCalories = proteinCalories.add(carbCalories).add(fatCalories);
        if (totalCalories.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal metricCalories = fatMetric ? value(metric).multiply(new BigDecimal("9")) : value(metric).multiply(new BigDecimal("4"));
        return metricCalories.divide(totalCalories, 4, RoundingMode.HALF_UP).multiply(HUNDRED);
    }

    private BigDecimal[] parseRange(String configuredRange) {
        String[] parts = configuredRange.split("-");
        if (parts.length != 2) {
            return null;
        }
        return new BigDecimal[] {
                new BigDecimal(parts[0].trim()),
                new BigDecimal(parts[1].trim())
        };
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(Object raw) {
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(raw));
    }

    private BigDecimal estimateIngredientPrice(String materialName) {
        if (materialName == null) return new BigDecimal("15.00");
        if (materialName.contains("牛肉")) return new BigDecimal("45.00");
        if (materialName.contains("羊肉")) return new BigDecimal("50.00");
        if (materialName.contains("猪肉") || materialName.contains("五花肉")) return new BigDecimal("28.00");
        if (materialName.contains("排骨")) return new BigDecimal("35.00");
        if (materialName.contains("鸡肉") || materialName.contains("鸡胸") || materialName.contains("鸡腿")) return new BigDecimal("18.00");
        if (materialName.contains("鸭肉")) return new BigDecimal("20.00");
        if (materialName.contains("龙虾")) return new BigDecimal("120.00");
        if (materialName.contains("虾") || materialName.contains("虾仁")) return new BigDecimal("55.00");
        if (materialName.contains("鲍鱼")) return new BigDecimal("80.00");
        if (materialName.contains("鱼")) return new BigDecimal("35.00");
        if (materialName.contains("蟹")) return new BigDecimal("65.00");
        if (materialName.contains("贝") || materialName.contains("蛤")) return new BigDecimal("25.00");
        if (materialName.contains("西兰花")) return new BigDecimal("8.00");
        if (materialName.contains("芦笋")) return new BigDecimal("15.00");
        if (materialName.contains("菌") || materialName.contains("菇") || materialName.contains("木耳")) return new BigDecimal("12.00");
        if (materialName.contains("菜") || materialName.contains("蔬")) return new BigDecimal("5.00");
        if (materialName.contains("椒")) return new BigDecimal("6.00");
        if (materialName.contains("葱") || materialName.contains("姜") || materialName.contains("蒜")) return new BigDecimal("3.00");
        if (materialName.contains("蛋")) return new BigDecimal("1.50");
        if (materialName.contains("豆腐") || materialName.contains("豆")) return new BigDecimal("4.00");
        if (materialName.contains("酱油") || materialName.contains("醋") || materialName.contains("料酒")) return new BigDecimal("2.00");
        return new BigDecimal("15.00");
    }
}
