package com.xykj.recipe.service.impl;

import com.xykj.recipe.config.NutritionTargetConfig;
import com.xykj.recipe.dto.NutritionAnalysisDTO;
import com.xykj.recipe.entity.Recipe;
import com.xykj.recipe.mapper.RecipeMapper;
import com.xykj.recipe.service.NutritionAnalysisService;
import com.xykj.recipe.service.RecipeNutritionSupportService;
import com.xykj.recipe.vo.AINutritionAssessmentVO;
import com.xykj.recipe.vo.NutritionTargetVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 营养分析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionAnalysisServiceImpl implements NutritionAnalysisService {

    private final RecipeMapper recipeMapper;
    private final NutritionTargetConfig nutritionTargetConfig;
    private final RecipeNutritionSupportService recipeNutritionSupportService;

    private static final Map<String, String> TARGET_GROUP_NAMES = new HashMap<>();
    private static final Map<String, String> HEALTH_STATUS_NAMES = new HashMap<>();

    static {
        TARGET_GROUP_NAMES.put("adult", "普通成人");
        TARGET_GROUP_NAMES.put("elderly", "老年人");
        TARGET_GROUP_NAMES.put("child", "儿童");
        TARGET_GROUP_NAMES.put("teenager", "青少年");
        TARGET_GROUP_NAMES.put("patient", "病人");
        TARGET_GROUP_NAMES.put("worker", "体力劳动者");

        HEALTH_STATUS_NAMES.put("diabetes", "糖尿病");
        HEALTH_STATUS_NAMES.put("hypertension", "高血压");
        HEALTH_STATUS_NAMES.put("hyperlipidemia", "高血脂");
        HEALTH_STATUS_NAMES.put("obesity", "肥胖");
        HEALTH_STATUS_NAMES.put("gout", "痛风");
        HEALTH_STATUS_NAMES.put("kidney_disease", "肾病");
        HEALTH_STATUS_NAMES.put("stomach_disease", "胃病");
        HEALTH_STATUS_NAMES.put("anemia", "贫血");
    }

    @Override
    public AINutritionAssessmentVO analyzeNutrition(NutritionAnalysisDTO dto) {
        List<Recipe> recipes = new ArrayList<>();
        for (NutritionAnalysisDTO.RecipeNutritionInput input : dto.getRecipes()) {
            Recipe recipe = recipeMapper.selectById(input.getRecipeId());
            if (recipe != null) {
                recipeNutritionSupportService.recalculateRecipeNutrition(recipe);
                recipes.add(recipe);
            }
        }

        if (recipes.isEmpty()) {
            return buildEmptyAssessment();
        }

        BigDecimal totalCalories = BigDecimal.ZERO;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalCarbohydrate = BigDecimal.ZERO;
        BigDecimal totalFat = BigDecimal.ZERO;
        BigDecimal totalSodium = BigDecimal.ZERO;
        BigDecimal totalFiber = BigDecimal.ZERO;
        int totalServings = 0;

        for (int i = 0; i < recipes.size(); i++) {
            Recipe recipe = recipes.get(i);
            Integer servings = dto.getRecipes().get(i).getServings();
            if (servings == null) servings = 1;

            totalServings += servings;
            totalCalories = addNutrient(totalCalories, recipe.getCalories(), servings);
            totalProtein = addNutrient(totalProtein, recipe.getProtein(), servings);
            totalCarbohydrate = addNutrient(totalCarbohydrate, recipe.getCarbohydrate(), servings);
            totalFat = addNutrient(totalFat, recipe.getFat(), servings);
            totalSodium = addNutrient(totalSodium, recipe.getSodium(), servings);
            totalFiber = addNutrient(totalFiber, recipe.getFiber(), servings);
        }

        String targetGroup = dto.getTargetGroup() != null ? dto.getTargetGroup() : "adult";
        NutritionTargetConfig.GroupNutritionTarget mealTarget = nutritionTargetConfig.getPerMealTarget(targetGroup);

        if (dto.getHealthStatus() != null && !dto.getHealthStatus().isEmpty()) {
            mealTarget = nutritionTargetConfig.adjustForHealthStatus(mealTarget, dto.getHealthStatus());
        }

        int servingCount = dto.getServingCount() != null ? dto.getServingCount() : 1;
        BigDecimal avgCalories = safeDivide(totalCalories, servingCount);
        BigDecimal avgProtein = safeDivide(totalProtein, servingCount);
        BigDecimal avgCarbohydrate = safeDivide(totalCarbohydrate, servingCount);
        BigDecimal avgFat = safeDivide(totalFat, servingCount);
        BigDecimal avgFiber = safeDivide(totalFiber, servingCount);

        BigDecimal proteinCalories = totalProtein.multiply(BigDecimal.valueOf(4));
        BigDecimal carbCalories = totalCarbohydrate.multiply(BigDecimal.valueOf(4));
        BigDecimal fatCalories = totalFat.multiply(BigDecimal.valueOf(9));
        BigDecimal totalMacroCalories = proteinCalories.add(carbCalories).add(fatCalories);

        BigDecimal proteinRatio = BigDecimal.ZERO;
        BigDecimal carbRatio = BigDecimal.ZERO;
        BigDecimal fatRatio = BigDecimal.ZERO;

        if (totalMacroCalories.compareTo(BigDecimal.ZERO) > 0) {
            proteinRatio = proteinCalories.divide(totalMacroCalories, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            carbRatio = carbCalories.divide(totalMacroCalories, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            fatRatio = fatCalories.divide(totalMacroCalories, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        int score = calculateNutritionScore(avgProtein, avgCarbohydrate, avgFat, avgCalories, mealTarget);
        BigDecimal passRate = calculatePassRate(avgProtein, avgCarbohydrate, avgFat, avgFiber, mealTarget);
        String grade = getGrade(score);
        String assessment = generateAssessmentText(score);
        List<String> suggestions = generateSuggestions(avgProtein, avgCarbohydrate, avgFat, avgCalories, avgFiber, mealTarget);
        List<AINutritionAssessmentVO.NutritionComparison> comparisons = buildNutritionComparisons(
                avgProtein, avgCarbohydrate, avgFat, avgCalories, avgFiber, mealTarget);

        AINutritionAssessmentVO.DietStructureAnalysis dietStructure = AINutritionAssessmentVO.DietStructureAnalysis.builder()
                .proteinRatio(proteinRatio)
                .carbohydrateRatio(carbRatio)
                .fatRatio(fatRatio)
                .evaluation(generateDietStructureEvaluation(proteinRatio, carbRatio, fatRatio))
                .build();

        return AINutritionAssessmentVO.builder()
                .overallScore(score)
                .grade(grade)
                .assessment(assessment)
                .totalCalories(totalCalories)
                .totalProtein(totalProtein)
                .totalCarbohydrate(totalCarbohydrate)
                .totalFat(totalFat)
                .totalSodium(totalSodium)
                .totalFiber(totalFiber)
                .avgCalories(avgCalories)
                .avgProtein(avgProtein)
                .avgCarbohydrate(avgCarbohydrate)
                .avgFat(avgFat)
                .passRate(passRate)
                .suggestions(suggestions)
                .dietStructure(dietStructure)
                .nutritionComparisons(comparisons)
                .servingCount(servingCount)
                .build();
    }

    private BigDecimal addNutrient(BigDecimal total, BigDecimal nutrient, Integer servings) {
        if (nutrient == null) return total;
        return total.add(nutrient.multiply(BigDecimal.valueOf(servings)));
    }

    private BigDecimal safeDivide(BigDecimal value, int divisor) {
        if (divisor <= 0) return BigDecimal.ZERO;
        return value.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private int calculateNutritionScore(BigDecimal avgProtein, BigDecimal avgCarb, BigDecimal avgFat,
                                        BigDecimal avgCalories, NutritionTargetConfig.GroupNutritionTarget target) {
        int score = 100;

        if (avgProtein.compareTo(target.getProtein().multiply(BigDecimal.valueOf(0.8))) < 0) score -= 15;
        else if (avgProtein.compareTo(target.getProtein().multiply(BigDecimal.valueOf(1.5))) > 0) score -= 10;

        if (avgCarb.compareTo(target.getCarbohydrate().multiply(BigDecimal.valueOf(0.7))) < 0) score -= 15;
        else if (avgCarb.compareTo(target.getCarbohydrate().multiply(BigDecimal.valueOf(1.5))) > 0) score -= 10;

        if (avgFat.compareTo(target.getFat().multiply(BigDecimal.valueOf(0.7))) < 0) score -= 10;
        else if (avgFat.compareTo(target.getFat().multiply(BigDecimal.valueOf(1.5))) > 0) score -= 15;

        if (avgCalories.compareTo(BigDecimal.valueOf(400)) < 0) score -= 10;
        else if (avgCalories.compareTo(BigDecimal.valueOf(1200)) > 0) score -= 5;

        return Math.max(0, score);
    }

    private BigDecimal calculatePassRate(BigDecimal avgProtein, BigDecimal avgCarb, BigDecimal avgFat,
                                          BigDecimal avgFiber, NutritionTargetConfig.GroupNutritionTarget target) {
        int passCount = 0;
        int totalItems = 4;

        if (avgProtein.compareTo(target.getProtein().multiply(BigDecimal.valueOf(0.8))) >= 0) passCount++;
        if (avgCarb.compareTo(target.getCarbohydrate().multiply(BigDecimal.valueOf(0.8))) >= 0) passCount++;
        if (avgFat.compareTo(target.getFat().multiply(BigDecimal.valueOf(0.7))) >= 0) passCount++;
        if (avgFiber.compareTo(target.getFiber().multiply(BigDecimal.valueOf(0.7))) >= 0) passCount++;

        return BigDecimal.valueOf(passCount * 100.0 / totalItems).setScale(1, RoundingMode.HALF_UP);
    }

    private String getGrade(int score) {
        if (score >= 85) return "优秀";
        if (score >= 70) return "良好";
        if (score >= 60) return "达标";
        return "需改进";
    }

    private String generateAssessmentText(int score) {
        if (score >= 85) return "营养搭配优秀，各营养素配比合理，满足健康饮食要求。";
        if (score >= 70) return "营养搭配良好，大部分营养素达标，建议适当调整。";
        if (score >= 60) return "营养搭配基本达标，部分营养素可优化。";
        return "营养搭配有待改进，建议参考优化建议调整菜谱。";
    }

    private List<String> generateSuggestions(BigDecimal avgProtein, BigDecimal avgCarb, BigDecimal avgFat,
                                              BigDecimal avgCalories, BigDecimal avgFiber,
                                              NutritionTargetConfig.GroupNutritionTarget target) {
        List<String> suggestions = new ArrayList<>();

        if (avgProtein.compareTo(target.getProtein().multiply(BigDecimal.valueOf(0.8))) < 0) {
            suggestions.add("蛋白质摄入不足，建议增加肉类、鱼类、蛋类或豆制品");
        } else if (avgProtein.compareTo(target.getProtein().multiply(BigDecimal.valueOf(1.5))) > 0) {
            suggestions.add("蛋白质摄入充足，继续保持优质蛋白摄入");
        }

        if (avgCarb.compareTo(target.getCarbohydrate().multiply(BigDecimal.valueOf(0.8))) < 0) {
            suggestions.add("碳水化合物摄入不足，建议增加主食类食材");
        }

        if (avgFat.compareTo(target.getFat().multiply(BigDecimal.valueOf(1.5))) > 0) {
            suggestions.add("脂肪摄入偏高，建议减少油炸或高脂肪食材");
        }

        if (avgCalories.compareTo(BigDecimal.valueOf(400)) < 0) {
            suggestions.add("人均热量偏低，建议增加菜量或高能量食材");
        } else if (avgCalories.compareTo(BigDecimal.valueOf(1200)) > 0) {
            suggestions.add("人均热量偏高，建议适当减少份量");
        }

        if (avgFiber.compareTo(target.getFiber().multiply(BigDecimal.valueOf(0.7))) < 0) {
            suggestions.add("膳食纤维摄入不足，建议增加蔬菜、水果和全谷物");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("营养搭配均衡，建议保持当前菜谱组合");
            suggestions.add("各项指标达标，继续注意均衡饮食");
        }

        return suggestions;
    }

    private List<AINutritionAssessmentVO.NutritionComparison> buildNutritionComparisons(
            BigDecimal avgProtein, BigDecimal avgCarb, BigDecimal avgFat,
            BigDecimal avgCalories, BigDecimal avgFiber,
            NutritionTargetConfig.GroupNutritionTarget target) {
        List<AINutritionAssessmentVO.NutritionComparison> comparisons = new ArrayList<>();

        comparisons.add(buildComparison("热量", avgCalories, target.getCalories()));
        comparisons.add(buildComparison("蛋白质", avgProtein, target.getProtein()));
        comparisons.add(buildComparison("碳水化合物", avgCarb, target.getCarbohydrate()));
        comparisons.add(buildComparison("脂肪", avgFat, target.getFat()));
        comparisons.add(buildComparison("膳食纤维", avgFiber, target.getFiber()));

        return comparisons;
    }

    private AINutritionAssessmentVO.NutritionComparison buildComparison(String name, BigDecimal actual, BigDecimal target) {
        BigDecimal percentage = target.compareTo(BigDecimal.ZERO) > 0
                ? actual.divide(target, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String status;
        if (percentage.compareTo(BigDecimal.valueOf(80)) >= 0 && percentage.compareTo(BigDecimal.valueOf(120)) <= 0) {
            status = "达标";
        } else if (percentage.compareTo(BigDecimal.valueOf(80)) < 0) {
            status = "不足";
        } else {
            status = "过量";
        }

        return AINutritionAssessmentVO.NutritionComparison.builder()
                .nutrientName(name)
                .actualValue(actual.setScale(1, RoundingMode.HALF_UP))
                .targetValue(target.setScale(1, RoundingMode.HALF_UP))
                .status(status)
                .percentage(percentage)
                .build();
    }

    private String generateDietStructureEvaluation(BigDecimal proteinRatio, BigDecimal carbRatio, BigDecimal fatRatio) {
        boolean proteinOk = proteinRatio.compareTo(BigDecimal.valueOf(12)) >= 0 && proteinRatio.compareTo(BigDecimal.valueOf(25)) <= 0;
        boolean carbOk = carbRatio.compareTo(BigDecimal.valueOf(45)) >= 0 && carbRatio.compareTo(BigDecimal.valueOf(65)) <= 0;
        boolean fatOk = fatRatio.compareTo(BigDecimal.valueOf(18)) >= 0 && fatRatio.compareTo(BigDecimal.valueOf(35)) <= 0;

        if (proteinOk && carbOk && fatOk) {
            return "饮食结构均衡，三大营养素配比合理";
        }

        StringBuilder sb = new StringBuilder();
        if (!proteinOk) sb.append(proteinRatio.compareTo(BigDecimal.valueOf(12)) < 0 ? "蛋白质占比偏低；" : "蛋白质占比偏高；");
        if (!carbOk) sb.append(carbRatio.compareTo(BigDecimal.valueOf(45)) < 0 ? "碳水化合物占比偏低；" : "碳水化合物占比偏高；");
        if (!fatOk) sb.append(fatRatio.compareTo(BigDecimal.valueOf(18)) < 0 ? "脂肪占比偏低；" : "脂肪占比偏高；");

        return sb.length() > 0 ? sb.toString() : "饮食结构基本合理";
    }

    private AINutritionAssessmentVO buildEmptyAssessment() {
        return AINutritionAssessmentVO.builder()
                .overallScore(0)
                .grade("无数据")
                .assessment("暂无菜谱数据")
                .totalCalories(BigDecimal.ZERO)
                .totalProtein(BigDecimal.ZERO)
                .totalCarbohydrate(BigDecimal.ZERO)
                .totalFat(BigDecimal.ZERO)
                .totalSodium(BigDecimal.ZERO)
                .totalFiber(BigDecimal.ZERO)
                .avgCalories(BigDecimal.ZERO)
                .avgProtein(BigDecimal.ZERO)
                .avgCarbohydrate(BigDecimal.ZERO)
                .avgFat(BigDecimal.ZERO)
                .passRate(BigDecimal.ZERO)
                .suggestions(List.of("请先添加菜谱"))
                .nutritionComparisons(new ArrayList<>())
                .servingCount(0)
                .build();
    }

    @Override
    public NutritionTargetVO getNutritionTargets(String targetGroup, String healthStatus) {
        NutritionTargetConfig.GroupNutritionTarget dailyTarget = nutritionTargetConfig.getDailyTarget(targetGroup);
        NutritionTargetConfig.GroupNutritionTarget perMealTarget = nutritionTargetConfig.getPerMealTarget(targetGroup);

        List<NutritionTargetVO.HealthAdjustment> adjustments = new ArrayList<>();
        if (healthStatus != null && !healthStatus.isEmpty()) {
            perMealTarget = nutritionTargetConfig.adjustForHealthStatus(perMealTarget, healthStatus);

            String[] statuses = healthStatus.split(",");
            for (String status : statuses) {
                String trimmedStatus = status.trim();
                String statusName = HEALTH_STATUS_NAMES.getOrDefault(trimmedStatus, trimmedStatus);
                adjustments.add(NutritionTargetVO.HealthAdjustment.builder()
                        .healthStatus(trimmedStatus)
                        .healthStatusName(statusName)
                        .adjustment(getHealthAdjustmentDescription(trimmedStatus))
                        .restrictions(getHealthRestrictions(trimmedStatus))
                        .build());
            }
        }

        return NutritionTargetVO.builder()
                .targetGroup(targetGroup)
                .groupName(TARGET_GROUP_NAMES.getOrDefault(targetGroup, targetGroup))
                .dailyTarget(buildDailyTarget(dailyTarget))
                .perMealTarget(buildDailyTarget(perMealTarget))
                .healthAdjustments(adjustments)
                .build();
    }

    private NutritionTargetVO.DailyTarget buildDailyTarget(NutritionTargetConfig.GroupNutritionTarget target) {
        return NutritionTargetVO.DailyTarget.builder()
                .calories(target.getCalories())
                .protein(target.getProtein())
                .carbohydrate(target.getCarbohydrate())
                .fat(target.getFat())
                .sodium(target.getSodium())
                .fiber(target.getFiber())
                .build();
    }

    private String getHealthAdjustmentDescription(String healthStatus) {
        switch (healthStatus) {
            case "diabetes": return "需要低糖、低GI饮食，控制碳水化合物摄入";
            case "hypertension": return "需要低盐、低脂饮食，控制钠摄入";
            case "hyperlipidemia": return "需要低脂、低胆固醇饮食";
            case "obesity": return "需要低热量、高纤维饮食，控制总能量";
            case "gout": return "需要低嘌呤饮食，限制高嘌呤食材";
            case "kidney_disease": return "需要低蛋白、低盐饮食";
            case "stomach_disease": return "需要易消化、温和饮食";
            case "anemia": return "需要高铁、高蛋白饮食";
            default: return "需要特殊饮食照护";
        }
    }

    private List<String> getHealthRestrictions(String healthStatus) {
        switch (healthStatus) {
            case "diabetes": return List.of("限制高糖食材", "避免高GI主食", "减少精制糖");
            case "hypertension": return List.of("限制高钠食材", "减少腌制食品", "控制盐量");
            case "hyperlipidemia": return List.of("限制高脂肪食材", "减少油炸食品", "控制胆固醇");
            case "obesity": return List.of("限制高热量食材", "减少油炸食品", "控制份量");
            case "gout": return List.of("限制高嘌呤食材", "避免动物内脏", "减少海鲜");
            case "kidney_disease": return List.of("限制高蛋白食材", "控制盐分", "减少高钾食物");
            case "stomach_disease": return List.of("避免刺激性食物", "选择易消化食材", "避免生冷");
            case "anemia": return List.of("增加富铁食材", "补充维生素C促进铁吸收", "避免浓茶");
            default: return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getTargetGroups() {
        List<Map<String, Object>> groups = new ArrayList<>();
        TARGET_GROUP_NAMES.forEach((key, value) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("value", key);
            item.put("label", value);
            groups.add(item);
        });
        return groups;
    }

    @Override
    public List<Map<String, Object>> getHealthStatuses() {
        List<Map<String, Object>> statuses = new ArrayList<>();
        HEALTH_STATUS_NAMES.forEach((key, value) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("value", key);
            item.put("label", value);
            statuses.add(item);
        });
        return statuses;
    }
}
