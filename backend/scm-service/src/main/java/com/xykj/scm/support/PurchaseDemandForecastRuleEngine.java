package com.xykj.scm.support;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 采购需求预测首版规则引擎。
 */
@Component
public class PurchaseDemandForecastRuleEngine {

    public static final String MODEL_RECIPE_FORMULA = "菜谱修正模型";
    public static final String MODEL_DOUBLE_EXP = "双指数平滑";
    public static final String MODEL_VOLATILITY = "轻量XGBoost波动模型";
    public static final String MODEL_VOLATILITY_FALLBACK = "结构化波动回退模型";
    public static final String MODEL_CROSTON = "Croston间歇模型";
    public static final String MODEL_LONG_CYCLE = "长周期补货模型";
    public static final String SEGMENT_RECIPE_DRIVEN = "菜谱强驱动型";
    public static final String SEGMENT_STABLE = "高频稳定型";
    public static final String SEGMENT_VOLATILE = "波动型";
    public static final String SEGMENT_INTERMITTENT = "低频间歇型";
    public static final String SEGMENT_LONG_CYCLE = "长周期补货型";
    public static final String SEGMENT_PERISHABLE = "易腐型";
    public static final String PRIORITY_URGENT = "紧急补货";
    public static final String PRIORITY_HIGH = "优先补货";
    public static final String PRIORITY_NORMAL = "正常补货";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP);
    private static final BigDecimal RECIPE_CORRECTION_MIN = BigDecimal.valueOf(0.80d);
    private static final BigDecimal RECIPE_CORRECTION_MAX = BigDecimal.valueOf(1.20d);
    private static final BigDecimal DEFAULT_LOSS_LIMIT_FACTOR = BigDecimal.valueOf(3L);
    private static final BigDecimal CONFIDENCE_Z80 = BigDecimal.valueOf(1.28d);
    private static final BigDecimal CONFIDENCE_LEVEL = BigDecimal.valueOf(80.0d);
    private static final BigDecimal EPSILON = BigDecimal.valueOf(0.001d);
    private static final BigDecimal DEFAULT_SERVICE_LEVEL = BigDecimal.valueOf(0.90d);

    public RuleResult evaluate(MaterialRuleContext context) {
        return evaluate(context, resolveDefaultParameters(context));
    }

    public RuleResult evaluate(MaterialRuleContext context, ModelParameters parameters) {
        ModelParameters effectiveParameters = parameters == null ? resolveDefaultParameters(context) : parameters;
        String materialSegment = normalizeOptional(effectiveParameters.materialSegment());
        if (materialSegment.isEmpty()) {
            materialSegment = resolveMaterialSegment(context);
        }
        String modelType = normalizeOptional(effectiveParameters.modelType());
        if (modelType.isEmpty()) {
            modelType = resolveDefaultModelType(materialSegment);
        }

        BigDecimal avg7 = scale(context.avgConsumption7d());
        BigDecimal avg14 = scale(context.avgConsumption14d());
        BigDecimal avg30 = scale(context.avgConsumption30d());
        BigDecimal trendDaily = scale(
                avg7.multiply(BigDecimal.valueOf(0.5d))
                        .add(avg14.multiply(BigDecimal.valueOf(0.3d)))
                        .add(avg30.multiply(BigDecimal.valueOf(0.2d)))
        );
        BigDecimal trendDemand = scale(trendDaily.multiply(BigDecimal.valueOf(Math.max(1, context.forecastDays()))));
        BigDecimal correctionFactor = resolveRecipeCorrectionFactor(
                context.actualConsumption30d(),
                context.recipeHistory30d(),
                effectiveParameters.recipeCorrectionMin(),
                effectiveParameters.recipeCorrectionMax()
        );
        BigDecimal adjustedRecipeDemand = scale(scale(context.recipeDemand7d()).multiply(correctionFactor));
        BigDecimal combinedFactor = resolveCombinedFactor(context, effectiveParameters);
        BigDecimal leadTimeDays = BigDecimal.valueOf(Math.max(1, defaultInt(effectiveParameters.leadTimeDays(), context.leadTimeDays(), 3)));

        BigDecimal baseForecastDemand;
        String resolvedModelType = modelType;
        switch (materialSegment) {
            case SEGMENT_RECIPE_DRIVEN ->
                    baseForecastDemand = resolveRecipeDrivenDemand(trendDemand, adjustedRecipeDemand, effectiveParameters.alphaValue());
            case SEGMENT_STABLE, SEGMENT_PERISHABLE ->
                    baseForecastDemand = resolveStableDemand(context.recentConsumptionSeries30d(), trendDemand, context.forecastDays(), effectiveParameters.alphaValue(), effectiveParameters.betaValue());
            case SEGMENT_VOLATILE -> {
                VolatileDemandResult volatileDemandResult = resolveVolatileDemand(context, trendDemand, adjustedRecipeDemand, effectiveParameters.modelWeight());
                baseForecastDemand = volatileDemandResult.demandQty();
                resolvedModelType = volatileDemandResult.modelType();
            }
            case SEGMENT_INTERMITTENT ->
                    baseForecastDemand = resolveIntermittentDemand(context.recentConsumptionSeries30d(), trendDemand, context.forecastDays(), effectiveParameters.alphaValue());
            case SEGMENT_LONG_CYCLE ->
                    baseForecastDemand = resolveLongCycleDemand(avg30, context.forecastDays(), leadTimeDays);
            default -> baseForecastDemand = max(adjustedRecipeDemand, trendDemand);
        }
        BigDecimal forecastDemand = scale(baseForecastDemand.multiply(combinedFactor));

        boolean perishable = isPerishable(context.materialCategory(), context.shelfLifeDays()) || SEGMENT_PERISHABLE.equals(materialSegment);
        int safetyDays = resolveSafetyDays(materialSegment, perishable, context.warningDays(), effectiveParameters.safetyDays());
        BigDecimal safetyStock = resolveSafetyStock(context, effectiveParameters, avg7, safetyDays);

        BigDecimal theoreticalSuggested = scale(
                forecastDemand
                        .add(safetyStock)
                        .subtract(scale(context.availableStockQty()))
                        .subtract(scale(context.inTransitQty()))
                        .subtract(scale(context.pendingPlanQty()))
        );
        BigDecimal suggestedQty = max(theoreticalSuggested, ZERO);

        String limitNote = "";
        int maxCoverageDays = resolveMaxCoverageDays(materialSegment, perishable, context.shelfLifeDays(), effectiveParameters.maxCoverageDays());
        if (avg7.compareTo(ZERO) > 0 && maxCoverageDays > 0) {
            BigDecimal maxCoverStock = avg7.multiply(BigDecimal.valueOf(maxCoverageDays));
            BigDecimal maxSuggested = max(maxCoverStock.subtract(scale(context.availableStockQty())), ZERO);
            if (suggestedQty.compareTo(maxSuggested) > 0) {
                suggestedQty = scale(maxSuggested);
                limitNote = String.format(Locale.ROOT, "，已按最大覆盖%s天限量", maxCoverageDays);
            }
        }
        if (scale(context.maxStock()).compareTo(ZERO) > 0) {
            BigDecimal maxSuggested = max(scale(context.maxStock()).subtract(scale(context.currentStockQty())), ZERO);
            if (suggestedQty.compareTo(maxSuggested) > 0) {
                suggestedQty = scale(maxSuggested);
                if (limitNote.isEmpty()) {
                    limitNote = "，已按最高库存上限限量";
                }
            }
        }

        BigDecimal sigma = resolveSigma(scale(context.stdConsumption30d()), context.forecastDays(), forecastDemand);
        BigDecimal lower = max(scale(suggestedQty.subtract(CONFIDENCE_Z80.multiply(sigma))), ZERO);
        BigDecimal upper = scale(suggestedQty.add(CONFIDENCE_Z80.multiply(sigma)));
        BigDecimal coverageDays = resolveCoverageDays(scale(context.availableStockQty()), avg7);
        String priority = resolvePriority(coverageDays, suggestedQty, scale(context.availableStockQty()));

        String basis = String.format(
                Locale.ROOT,
                "分层=%s，模型=%s，近7/14/30日均耗=%s/%s/%s，未来7天菜谱需求=%s，菜谱修正系数=%s，预测需求=%s，安全库存=%s，服务水平=%s，提前期=%s天，在途=%s，待执行计划=%s，当前可用库存=%s，建议采购=%s%s。",
                materialSegment,
                resolvedModelType,
                format(avg7),
                format(avg14),
                format(avg30),
                format(context.recipeDemand7d()),
                format(correctionFactor),
                format(forecastDemand),
                format(safetyStock),
                format(effectiveParameters.serviceLevel()),
                format(BigDecimal.valueOf(Math.max(1, defaultInt(effectiveParameters.leadTimeDays(), context.leadTimeDays(), 3)))),
                format(context.inTransitQty()),
                format(context.pendingPlanQty()),
                format(context.availableStockQty()),
                format(suggestedQty),
                limitNote
        );

        return new RuleResult(
                resolvedModelType,
                materialSegment,
                perishable,
                scale(correctionFactor),
                trendDaily,
                trendDemand,
                forecastDemand,
                safetyStock,
                scale(suggestedQty),
                lower,
                upper,
                CONFIDENCE_LEVEL.setScale(1, RoundingMode.HALF_UP),
                priority,
                basis,
                coverageDays,
                BigDecimal.valueOf(defaultInt(effectiveParameters.leadTimeDays(), context.leadTimeDays(), 3)).setScale(0, RoundingMode.HALF_UP),
                scaleFactor(defaultBigDecimal(effectiveParameters.serviceLevel(), context.serviceLevel(), DEFAULT_SERVICE_LEVEL)),
                BigDecimal.valueOf(safetyDays).setScale(0, RoundingMode.HALF_UP)
        );
    }

    public ModelParameters resolveDefaultParameters(MaterialRuleContext context) {
        String materialSegment = resolveMaterialSegment(context);
        String modelType = resolveDefaultModelType(materialSegment);
        boolean perishable = isPerishable(context.materialCategory(), context.shelfLifeDays()) || SEGMENT_PERISHABLE.equals(materialSegment);
        BigDecimal serviceLevel = switch (materialSegment) {
            case SEGMENT_STABLE -> BigDecimal.valueOf(0.95d);
            case SEGMENT_RECIPE_DRIVEN, SEGMENT_LONG_CYCLE -> BigDecimal.valueOf(0.93d);
            case SEGMENT_VOLATILE -> BigDecimal.valueOf(0.92d);
            case SEGMENT_INTERMITTENT -> BigDecimal.valueOf(0.90d);
            case SEGMENT_PERISHABLE -> BigDecimal.valueOf(0.88d);
            default -> DEFAULT_SERVICE_LEVEL;
        };
        int leadTimeDays = defaultInt(context.leadTimeDays(), null, SEGMENT_LONG_CYCLE.equals(materialSegment) ? 5 : 3);
        int safetyDays = resolveSafetyDays(materialSegment, perishable, context.warningDays(), null);
        int maxCoverageDays = resolveMaxCoverageDays(materialSegment, perishable, context.shelfLifeDays(), null);
        BigDecimal alpha = SEGMENT_RECIPE_DRIVEN.equals(materialSegment) ? BigDecimal.valueOf(0.25d) : BigDecimal.valueOf(0.35d);
        BigDecimal beta = SEGMENT_STABLE.equals(materialSegment) ? BigDecimal.valueOf(0.12d) : BigDecimal.valueOf(0.08d);
        BigDecimal shortagePenalty = switch (materialSegment) {
            case SEGMENT_RECIPE_DRIVEN, SEGMENT_PERISHABLE -> BigDecimal.valueOf(8.0d);
            case SEGMENT_VOLATILE, SEGMENT_LONG_CYCLE -> BigDecimal.valueOf(7.0d);
            case SEGMENT_INTERMITTENT -> BigDecimal.valueOf(5.5d);
            default -> BigDecimal.valueOf(6.0d);
        };
        BigDecimal holdingCostRate = perishable ? BigDecimal.valueOf(0.040d) : BigDecimal.valueOf(0.020d);
        BigDecimal wasteCostRate = perishable ? BigDecimal.valueOf(0.800d) : BigDecimal.valueOf(0.250d);
        return new ModelParameters(
                materialSegment,
                modelType,
                BigDecimal.ONE,
                safetyDays,
                leadTimeDays,
                serviceLevel,
                alpha,
                beta,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                maxCoverageDays,
                RECIPE_CORRECTION_MIN,
                RECIPE_CORRECTION_MAX,
                ZERO,
                ZERO,
                BigDecimal.valueOf(0.70d),
                shortagePenalty,
                holdingCostRate,
                wasteCostRate,
                BigDecimal.valueOf(30.00d),
                0,
                null,
                "system"
        );
    }

    public String resolveMaterialSegment(MaterialRuleContext context) {
        if (isPerishable(context.materialCategory(), context.shelfLifeDays())) {
            return SEGMENT_PERISHABLE;
        }
        if (context.leadTimeDays() != null && context.leadTimeDays() >= 7) {
            return SEGMENT_LONG_CYCLE;
        }
        if (scale(context.recipeDriveRatio()).compareTo(BigDecimal.valueOf(0.6d)) >= 0
                && scale(context.recipeDemand7d()).compareTo(ZERO) > 0) {
            return SEGMENT_RECIPE_DRIVEN;
        }
        if (scale(context.demandActiveRatio()).compareTo(BigDecimal.valueOf(0.8d)) >= 0
                && scale(context.demandCv()).compareTo(BigDecimal.valueOf(0.35d)) < 0) {
            return SEGMENT_STABLE;
        }
        if (scale(context.demandActiveRatio()).compareTo(BigDecimal.valueOf(0.4d)) < 0) {
            return SEGMENT_INTERMITTENT;
        }
        if (scale(context.demandCv()).compareTo(BigDecimal.valueOf(0.35d)) >= 0
                || scale(context.activitySensitivity()).compareTo(BigDecimal.valueOf(1.2d)) >= 0) {
            return SEGMENT_VOLATILE;
        }
        if (isRecipeDrivenCategory(context.materialCategory())) {
            return SEGMENT_RECIPE_DRIVEN;
        }
        return SEGMENT_STABLE;
    }

    public String resolveForecastType(MaterialRuleContext context, BigDecimal avg30) {
        String segment = normalizeOptional(context.materialSegment());
        if (segment.isEmpty()) {
            segment = resolveMaterialSegment(context);
        }
        return resolveDefaultModelType(segment);
    }

    private String resolveDefaultModelType(String materialSegment) {
        return switch (normalizeOptional(materialSegment)) {
            case SEGMENT_RECIPE_DRIVEN -> MODEL_RECIPE_FORMULA;
            case SEGMENT_STABLE, SEGMENT_PERISHABLE -> MODEL_DOUBLE_EXP;
            case SEGMENT_VOLATILE -> MODEL_VOLATILITY;
            case SEGMENT_INTERMITTENT -> MODEL_CROSTON;
            case SEGMENT_LONG_CYCLE -> MODEL_LONG_CYCLE;
            default -> MODEL_DOUBLE_EXP;
        };
    }

    private BigDecimal resolveRecipeCorrectionFactor(
            BigDecimal actualConsumption30d,
            BigDecimal recipeHistory30d,
            BigDecimal lowerBound,
            BigDecimal upperBound
    ) {
        BigDecimal actual = scale(actualConsumption30d);
        BigDecimal recipe = scale(recipeHistory30d);
        if (actual.compareTo(ZERO) <= 0 || recipe.compareTo(ZERO) <= 0) {
            return ONE;
        }
        BigDecimal correction = actual.divide(recipe, 4, RoundingMode.HALF_UP);
        BigDecimal min = lowerBound == null ? RECIPE_CORRECTION_MIN : scale(lowerBound);
        BigDecimal max = upperBound == null ? RECIPE_CORRECTION_MAX : scale(upperBound);
        if (max.compareTo(min) < 0) {
            max = RECIPE_CORRECTION_MAX;
        }
        if (correction.compareTo(min) < 0) {
            return min;
        }
        if (correction.compareTo(max) > 0) {
            return max;
        }
        return correction;
    }

    private BigDecimal resolveRecipeDrivenDemand(BigDecimal trendDemand, BigDecimal adjustedRecipeDemand, BigDecimal alpha) {
        BigDecimal weight = defaultBigDecimal(alpha, null, BigDecimal.valueOf(0.25d));
        BigDecimal blended = scale(trendDemand.multiply(weight)
                .add(adjustedRecipeDemand.multiply(BigDecimal.ONE.subtract(weight))));
        return max(adjustedRecipeDemand, blended);
    }

    private BigDecimal resolveStableDemand(
            List<BigDecimal> series,
            BigDecimal fallbackTrendDemand,
            Integer forecastDays,
            BigDecimal alpha,
            BigDecimal beta
    ) {
        List<BigDecimal> normalizedSeries = normalizeSeries(series);
        if (normalizedSeries.size() < 2) {
            return fallbackTrendDemand;
        }
        BigDecimal alphaValue = bounded(defaultBigDecimal(alpha, null, BigDecimal.valueOf(0.35d)), BigDecimal.valueOf(0.2d), BigDecimal.valueOf(0.5d));
        BigDecimal betaValue = bounded(defaultBigDecimal(beta, null, BigDecimal.valueOf(0.12d)), BigDecimal.valueOf(0.05d), BigDecimal.valueOf(0.2d));
        BigDecimal level = normalizedSeries.get(0);
        BigDecimal trend = normalizedSeries.get(1).subtract(normalizedSeries.get(0));
        for (int i = 1; i < normalizedSeries.size(); i++) {
            BigDecimal current = normalizedSeries.get(i);
            BigDecimal previousLevel = level;
            level = alphaValue.multiply(current)
                    .add(BigDecimal.ONE.subtract(alphaValue).multiply(level.add(trend)));
            trend = betaValue.multiply(level.subtract(previousLevel))
                    .add(BigDecimal.ONE.subtract(betaValue).multiply(trend));
        }
        return scale(level.add(trend.multiply(BigDecimal.valueOf(Math.max(1, forecastDays)))));
    }

    private VolatileDemandResult resolveVolatileDemand(
            MaterialRuleContext context,
            BigDecimal trendDemand,
            BigDecimal adjustedRecipeDemand,
            BigDecimal modelWeight
    ) {
        BigDecimal structuredBase = max(trendDemand.multiply(BigDecimal.valueOf(0.6d))
                .add(adjustedRecipeDemand.multiply(BigDecimal.valueOf(0.4d))), max(trendDemand, adjustedRecipeDemand));
        BigDecimal volatilityBoost = BigDecimal.ONE.add(
                bounded(scale(context.demandCv()).subtract(BigDecimal.valueOf(0.35d)).multiply(BigDecimal.valueOf(0.6d)),
                        ZERO,
                        BigDecimal.valueOf(0.45d))
        );
        BigDecimal activityBoost = max(BigDecimal.ONE, scale(context.activitySensitivity()));
        BigDecimal structuredDemand = scale(structuredBase.multiply(max(volatilityBoost, activityBoost)));
        BigDecimal mlForecast = scale(context.volatileMlForecastQty());
        if (mlForecast.compareTo(ZERO) <= 0) {
            return new VolatileDemandResult(structuredDemand, MODEL_VOLATILITY_FALLBACK);
        }
        BigDecimal blendedWeight = bounded(defaultBigDecimal(modelWeight, null, BigDecimal.valueOf(0.75d)), BigDecimal.valueOf(0.55d), BigDecimal.valueOf(0.90d));
        BigDecimal blendedDemand = scale(mlForecast.multiply(blendedWeight)
                .add(structuredDemand.multiply(BigDecimal.ONE.subtract(blendedWeight))));
        BigDecimal lowerClamp = max(scale(structuredDemand.multiply(BigDecimal.valueOf(0.60d))), ZERO);
        BigDecimal upperReference = max(max(structuredDemand, adjustedRecipeDemand), max(trendDemand, mlForecast));
        BigDecimal upperClamp = max(scale(upperReference.multiply(BigDecimal.valueOf(2.50d))), ONE);
        return new VolatileDemandResult(bounded(blendedDemand, lowerClamp, upperClamp), MODEL_VOLATILITY);
    }

    private BigDecimal resolveIntermittentDemand(
            List<BigDecimal> series,
            BigDecimal fallbackTrendDemand,
            Integer forecastDays,
            BigDecimal alpha
    ) {
        List<BigDecimal> normalizedSeries = normalizeSeries(series);
        if (normalizedSeries.isEmpty()) {
            return fallbackTrendDemand;
        }
        BigDecimal alphaValue = bounded(defaultBigDecimal(alpha, null, BigDecimal.valueOf(0.35d)), BigDecimal.valueOf(0.1d), BigDecimal.valueOf(0.5d));
        BigDecimal demandEstimate = ZERO;
        BigDecimal intervalEstimate = BigDecimal.ONE;
        int interval = 1;
        boolean initialized = false;
        for (BigDecimal point : normalizedSeries) {
            if (point.compareTo(ZERO) > 0) {
                if (!initialized) {
                    demandEstimate = point;
                    intervalEstimate = BigDecimal.valueOf(interval);
                    initialized = true;
                } else {
                    demandEstimate = demandEstimate.add(alphaValue.multiply(point.subtract(demandEstimate)));
                    intervalEstimate = intervalEstimate.add(alphaValue.multiply(BigDecimal.valueOf(interval).subtract(intervalEstimate)));
                }
                interval = 1;
            } else {
                interval++;
            }
        }
        if (!initialized || intervalEstimate.compareTo(ZERO) <= 0) {
            return fallbackTrendDemand;
        }
        BigDecimal dailyForecast = demandEstimate.divide(intervalEstimate, 6, RoundingMode.HALF_UP);
        return scale(dailyForecast.multiply(BigDecimal.valueOf(Math.max(1, forecastDays))));
    }

    private BigDecimal resolveLongCycleDemand(BigDecimal avg30, Integer forecastDays, BigDecimal leadTimeDays) {
        BigDecimal daily = scale(avg30);
        if (daily.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return scale(daily.multiply(BigDecimal.valueOf(Math.max(1, forecastDays))).add(daily.multiply(leadTimeDays)));
    }

    private BigDecimal resolveSigma(BigDecimal std30, int forecastDays, BigDecimal forecastDemand) {
        BigDecimal normalizedStd = scale(std30);
        if (normalizedStd.compareTo(ZERO) <= 0) {
            return scale(max(forecastDemand.multiply(BigDecimal.valueOf(0.12d)), ONE));
        }
        double sqrtDays = Math.sqrt(Math.max(1, forecastDays));
        return scale(normalizedStd.multiply(BigDecimal.valueOf(sqrtDays)));
    }

    private BigDecimal resolveCoverageDays(BigDecimal availableStockQty, BigDecimal avg7) {
        if (avg7.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return scale(scale(availableStockQty).divide(avg7, 3, RoundingMode.HALF_UP));
    }

    private String resolvePriority(BigDecimal coverageDays, BigDecimal suggestedQty, BigDecimal availableStockQty) {
        if (scale(suggestedQty).compareTo(ZERO) <= 0) {
            return PRIORITY_NORMAL;
        }
        if (coverageDays.compareTo(ZERO) <= 0 && scale(availableStockQty).compareTo(ZERO) <= 0) {
            return PRIORITY_URGENT;
        }
        if (coverageDays.compareTo(BigDecimal.ONE) < 0) {
            return PRIORITY_URGENT;
        }
        if (coverageDays.compareTo(BigDecimal.valueOf(3L)) < 0) {
            return PRIORITY_HIGH;
        }
        return PRIORITY_NORMAL;
    }

    private BigDecimal resolveSafetyStock(
            MaterialRuleContext context,
            ModelParameters parameters,
            BigDecimal avg7,
            int safetyDays
    ) {
        BigDecimal serviceLevel = defaultBigDecimal(parameters.serviceLevel(), context.serviceLevel(), DEFAULT_SERVICE_LEVEL);
        BigDecimal z = resolveServiceLevelZ(serviceLevel);
        int leadTimeDays = Math.max(1, defaultInt(parameters.leadTimeDays(), context.leadTimeDays(), 3));
        int reviewPeriod = Math.max(1, context.forecastDays() == null ? 7 : context.forecastDays());
        BigDecimal sigma = scale(context.stdConsumption30d());
        BigDecimal dynamicSafety = sigma.compareTo(ZERO) <= 0
                ? avg7.multiply(BigDecimal.valueOf(safetyDays))
                : scale(z.multiply(sigma).multiply(BigDecimal.valueOf(Math.sqrt(leadTimeDays + reviewPeriod))));
        return scale(max(scale(context.minStock()), dynamicSafety));
    }

    private int resolveSafetyDays(String materialSegment, boolean perishable, Integer warningDays, Integer configuredSafetyDays) {
        if (configuredSafetyDays != null && configuredSafetyDays > 0) {
            return configuredSafetyDays;
        }
        if (perishable) {
            return 1;
        }
        if (SEGMENT_VOLATILE.equals(materialSegment)) {
            return 4;
        }
        if (SEGMENT_INTERMITTENT.equals(materialSegment)) {
            return 2;
        }
        if (warningDays != null && warningDays >= 45) {
            return 4;
        }
        if (SEGMENT_RECIPE_DRIVEN.equals(materialSegment) || SEGMENT_LONG_CYCLE.equals(materialSegment)) {
            return 2;
        }
        return 3;
    }

    private int resolveMaxCoverageDays(String materialSegment, boolean perishable, Integer shelfLifeDays, Integer configuredMaxCoverageDays) {
        if (configuredMaxCoverageDays != null && configuredMaxCoverageDays > 0) {
            return configuredMaxCoverageDays;
        }
        if (perishable) {
            return resolvePerishableCoverDays(shelfLifeDays);
        }
        if (SEGMENT_LONG_CYCLE.equals(materialSegment)) {
            return 14;
        }
        if (SEGMENT_INTERMITTENT.equals(materialSegment)) {
            return 10;
        }
        return 7;
    }

    private int resolvePerishableCoverDays(Integer shelfLifeDays) {
        if (shelfLifeDays == null || shelfLifeDays <= 0) {
            return DEFAULT_LOSS_LIMIT_FACTOR.intValue();
        }
        return Math.max(1, Math.min(DEFAULT_LOSS_LIMIT_FACTOR.intValue(), shelfLifeDays));
    }

    private boolean isPerishable(String materialCategory, Integer shelfLifeDays) {
        if (shelfLifeDays != null && shelfLifeDays > 0 && shelfLifeDays <= 7) {
            return true;
        }
        String category = normalize(materialCategory);
        return category.contains("蔬菜")
                || category.contains("肉")
                || category.contains("蛋")
                || category.contains("鲜奶")
                || category.contains("奶制品")
                || category.contains("豆制品");
    }

    private boolean isRecipeDrivenCategory(String materialCategory) {
        String category = normalize(materialCategory);
        return category.contains("蔬菜")
                || category.contains("肉")
                || category.contains("蛋")
                || category.contains("豆制品")
                || category.contains("奶制品")
                || category.contains("水果");
    }

    private BigDecimal resolveCombinedFactor(MaterialRuleContext context, ModelParameters parameters) {
        BigDecimal calendar = scaleFactor(context.calendarFactor());
        BigDecimal holiday = scaleFactor(context.holidayFactor()).multiply(scaleFactor(parameters.holidayFactor()));
        BigDecimal activity = scaleFactor(context.activityFactor()).multiply(scaleFactor(parameters.activityFactor()));
        BigDecimal weekend = scaleFactor(parameters.weekendFactor());
        return scale(calendar.multiply(holiday).multiply(activity).multiply(weekend));
    }

    private BigDecimal resolveServiceLevelZ(BigDecimal serviceLevel) {
        BigDecimal normalized = defaultBigDecimal(serviceLevel, null, DEFAULT_SERVICE_LEVEL);
        if (normalized.compareTo(BigDecimal.valueOf(0.975d)) >= 0) {
            return BigDecimal.valueOf(1.96d);
        }
        if (normalized.compareTo(BigDecimal.valueOf(0.95d)) >= 0) {
            return BigDecimal.valueOf(1.65d);
        }
        if (normalized.compareTo(BigDecimal.valueOf(0.90d)) >= 0) {
            return BigDecimal.valueOf(1.28d);
        }
        return BigDecimal.valueOf(0.84d);
    }

    private List<BigDecimal> normalizeSeries(List<BigDecimal> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<BigDecimal> normalized = new ArrayList<>();
        for (BigDecimal value : source) {
            normalized.add(scale(value));
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private BigDecimal scaleFactor(BigDecimal value) {
        BigDecimal normalized = scale(value);
        return normalized.compareTo(ZERO) <= 0 ? ONE : normalized;
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        BigDecimal normalizedLeft = scale(left);
        BigDecimal normalizedRight = scale(right);
        return normalizedLeft.compareTo(normalizedRight) >= 0 ? normalizedLeft : normalizedRight;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal bounded(BigDecimal value, BigDecimal min, BigDecimal max) {
        BigDecimal normalized = scale(value);
        if (normalized.compareTo(min) < 0) {
            return min;
        }
        if (normalized.compareTo(max) > 0) {
            return max;
        }
        return normalized;
    }

    private BigDecimal defaultBigDecimal(BigDecimal first, BigDecimal second, BigDecimal fallback) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return fallback;
    }

    private int defaultInt(Integer first, Integer second, int fallback) {
        if (first != null && first > 0) {
            return first;
        }
        if (second != null && second > 0) {
            return second;
        }
        return fallback;
    }

    private String format(BigDecimal value) {
        return scale(value).stripTrailingZeros().toPlainString();
    }

    public record MaterialRuleContext(
            Long materialId,
            String materialName,
            String materialCategory,
            String materialUnit,
            Integer shelfLifeDays,
            Integer warningDays,
            Integer forecastDays,
            Integer consumptionDays30d,
            BigDecimal currentStockQty,
            BigDecimal availableStockQty,
            BigDecimal avgConsumption7d,
            BigDecimal avgConsumption14d,
            BigDecimal avgConsumption30d,
            BigDecimal stdConsumption30d,
            BigDecimal recipeDemand7d,
            BigDecimal recipeHistory30d,
            BigDecimal actualConsumption30d,
            BigDecimal pendingPlanQty,
            BigDecimal inTransitQty,
            BigDecimal minStock,
            BigDecimal maxStock,
            BigDecimal calendarFactor,
            BigDecimal holidayFactor,
            BigDecimal activityFactor,
            BigDecimal recipeDriveRatio,
            BigDecimal demandActiveRatio,
            BigDecimal demandCv,
            BigDecimal activitySensitivity,
            String materialSegment,
            Integer leadTimeDays,
            BigDecimal serviceLevel,
            BigDecimal inventoryTurnoverDays,
            BigDecimal volatileMlForecastQty,
            List<BigDecimal> recentConsumptionSeries30d
    ) {
    }

    public record ModelParameters(
            String materialSegment,
            String modelType,
            BigDecimal modelWeight,
            Integer safetyDays,
            Integer leadTimeDays,
            BigDecimal serviceLevel,
            BigDecimal alphaValue,
            BigDecimal betaValue,
            BigDecimal holidayFactor,
            BigDecimal weekendFactor,
            BigDecimal activityFactor,
            Integer maxCoverageDays,
            BigDecimal recipeCorrectionMin,
            BigDecimal recipeCorrectionMax,
            BigDecimal minOrderQty,
            BigDecimal packSize,
            BigDecimal leadTimeRiskFactor,
            BigDecimal shortagePenalty,
            BigDecimal holdingCostRate,
            BigDecimal wasteCostRate,
            BigDecimal orderCost,
            Integer optimizationVersion,
            BigDecimal optimizationScore,
            String sourceType
    ) {
    }

    public record RuleResult(
            String modelType,
            String materialSegment,
            boolean perishable,
            BigDecimal recipeCorrectionFactor,
            BigDecimal trendDaily,
            BigDecimal trendDemand,
            BigDecimal forecastDemandQty,
            BigDecimal safetyStockQty,
            BigDecimal suggestedPurchaseQty,
            BigDecimal lowerBoundQty,
            BigDecimal upperBoundQty,
            BigDecimal confidenceLevel,
            String priorityLevel,
            String forecastBasis,
            BigDecimal coverageDays,
            BigDecimal leadTimeDays,
            BigDecimal serviceLevel,
            BigDecimal safetyDays
    ) {
    }

    private record VolatileDemandResult(
            BigDecimal demandQty,
            String modelType
    ) {
    }
}
