package com.xykj.scm.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 采购需求预测四期智能解释引擎。
 */
@Component
public class PurchaseDemandForecastExplanationEngine {

    public static final String TEMPLATE_RECIPE_GROWTH = "recipe_growth";
    public static final String TEMPLATE_ACTIVITY_GROWTH = "activity_growth";
    public static final String TEMPLATE_STOCK_SUFFICIENT = "stock_sufficient";
    public static final String TEMPLATE_PERISHABLE_LIMIT = "perishable_limit";
    public static final String TEMPLATE_BASELINE_REPLENISH = "baseline_replenish";

    public static final String WARNING_LEVEL_LOW = "low";
    public static final String WARNING_LEVEL_MEDIUM = "medium";
    public static final String WARNING_LEVEL_HIGH = "high";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100L).setScale(2, RoundingMode.HALF_UP);

    public ExplanationResult explain(ExplanationContext context) {
        if (context == null) {
            return ExplanationResult.empty();
        }

        List<AnomalyHit> anomalyHits = detectAnomalies(context);
        String templateCode = resolveTemplateCode(context);
        String explanationTitle = resolveTitle(templateCode);
        boolean manualReviewRequired = context.manualReviewSuggested()
                || anomalyHits.stream().anyMatch(AnomalyHit::highRisk)
                || StrUtil.isNotBlank(context.phaseThreeRiskFlags());
        String warningLevel = resolveWarningLevel(context, anomalyHits, manualReviewRequired);
        String warningMessage = buildWarningMessage(context, anomalyHits, manualReviewRequired);
        String summary = buildSummary(context, templateCode);
        String detail = buildDetail(context, summary, anomalyHits, warningMessage);
        String approvalNote = buildApprovalNote(context, anomalyHits, manualReviewRequired);
        List<EvidenceItem> evidenceItems = buildEvidenceItems(context);
        String anomalyFlags = buildAnomalyFlags(context, anomalyHits);
        String anomalyCodes = anomalyHits.isEmpty()
                ? ""
                : String.join(",", anomalyHits.stream().map(AnomalyHit::code).toList());
        BigDecimal sortScore = buildSortScore(context, anomalyHits, warningLevel, manualReviewRequired);

        return new ExplanationResult(
                templateCode,
                explanationTitle,
                summary,
                detail,
                warningLevel,
                warningMessage,
                approvalNote,
                manualReviewRequired,
                anomalyFlags,
                anomalyCodes,
                scaleRate(sortScore),
                evidenceItems
        );
    }

    private String resolveTemplateCode(ExplanationContext context) {
        if (scale(context.suggestedQty()).compareTo(ZERO) <= 0
                || scale(context.availableInventoryQty()).compareTo(scale(context.forecastDemandQty()).add(scale(context.safetyStockQty()))) >= 0) {
            return TEMPLATE_STOCK_SUFFICIENT;
        }
        if (context.perishable()
                || (scale(context.theoreticalSuggestedQty()).compareTo(ZERO) > 0
                && scale(context.suggestedQty()).compareTo(scale(context.theoreticalSuggestedQty())) < 0)) {
            return TEMPLATE_PERISHABLE_LIMIT;
        }
        if (scale(context.recipeChangeRate()).compareTo(BigDecimal.valueOf(0.15d)) >= 0
                || scale(context.recipeDriveRatio()).compareTo(BigDecimal.valueOf(0.60d)) >= 0) {
            return TEMPLATE_RECIPE_GROWTH;
        }
        if (scale(context.activityFactor()).compareTo(BigDecimal.valueOf(1.05d)) > 0) {
            return TEMPLATE_ACTIVITY_GROWTH;
        }
        return TEMPLATE_BASELINE_REPLENISH;
    }

    private String resolveTitle(String templateCode) {
        return switch (StrUtil.blankToDefault(templateCode, TEMPLATE_BASELINE_REPLENISH)) {
            case TEMPLATE_RECIPE_GROWTH -> "菜谱驱动增长说明";
            case TEMPLATE_ACTIVITY_GROWTH -> "活动驱动增长说明";
            case TEMPLATE_STOCK_SUFFICIENT -> "库存充足说明";
            case TEMPLATE_PERISHABLE_LIMIT -> "易腐限量说明";
            default -> "常规补货说明";
        };
    }

    private String buildSummary(ExplanationContext context, String templateCode) {
        BigDecimal stockCoverageDays = resolveStockCoverageDays(context);
        return switch (templateCode) {
            case TEMPLATE_RECIPE_GROWTH -> String.format(
                    Locale.ROOT,
                    "因未来7天菜谱计划中该物料理论需求较近30天均值上升%s，且当前库存仅可覆盖%s天，系统建议补货%s。",
                    formatPercent(context.recipeChangeRate()),
                    format(stockCoverageDays),
                    format(context.suggestedQty())
            );
            case TEMPLATE_ACTIVITY_GROWTH -> String.format(
                    Locale.ROOT,
                    "因预测周期内存在%s，活动系数为%s，推高该物料需求，建议采购量较常规周期增加%s。",
                    resolveActivityNames(context.activityNames()),
                    format(context.activityFactor()),
                    formatPercent(context.activityLiftRate())
            );
            case TEMPLATE_STOCK_SUFFICIENT -> String.format(
                    Locale.ROOT,
                    "当前可用库存%s已可覆盖未来%s天需求，暂不建议补货。",
                    format(context.availableInventoryQty()),
                    format(stockCoverageDays)
            );
            case TEMPLATE_PERISHABLE_LIMIT -> String.format(
                    Locale.ROOT,
                    "该物料保质期较短，系统已按最大覆盖%s天规则限制采购量，避免过量备货。",
                    context.maxCoverageDays() == null ? "0" : context.maxCoverageDays().toString()
            );
            default -> String.format(
                    Locale.ROOT,
                    "结合历史消耗、当前库存与补货约束，系统建议补货%s，并按%s执行%s。",
                    format(context.suggestedQty()),
                    StrUtil.blankToDefault(context.modelType(), "当前模型"),
                    StrUtil.blankToDefault(context.orderAction(), "采购建议")
            );
        };
    }

    private String buildDetail(
            ExplanationContext context,
            String summary,
            List<AnomalyHit> anomalyHits,
            String warningMessage
    ) {
        StringBuilder detail = new StringBuilder(summary)
                .append(" 当前预测需求=").append(format(context.forecastDemandQty()))
                .append("，上期预测=").append(formatNullable(context.previousForecastQty()))
                .append("，库存位置=").append(format(context.inventoryPositionQty()))
                .append("，安全库存=").append(format(context.safetyStockQty()))
                .append("，在途量=").append(format(context.inTransitQty()))
                .append("，待执行采购量=").append(format(context.pendingPlanQty()))
                .append("，模型=").append(StrUtil.blankToDefault(context.modelType(), "未识别"))
                .append("，优先级=").append(StrUtil.blankToDefault(context.priorityLabel(), "正常补货"));
        if (StrUtil.isNotBlank(context.supplierName())) {
            detail.append("，推荐供应商=").append(context.supplierName());
        }
        if (StrUtil.isNotBlank(context.phaseThreeRiskFlags())) {
            detail.append("，补货约束=").append(context.phaseThreeRiskFlags());
        }
        if (!anomalyHits.isEmpty()) {
            detail.append(" 异常归因：")
                    .append(String.join("；", anomalyHits.stream().map(AnomalyHit::message).toList()))
                    .append("。");
        }
        if (StrUtil.isNotBlank(warningMessage)) {
            detail.append(" 预警说明：").append(warningMessage).append("。");
        }
        return detail.toString();
    }

    private String buildApprovalNote(
            ExplanationContext context,
            List<AnomalyHit> anomalyHits,
            boolean manualReviewRequired
    ) {
        if (manualReviewRequired) {
            return String.format(
                    Locale.ROOT,
                    "建议人工复核后再提交采购审批，重点核对%s。",
                    resolveReviewFocus(context, anomalyHits)
            );
        }
        if (scale(context.suggestedQty()).compareTo(ZERO) <= 0) {
            return "当前库存可覆盖本期需求，可暂不发起采购审批。";
        }
        if (StrUtil.equals("立即下单", context.orderAction())) {
            if (StrUtil.isNotBlank(context.supplierName())) {
                return String.format(Locale.ROOT,
                        "建议优先发起采购审批，并与供应商%s确认提前期与到货时点。",
                        context.supplierName());
            }
            return "建议优先发起采购审批，并同步确认供应商到货能力。";
        }
        return "建议按系统建议量进入采购审批，审批时可重点核对库存、菜谱与活动变化。";
    }

    private String resolveReviewFocus(ExplanationContext context, List<AnomalyHit> anomalyHits) {
        Set<String> parts = new LinkedHashSet<>();
        for (AnomalyHit anomalyHit : anomalyHits) {
            if (anomalyHit.highRisk()) {
                parts.add(anomalyHit.focus());
            }
        }
        if (StrUtil.isNotBlank(context.phaseThreeRiskFlags())) {
            parts.add("补货约束与建议量");
        }
        if (parts.isEmpty()) {
            parts.add("库存、活动与菜谱计划");
        }
        return String.join("、", parts);
    }

    private String resolveWarningLevel(
            ExplanationContext context,
            List<AnomalyHit> anomalyHits,
            boolean manualReviewRequired
    ) {
        if (manualReviewRequired) {
            return WARNING_LEVEL_HIGH;
        }
        if (!anomalyHits.isEmpty()
                || scale(context.activityFactor()).compareTo(BigDecimal.valueOf(1.20d)) > 0
                || scale(context.recipeChangeRate()).compareTo(BigDecimal.valueOf(0.30d)) > 0
                || scale(context.previousForecastChangeRate().abs()).compareTo(BigDecimal.valueOf(0.30d)) > 0) {
            return WARNING_LEVEL_MEDIUM;
        }
        return WARNING_LEVEL_LOW;
    }

    private String buildWarningMessage(
            ExplanationContext context,
            List<AnomalyHit> anomalyHits,
            boolean manualReviewRequired
    ) {
        if (manualReviewRequired) {
            return String.format(
                    Locale.ROOT,
                    "当前建议存在%s，建议人工复核。",
                    resolvePrimaryWarnings(context, anomalyHits)
            );
        }
        if (!anomalyHits.isEmpty()) {
            return String.format(Locale.ROOT,
                    "已识别到%s，请在执行采购前关注相关数据波动。",
                    resolvePrimaryWarnings(context, anomalyHits));
        }
        if (scale(context.suggestedQty()).compareTo(ZERO) <= 0) {
            return "当前库存覆盖充足，未发现需立即处理的风险。";
        }
        return "当前解释基于结构化数据自动生成，未发现显著异常。";
    }

    private String resolvePrimaryWarnings(ExplanationContext context, List<AnomalyHit> anomalyHits) {
        List<String> messages = new ArrayList<>();
        for (AnomalyHit anomalyHit : anomalyHits) {
            messages.add(anomalyHit.shortLabel());
            if (messages.size() >= 2) {
                break;
            }
        }
        if (messages.isEmpty() && StrUtil.isNotBlank(context.phaseThreeRiskFlags())) {
            messages.add(context.phaseThreeRiskFlags());
        }
        if (messages.isEmpty()) {
            messages.add("需求与库存波动");
        }
        return String.join("、", messages);
    }

    private List<EvidenceItem> buildEvidenceItems(ExplanationContext context) {
        List<EvidenceItem> factors = new ArrayList<>();
        factors.add(new EvidenceItem("预测需求", format(context.forecastDemandQty()), "本期预测窗口内的结构化需求输出"));
        factors.add(new EvidenceItem("上期预测", formatNullable(context.previousForecastQty()), "最近一次同物料预测需求"));
        factors.add(new EvidenceItem("实际消耗(30天)", format(context.actualConsumption30d()), "近30天累计真实消耗"));
        factors.add(new EvidenceItem("当前库存", format(context.currentInventoryQty()), "当前实时库存结余"));
        factors.add(new EvidenceItem("可用库存", format(context.availableInventoryQty()), "扣除锁定后的可支配库存"));
        factors.add(new EvidenceItem("库存位置", format(context.inventoryPositionQty()), "可用库存 + 在途量 + 待执行采购量"));
        factors.add(new EvidenceItem("安全库存", format(context.safetyStockQty()), "基于服务水平与波动度测算"));
        factors.add(new EvidenceItem("在途量", format(context.inTransitQty()), "已下单未到货数量"));
        factors.add(new EvidenceItem("待执行采购量", format(context.pendingPlanQty()), "已生成但未执行完的采购数量"));
        factors.add(new EvidenceItem("菜谱需求(7天)", format(context.recipeDemandQty()), "未来7天菜谱理论需求"));
        factors.add(new EvidenceItem("菜谱变化", formatPercent(context.recipeChangeRate()), "对比近30天同口径均值的变化幅度"));
        factors.add(new EvidenceItem("活动因子", format(context.activityFactor()), "预测周期活动放大系数"));
        factors.add(new EvidenceItem("模型/优先级",
                StrUtil.blankToDefault(context.modelType(), "未识别") + " / " + StrUtil.blankToDefault(context.priorityLabel(), "正常补货"),
                "当前解释对应的模型与补货优先级"));
        if (StrUtil.isNotBlank(context.supplierName())) {
            factors.add(new EvidenceItem("推荐供应商", context.supplierName(), "按履约、提前期与价格综合筛选"));
        }
        return factors;
    }

    private BigDecimal buildSortScore(
            ExplanationContext context,
            List<AnomalyHit> anomalyHits,
            String warningLevel,
            boolean manualReviewRequired
    ) {
        BigDecimal score = BigDecimal.ZERO;
        if (manualReviewRequired) {
            score = score.add(BigDecimal.valueOf(80));
        }
        if (WARNING_LEVEL_HIGH.equals(warningLevel)) {
            score = score.add(BigDecimal.valueOf(30));
        } else if (WARNING_LEVEL_MEDIUM.equals(warningLevel)) {
            score = score.add(BigDecimal.valueOf(15));
        }
        if (StrUtil.equals("立即下单", context.orderAction())) {
            score = score.add(BigDecimal.valueOf(20));
        }
        score = score.add(scale(context.totalCost()).min(BigDecimal.valueOf(9999)).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        score = score.add(BigDecimal.valueOf(anomalyHits.size()).multiply(BigDecimal.valueOf(5L)));
        if (scale(context.recipeChangeRate()).compareTo(BigDecimal.ZERO) > 0) {
            score = score.add(scale(context.recipeChangeRate()).multiply(BigDecimal.valueOf(10L)));
        }
        return score;
    }

    private String buildAnomalyFlags(ExplanationContext context, List<AnomalyHit> anomalyHits) {
        Set<String> flags = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(context.baseAnomalyFlags())) {
            Collections.addAll(flags, context.baseAnomalyFlags().split("[、,，]"));
        }
        if (StrUtil.isNotBlank(context.phaseThreeRiskFlags())) {
            Collections.addAll(flags, context.phaseThreeRiskFlags().split("[、,，]"));
        }
        for (AnomalyHit anomalyHit : anomalyHits) {
            flags.add(anomalyHit.shortLabel());
        }
        return flags.stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }

    private List<AnomalyHit> detectAnomalies(ExplanationContext context) {
        List<AnomalyHit> anomalies = new ArrayList<>();
        BigDecimal historyWindowDemand = scale(context.avgConsumption30d()).multiply(BigDecimal.valueOf(Math.max(1, context.forecastDays())));
        if (historyWindowDemand.compareTo(ZERO) > 0
                && scale(context.forecastDemandQty()).compareTo(historyWindowDemand.multiply(BigDecimal.valueOf(2L))) > 0) {
            anomalies.add(new AnomalyHit(
                    "forecast_spike",
                    "高于历史均值2倍以上",
                    "预测需求高于近30天均值2倍以上",
                    "需求激增与活动/菜谱配置",
                    true
            ));
        }
        if (context.inventoryMismatchDetected()) {
            anomalies.add(new AnomalyHit(
                    "inventory_mismatch",
                    "库存数据异常",
                    "库存数据异常",
                    "库存台账与出入库流水",
                    true
            ));
        }
        if (scale(context.recipeDemandQty()).compareTo(ZERO) <= 0
                && scale(context.recipeDriveRatio()).compareTo(BigDecimal.valueOf(0.60d)) >= 0) {
            anomalies.add(new AnomalyHit(
                    "recipe_missing",
                    "未来菜谱计划可能缺失",
                    "未来菜谱计划可能缺失，当前预测更多依赖历史趋势",
                    "未来7天菜谱计划配置",
                    false
            ));
        }
        if (scale(context.activityFactor()).compareTo(BigDecimal.valueOf(1.50d)) > 0) {
            anomalies.add(new AnomalyHit(
                    "activity_factor_high",
                    "活动放大系数过高",
                    "活动放大系数过高，建议人工复核",
                    "活动配置与活动影响系数",
                    true
            ));
        }
        return anomalies;
    }

    private BigDecimal resolveStockCoverageDays(ExplanationContext context) {
        BigDecimal avgDailyDemand = scale(context.avgDailyDemandQty());
        if (avgDailyDemand.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return scale(scale(context.availableInventoryQty()).divide(avgDailyDemand, 3, RoundingMode.HALF_UP));
    }

    private String resolveActivityNames(List<String> activityNames) {
        if (activityNames == null || activityNames.isEmpty()) {
            return "活动安排";
        }
        return String.join("、", activityNames);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleRate(BigDecimal value) {
        return value == null ? ZERO_RATE : value.setScale(6, RoundingMode.HALF_UP);
    }

    private String format(BigDecimal value) {
        return scale(value).stripTrailingZeros().toPlainString();
    }

    private String formatNullable(BigDecimal value) {
        if (value == null || scale(value).compareTo(ZERO) == 0) {
            return "—";
        }
        return format(value);
    }

    private String formatPercent(BigDecimal rate) {
        if (rate == null) {
            return "0%";
        }
        return scaleRate(rate).multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    public record ExplanationContext(
            String materialName,
            Integer forecastDays,
            BigDecimal forecastDemandQty,
            BigDecimal previousForecastQty,
            BigDecimal previousForecastChangeRate,
            BigDecimal avgConsumption30d,
            BigDecimal actualConsumption30d,
            BigDecimal avgDailyDemandQty,
            BigDecimal currentInventoryQty,
            BigDecimal availableInventoryQty,
            BigDecimal inventoryPositionQty,
            BigDecimal safetyStockQty,
            BigDecimal inTransitQty,
            BigDecimal pendingPlanQty,
            BigDecimal suggestedQty,
            BigDecimal theoreticalSuggestedQty,
            BigDecimal recipeDemandQty,
            BigDecimal recipeDriveRatio,
            BigDecimal recipeChangeRate,
            BigDecimal activityFactor,
            BigDecimal activityLiftRate,
            List<String> activityNames,
            boolean perishable,
            Integer maxCoverageDays,
            String modelType,
            String priorityLabel,
            String orderAction,
            String supplierName,
            String phaseThreeRiskFlags,
            String baseAnomalyFlags,
            boolean inventoryMismatchDetected,
            boolean manualReviewSuggested,
            BigDecimal totalCost
    ) {
    }

    public record ExplanationResult(
            String templateCode,
            String explanationTitle,
            String summary,
            String detail,
            String warningLevel,
            String warningMessage,
            String approvalNote,
            boolean manualReviewRequired,
            String anomalyFlags,
            String anomalyCodes,
            BigDecimal sortScore,
            List<EvidenceItem> evidenceItems
    ) {
        private static ExplanationResult empty() {
            return new ExplanationResult(
                    TEMPLATE_BASELINE_REPLENISH,
                    "常规补货说明",
                    "",
                    "",
                    WARNING_LEVEL_LOW,
                    "",
                    "",
                    false,
                    "",
                    "",
                    ZERO_RATE,
                    Collections.emptyList()
            );
        }
    }

    public record EvidenceItem(String label, String value, String description) {
    }

    private record AnomalyHit(
            String code,
            String shortLabel,
            String message,
            String focus,
            boolean highRisk
    ) {
    }
}
