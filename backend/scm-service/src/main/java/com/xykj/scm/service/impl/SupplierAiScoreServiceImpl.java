package com.xykj.scm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.service.AuditLogService;
import com.xykj.scm.entity.Supplier;
import com.xykj.scm.mapper.SupplierMapper;
import com.xykj.scm.service.SupplierAiScoreService;
import com.xykj.scm.vo.SupplierQualificationFileVO;
import com.xykj.scm.vo.SupplierVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商 AI 综合评分实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierAiScoreServiceImpl implements SupplierAiScoreService {

    private static final String MODULE_CODE_SUPPLIER = AuditModule.SCM_SUPPLIER.getCode();
    private static final String SCORE_AUDIT_PREFIX = "AI综合评分更新：";
    private static final Set<String> INVALID_ORDER_STATUSES = Set.of("pending_submit", "rejected", "voided", "cancelled");
    private static final Set<String> INVALID_DELIVERY_ORDER_STATUSES = Set.of("pending_submit", "rejected", "voided", "cancelled", "terminated");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> QUARANTINE_KEYWORDS = Set.of("检疫", "检验检疫", "动物检疫", "肉品品质");
    private static final Set<String> REPORT_KEYWORDS = Set.of("检测", "检验", "合格报告", "检测报告", "质检");
    private static final Set<String> QUALITY_SEVERE_KEYWORDS = Set.of("变质", "异物", "霉变", "腐败", "发臭", "污染");
    private static final Set<String> DELIVERY_SEVERE_KEYWORDS = Set.of("影响供餐", "断供", "停供", "严重延迟", "严重逾期");
    private static final Set<String> DELIVERY_SPLIT_KEYWORDS = Set.of("分批", "部分签收", "部分到货", "部分收货", "分次送达", "拆分送达");
    private static final Set<String> DELIVERY_REPLENISH_KEYWORDS = Set.of("补送", "补发", "补货", "少货补送", "破损补发", "换货补送");
    private static final Set<String> DELIVERY_CHANGE_KEYWORDS = Set.of("改单", "改期", "变更到货", "变更交期", "调整到货");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final SupplierMapper supplierMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshAllSupplierScores() {
        List<Supplier> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<>());
        refreshSuppliers(suppliers, "系统批量刷新供应商 AI 综合评分");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshSupplierScores(Collection<Long> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return;
        }
        List<Long> distinctIds = supplierIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return;
        }
        List<Supplier> suppliers = supplierMapper.selectList(
                new LambdaQueryWrapper<Supplier>().in(Supplier::getId, distinctIds)
        );
        refreshSuppliers(suppliers, "按需刷新供应商 AI 综合评分");
    }

    @Override
    public void enrichScoreMeta(SupplierVO supplierVO) {
        if (supplierVO == null || supplierVO.getId() == null) {
            return;
        }
        enrichScoreMeta(Collections.singletonList(supplierVO));
    }

    @Override
    public void enrichScoreMeta(List<SupplierVO> supplierVOList) {
        if (supplierVOList == null || supplierVOList.isEmpty()) {
            return;
        }
        List<Long> supplierIds = supplierVOList.stream()
                .map(SupplierVO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (supplierIds.isEmpty()) {
            return;
        }

        Map<Long, SupplierScoreAuditSnapshot> snapshotMap = queryLatestScoreSnapshots(supplierIds);
        String defaultPeriod = buildStatisticsPeriod(LocalDate.now().minusMonths(6), LocalDate.now());

        for (SupplierVO vo : supplierVOList) {
            SupplierScoreAuditSnapshot snapshot = snapshotMap.get(vo.getId());
            vo.setAiLevel(snapshot != null && StrUtil.isNotBlank(snapshot.aiLevel())
                    ? snapshot.aiLevel()
                    : resolveAiLevel(vo.getCreditScore()));
            vo.setRecommendPriority(snapshot != null && StrUtil.isNotBlank(snapshot.recommendPriority())
                    ? snapshot.recommendPriority()
                    : resolveRecommendPriority(vo.getCreditScore()));
            vo.setRiskWarningLevel(snapshot != null && StrUtil.isNotBlank(snapshot.riskWarningLevel())
                    ? snapshot.riskWarningLevel()
                    : resolveRiskWarningLevel(vo.getCreditScore(), false, false, false));
            vo.setOptimizationSuggestions(snapshot != null && snapshot.optimizationSuggestions() != null
                    ? snapshot.optimizationSuggestions()
                    : Collections.emptyList());
            vo.setScoreUpdatedAt(snapshot != null ? snapshot.scoreUpdatedAt() : null);
            vo.setScoreStatisticsPeriod(snapshot != null && StrUtil.isNotBlank(snapshot.scoreStatisticsPeriod())
                    ? snapshot.scoreStatisticsPeriod()
                    : defaultPeriod);
            vo.setScoreQualitySampleInsufficient(snapshot != null && Boolean.TRUE.equals(snapshot.scoreQualitySampleInsufficient()));
            vo.setScorePriceSampleInsufficient(snapshot != null && Boolean.TRUE.equals(snapshot.scorePriceSampleInsufficient()));
            vo.setScoreDeliverySampleInsufficient(snapshot != null && Boolean.TRUE.equals(snapshot.scoreDeliverySampleInsufficient()));
        }
    }

    private void refreshSuppliers(List<Supplier> suppliers, String batchDesc) {
        if (suppliers == null || suppliers.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate periodStart = now.minusMonths(6).toLocalDate();
        LocalDate periodEnd = now.toLocalDate();
        String statisticsPeriod = buildStatisticsPeriod(periodStart, periodEnd);

        List<Long> supplierIds = suppliers.stream().map(Supplier::getId).toList();
        Map<Long, List<ReceiptObservation>> receiptMap = queryReceiptObservations(supplierIds, periodStart);
        Map<Long, List<PriceObservation>> priceMap = queryPriceObservations(supplierIds, periodStart);
        Map<Long, List<DeliveryObservation>> deliveryMap = queryDeliveryObservations(supplierIds, periodStart);

        int refreshedCount = 0;
        List<Map<String, Object>> summaryItems = new ArrayList<>();

        for (Supplier supplier : suppliers) {
            try {
                SupplierScoreComputation computation = computeSupplierScore(
                        supplier,
                        receiptMap.getOrDefault(supplier.getId(), Collections.emptyList()),
                        priceMap.getOrDefault(supplier.getId(), Collections.emptyList()),
                        deliveryMap.getOrDefault(supplier.getId(), Collections.emptyList()),
                        periodStart,
                        periodEnd,
                        now
                );
                persistScore(supplier.getId(), computation);
                logScoreRefresh(supplier, computation, statisticsPeriod);
                refreshedCount++;
                summaryItems.add(buildSummaryItem(supplier, computation));
            } catch (Exception ex) {
                log.error("刷新供应商 AI 综合评分失败: supplierId={}, supplierCode={}", supplier.getId(), supplier.getSupplierCode(), ex);
                summaryItems.add(buildFailureSummaryItem(supplier, ex.getMessage()));
            }
        }

        if (suppliers.size() > 1) {
            Map<String, Object> batchSummary = new LinkedHashMap<>();
            batchSummary.put("statisticsPeriod", statisticsPeriod);
            batchSummary.put("refreshedCount", refreshedCount);
            batchSummary.put("totalCount", suppliers.size());
            batchSummary.put("suppliers", summaryItems);
            auditLogService.log(
                    AuditModule.SCM_SUPPLIER,
                    AuditOperationType.UPDATE,
                    null,
                    null,
                    batchDesc,
                    null,
                    toJson(batchSummary)
            );
        }
        log.info("供应商 AI 综合评分刷新完成: success={}, total={}", refreshedCount, suppliers.size());
    }

    private SupplierScoreComputation computeSupplierScore(
            Supplier supplier,
            List<ReceiptObservation> receiptObservations,
            List<PriceObservation> priceObservations,
            List<DeliveryObservation> deliveryObservations,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDateTime now
    ) {
        DimensionResult qualificationResult = computeQualificationScore(supplier, now);
        DimensionResult qualityResult = computeQualityScore(receiptObservations);
        DimensionResult priceResult = computePriceScore(priceObservations);
        DimensionResult deliveryResult = computeDeliveryScore(deliveryObservations);

        BigDecimal overall = scaleScore(qualificationResult.score()
                .add(qualityResult.score())
                .add(priceResult.score())
                .add(deliveryResult.score()));

        boolean severeRisk = qualificationResult.severeRisk()
                || qualityResult.severeRisk()
                || deliveryResult.severeRisk();
        String aiLevel = resolveAiLevel(overall);
        String recommendPriority = resolveRecommendPriority(overall);
        String riskWarningLevel = resolveRiskWarningLevel(
                overall,
                severeRisk,
                qualityResult.sampleInsufficient() || priceResult.sampleInsufficient(),
                deliveryResult.sampleInsufficient()
        );
        List<String> optimizationSuggestions = resolveOptimizationSuggestions(
                qualificationResult,
                qualityResult,
                priceResult,
                deliveryResult,
                overall
        );

        return new SupplierScoreComputation(
                overall,
                qualificationResult.score(),
                qualityResult.score(),
                priceResult.score(),
                deliveryResult.score(),
                aiLevel,
                recommendPriority,
                riskWarningLevel,
                optimizationSuggestions,
                qualificationResult.sampleInsufficient(),
                qualityResult.sampleInsufficient(),
                priceResult.sampleInsufficient(),
                deliveryResult.sampleInsufficient(),
                periodStart,
                periodEnd
        );
    }

    private DimensionResult computeQualificationScore(Supplier supplier, LocalDateTime now) {
        BigDecimal baseInfoScore = BigDecimal.ZERO;
        if (StrUtil.isNotBlank(supplier.getSupplierName())) {
            baseInfoScore = baseInfoScore.add(BigDecimal.valueOf(2));
        }
        if (StrUtil.isNotBlank(supplier.getUnifiedCreditCode())) {
            baseInfoScore = baseInfoScore.add(BigDecimal.valueOf(2));
        }
        if (StrUtil.isNotBlank(supplier.getAddress())) {
            baseInfoScore = baseInfoScore.add(BigDecimal.valueOf(2));
        }
        if (StrUtil.isNotBlank(supplier.getContactName())) {
            baseInfoScore = baseInfoScore.add(BigDecimal.valueOf(2));
        }
        if (StrUtil.isNotBlank(supplier.getContactPhone())) {
            baseInfoScore = baseInfoScore.add(BigDecimal.valueOf(2));
        }

        if (isExpired(supplier.getLicenseExpiresAt(), now) || isExpired(supplier.getFoodLicenseExpiresAt(), now)) {
            return new DimensionResult(ZERO, false, true, "关键资质已过期");
        }

        BigDecimal certificateScore = BigDecimal.valueOf(20);
        if (StrUtil.isBlank(supplier.getLicenseNo())) {
            certificateScore = certificateScore.subtract(BigDecimal.valueOf(8));
        }
        if (StrUtil.isBlank(supplier.getFoodLicenseNo())) {
            certificateScore = certificateScore.subtract(BigDecimal.valueOf(6));
        }

        Set<String> fileNames = extractQualificationFileNames(supplier.getQualificationFiles());
        if (!containsAnyKeyword(fileNames, QUARANTINE_KEYWORDS)) {
            certificateScore = certificateScore.subtract(BigDecimal.valueOf(3));
        }
        if (!containsAnyKeyword(fileNames, REPORT_KEYWORDS)) {
            certificateScore = certificateScore.subtract(BigDecimal.valueOf(3));
        }

        return new DimensionResult(
                scaleScore(baseInfoScore.add(certificateScore.max(BigDecimal.ZERO))),
                false,
                false,
                null
        );
    }

    private DimensionResult computeQualityScore(List<ReceiptObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return new DimensionResult(ZERO, true, false, "近6个月无有效验收样本");
        }

        long total = observations.size();
        if (total <= 0) {
            return new DimensionResult(ZERO, true, false, "近6个月无有效验收样本");
        }
        long passCount = observations.stream()
                .filter(observation -> "pass".equalsIgnoreCase(observation.result()))
                .count();
        long rejectCount = observations.stream()
                .filter(observation -> "reject".equalsIgnoreCase(observation.result()))
                .count();
        long normalReturnCount = observations.stream()
                .filter(this::isNormalReturnObservation)
                .count();
        boolean severeRisk = observations.stream().anyMatch(this::hasSevereQualityRisk);

        if (severeRisk) {
            return new DimensionResult(ZERO, false, true, "命中严重质量问题清零规则");
        }

        BigDecimal qualifiedRate = safeDivide(BigDecimal.valueOf(passCount), total);
        if (qualifiedRate == null) {
            return new DimensionResult(ZERO, true, false, "近6个月无有效验收样本");
        }
        BigDecimal baseScore = resolveRateScore(qualifiedRate, 25, 20, 15, 8, 5);
        BigDecimal deduction = BigDecimal.valueOf(rejectCount * 5L + normalReturnCount * 3L);

        return new DimensionResult(
                scaleScore(baseScore.subtract(deduction).max(BigDecimal.ZERO)),
                false,
                false,
                null
        );
    }

    private DimensionResult computePriceScore(List<PriceObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return new DimensionResult(ZERO, true, false, "近6个月无有效订单单价样本");
        }

        Map<Long, List<PriceObservation>> byMaterial = observations.stream()
                .filter(observation -> observation.unitPrice() != null && observation.unitPrice().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(PriceObservation::materialId));
        if (byMaterial.isEmpty()) {
            return new DimensionResult(ZERO, true, false, "近6个月无有效订单单价样本");
        }

        List<BigDecimal> fluctuations = new ArrayList<>();
        boolean stableBonus = false;
        boolean maliciousIncrease = false;

        for (List<PriceObservation> materialObservations : byMaterial.values()) {
            if (materialObservations.size() < 2) {
                continue;
            }
            BigDecimal maxPrice = materialObservations.stream()
                    .map(PriceObservation::unitPrice)
                    .max(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);
            BigDecimal minPrice = materialObservations.stream()
                    .map(PriceObservation::unitPrice)
                    .min(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);
            BigDecimal avgPrice = materialObservations.stream()
                    .map(PriceObservation::unitPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    ;
            avgPrice = safeDivide(avgPrice, materialObservations.size());
            if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal fluctuation = safeDivide(maxPrice.subtract(minPrice), avgPrice);
            if (fluctuation == null) {
                continue;
            }
            fluctuations.add(fluctuation);

            Map<YearMonth, BigDecimal> monthlyAverageMap = materialObservations.stream()
                    .collect(Collectors.groupingBy(
                            observation -> YearMonth.from(observation.orderDate()),
                            Collectors.collectingAndThen(Collectors.toList(), this::averageMonthlyPrice)
                    ));
            if (!stableBonus && hasThreeConsecutiveStableMonths(monthlyAverageMap)) {
                stableBonus = true;
            }
            if (!maliciousIncrease && hasThreeConsecutiveIncreasingMonths(monthlyAverageMap)) {
                maliciousIncrease = true;
            }
        }

        if (fluctuations.isEmpty()) {
            return new DimensionResult(ZERO, true, false, "近6个月单价样本不足以计算波动");
        }

        BigDecimal averageFluctuation = fluctuations.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                ;
        averageFluctuation = safeDivide(averageFluctuation, fluctuations.size());
        if (averageFluctuation == null) {
            return new DimensionResult(ZERO, true, false, "近6个月单价样本不足以计算波动");
        }

        BigDecimal baseScore = resolvePriceFluctuationScore(averageFluctuation);
        if (stableBonus) {
            baseScore = baseScore.add(BigDecimal.valueOf(2));
        }
        if (maliciousIncrease) {
            baseScore = baseScore.subtract(BigDecimal.valueOf(5));
        }

        return new DimensionResult(
                scaleScore(baseScore.max(BigDecimal.ZERO).min(BigDecimal.valueOf(25))),
                false,
                false,
                null
        );
    }

    private DimensionResult computeDeliveryScore(List<DeliveryObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return new DimensionResult(ZERO, true, false, "近6个月无有效履约样本");
        }

        long total = 0L;
        long onTimeCount = 0L;
        long overdueCount = 0L;
        boolean severeRisk = false;
        for (DeliveryObservation observation : observations) {
            DeliveryAssessment assessment = assessDeliveryObservation(observation);
            if (assessment.shouldLog()) {
                logDeliveryAssessment(observation, assessment);
            }
            if (!assessment.validSample()) {
                continue;
            }
            total++;
            if (assessment.onTime()) {
                onTimeCount++;
            } else {
                overdueCount++;
            }
            if (assessment.severeRisk()) {
                severeRisk = true;
            }
        }
        if (total <= 0) {
            return new DimensionResult(ZERO, true, false, "近6个月无有效履约样本");
        }

        if (severeRisk) {
            return new DimensionResult(ZERO, false, true, "命中严重履约逾期清零规则");
        }

        BigDecimal punctualityRate = safeDivide(BigDecimal.valueOf(onTimeCount), total);
        if (punctualityRate == null) {
            return new DimensionResult(ZERO, true, false, "近6个月无有效履约样本");
        }
        BigDecimal baseScore = resolveRateScore(punctualityRate, 20, 16, 12, 6, 4);
        BigDecimal deduction = BigDecimal.valueOf(overdueCount * 2L);

        return new DimensionResult(
                scaleScore(baseScore.subtract(deduction).max(BigDecimal.ZERO)),
                false,
                false,
                null
        );
    }

    private void persistScore(Long supplierId, SupplierScoreComputation computation) {
        jdbcTemplate.update("""
                UPDATE scm_supplier
                   SET credit_score = ?,
                       score_qualification = ?,
                       score_quality = ?,
                       score_price = ?,
                       score_delivery = ?,
                       updated_at = updated_at
                 WHERE id = ?
                   AND deleted = 0
                """,
                computation.overallScore(),
                computation.qualificationScore(),
                computation.qualityScore(),
                computation.priceScore(),
                computation.deliveryScore(),
                supplierId
        );
    }

    private void logScoreRefresh(Supplier supplier, SupplierScoreComputation computation, String statisticsPeriod) {
        SupplierScoreAuditSnapshot snapshot = new SupplierScoreAuditSnapshot(
                formatDateTime(LocalDateTime.now()),
                statisticsPeriod,
                computation.aiLevel(),
                computation.recommendPriority(),
                computation.riskWarningLevel(),
                computation.optimizationSuggestions(),
                computation.qualitySampleInsufficient(),
                computation.priceSampleInsufficient(),
                computation.deliverySampleInsufficient()
        );

        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("creditScore", supplier.getCreditScore());
        beforeData.put("scoreQualification", supplier.getScoreQualification());
        beforeData.put("scoreQuality", supplier.getScoreQuality());
        beforeData.put("scorePrice", supplier.getScorePrice());
        beforeData.put("scoreDelivery", supplier.getScoreDelivery());

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("creditScore", computation.overallScore());
        afterData.put("scoreQualification", computation.qualificationScore());
        afterData.put("scoreQuality", computation.qualityScore());
        afterData.put("scorePrice", computation.priceScore());
        afterData.put("scoreDelivery", computation.deliveryScore());
        afterData.put("scoreUpdatedAt", snapshot.scoreUpdatedAt());
        afterData.put("scoreStatisticsPeriod", snapshot.scoreStatisticsPeriod());
        afterData.put("aiLevel", snapshot.aiLevel());
        afterData.put("recommendPriority", snapshot.recommendPriority());
        afterData.put("riskWarningLevel", snapshot.riskWarningLevel());
        afterData.put("optimizationSuggestions", snapshot.optimizationSuggestions());
        afterData.put("scoreQualitySampleInsufficient", snapshot.scoreQualitySampleInsufficient());
        afterData.put("scorePriceSampleInsufficient", snapshot.scorePriceSampleInsufficient());
        afterData.put("scoreDeliverySampleInsufficient", snapshot.scoreDeliverySampleInsufficient());

        runWithSystemContextIfAbsent(supplier, () -> auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.UPDATE,
                supplier.getId(),
                supplier.getSupplierCode(),
                SCORE_AUDIT_PREFIX + supplier.getSupplierName(),
                toJson(beforeData),
                toJson(afterData)
        ));
    }

    private Map<Long, SupplierScoreAuditSnapshot> queryLatestScoreSnapshots(List<Long> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(supplierIds.size(), "?"));
        String sql = """
                SELECT target_id, after_data, created_at
                  FROM sys_audit_log
                 WHERE module_code = ?
                   AND result = 'success'
                   AND target_id IN (%s)
                   AND operation_desc LIKE ?
                 ORDER BY target_id ASC, created_at DESC, id DESC
                """.formatted(placeholders);

        List<Object> params = new ArrayList<>();
        params.add(MODULE_CODE_SUPPLIER);
        params.addAll(supplierIds);
        params.add(SCORE_AUDIT_PREFIX + "%");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<Long, SupplierScoreAuditSnapshot> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long targetId = toLong(row.get("target_id"));
            if (targetId == null || result.containsKey(targetId)) {
                continue;
            }
            SupplierScoreAuditSnapshot snapshot = parseSnapshot(row.get("after_data"), row.get("created_at"));
            if (snapshot != null) {
                result.put(targetId, snapshot);
            }
        }
        return result;
    }

    private SupplierScoreAuditSnapshot parseSnapshot(Object afterData, Object createdAt) {
        if (afterData == null) {
            return null;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(String.valueOf(afterData), new TypeReference<Map<String, Object>>() {
            });
            String scoreUpdatedAt = asString(payload.get("scoreUpdatedAt"));
            if (StrUtil.isBlank(scoreUpdatedAt)) {
                scoreUpdatedAt = formatDateTime(toLocalDateTime(createdAt));
            }
            return new SupplierScoreAuditSnapshot(
                    scoreUpdatedAt,
                    asString(payload.get("scoreStatisticsPeriod")),
                    asString(payload.get("aiLevel")),
                    asString(payload.get("recommendPriority")),
                    asString(payload.get("riskWarningLevel")),
                    toStringList(payload.get("optimizationSuggestions")),
                    toBoolean(payload.get("scoreQualitySampleInsufficient")),
                    toBoolean(payload.get("scorePriceSampleInsufficient")),
                    toBoolean(payload.get("scoreDeliverySampleInsufficient"))
            );
        } catch (Exception ex) {
            log.warn("解析供应商评分审计快照失败: {}", ex.getMessage());
            return null;
        }
    }

    private Map<Long, List<ReceiptObservation>> queryReceiptObservations(List<Long> supplierIds, LocalDate periodStart) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(supplierIds.size(), "?"));
        String sql = """
                SELECT supplier_id, result, reject_reason, remark
                  FROM scm_receipt_record
                 WHERE supplier_id IN (%s)
                   AND inspected_at >= ?
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(supplierIds);
        params.add(Timestamp.valueOf(periodStart.atStartOfDay()));

        return jdbcTemplate.query(sql, params.toArray(), rs -> {
            Map<Long, List<ReceiptObservation>> result = new HashMap<>();
            while (rs.next()) {
                Long supplierId = rs.getLong("supplier_id");
                result.computeIfAbsent(supplierId, key -> new ArrayList<>())
                        .add(new ReceiptObservation(
                                rs.getString("result"),
                                rs.getString("reject_reason"),
                                rs.getString("remark")
                        ));
            }
            return result;
        });
    }

    private Map<Long, List<PriceObservation>> queryPriceObservations(List<Long> supplierIds, LocalDate periodStart) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(supplierIds.size(), "?"));
        String statusPlaceholders = String.join(",", Collections.nCopies(INVALID_ORDER_STATUSES.size(), "?"));
        String sql = """
                SELECT po.supplier_id,
                       poi.material_id,
                       poi.unit_price,
                       po.order_date
                  FROM scm_purchase_order_item poi
                  JOIN scm_purchase_order po ON po.id = poi.order_id
                 WHERE po.deleted = 0
                   AND po.supplier_id IN (%s)
                   AND po.order_date >= ?
                   AND po.status NOT IN (%s)
                   AND poi.unit_price IS NOT NULL
                   AND poi.unit_price > 0
                """.formatted(placeholders, statusPlaceholders);
        List<Object> params = new ArrayList<>(supplierIds);
        params.add(periodStart);
        params.addAll(INVALID_ORDER_STATUSES);

        return jdbcTemplate.query(sql, params.toArray(), rs -> {
            Map<Long, List<PriceObservation>> result = new HashMap<>();
            while (rs.next()) {
                Long supplierId = rs.getLong("supplier_id");
                result.computeIfAbsent(supplierId, key -> new ArrayList<>())
                        .add(new PriceObservation(
                                rs.getLong("material_id"),
                                rs.getBigDecimal("unit_price"),
                                toLocalDate(rs.getObject("order_date"))
                        ));
            }
            return result;
        });
    }

    private Map<Long, List<DeliveryObservation>> queryDeliveryObservations(List<Long> supplierIds, LocalDate periodStart) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(supplierIds.size(), "?"));
        String statusPlaceholders = String.join(",", Collections.nCopies(INVALID_DELIVERY_ORDER_STATUSES.size(), "?"));
        String sql = """
                SELECT supplier_id,
                       order_no,
                       status,
                       expected_delivery_at,
                       COALESCE(actual_delivery_at, arrived_at) AS actual_delivery_at,
                       logistics_remark
                  FROM scm_purchase_order
                 WHERE deleted = 0
                   AND supplier_id IN (%s)
                   AND order_date >= ?
                   AND status NOT IN (%s)
                """.formatted(placeholders, statusPlaceholders);
        List<Object> params = new ArrayList<>(supplierIds);
        params.add(periodStart);
        params.addAll(INVALID_DELIVERY_ORDER_STATUSES);

        return jdbcTemplate.query(sql, params.toArray(), rs -> {
            Map<Long, List<DeliveryObservation>> result = new HashMap<>();
            while (rs.next()) {
                Long supplierId = rs.getLong("supplier_id");
                result.computeIfAbsent(supplierId, key -> new ArrayList<>())
                        .add(new DeliveryObservation(
                                rs.getString("order_no"),
                                rs.getString("status"),
                                toLocalDateTime(rs.getObject("expected_delivery_at")),
                                toLocalDateTime(rs.getObject("actual_delivery_at")),
                                rs.getString("logistics_remark")
                        ));
            }
            return result;
        });
    }

    private boolean isNormalReturnObservation(ReceiptObservation observation) {
        String text = mergeText(observation.rejectReason(), observation.remark());
        return text.contains("退货") || text.contains("换货");
    }

    private boolean hasSevereQualityRisk(ReceiptObservation observation) {
        return containsAnyKeyword(mergeText(observation.rejectReason(), observation.remark()), QUALITY_SEVERE_KEYWORDS);
    }

    private boolean hasSevereDeliveryRisk(DeliveryObservation observation) {
        if (observation.expectedDeliveryAt() == null || observation.actualDeliveryAt() == null) {
            return false;
        }
        if (observation.actualDeliveryAt().isAfter(observation.expectedDeliveryAt().plusDays(3))) {
            return true;
        }
        return containsAnyKeyword(mergeText(observation.logisticsRemark()), DELIVERY_SEVERE_KEYWORDS);
    }

    private DeliveryAssessment assessDeliveryObservation(DeliveryObservation observation) {
        LinkedHashSet<String> sceneTypes = new LinkedHashSet<>();
        if (observation == null) {
            sceneTypes.add("缺少履约观测数据");
            return new DeliveryAssessment(false, false, false, sceneTypes, "不纳入统计", "采购订单缺少履约观测数据");
        }

        if (isDeliveryAssessmentExcludedStatus(observation.status())) {
            sceneTypes.add("中途取消/作废/终止");
            return new DeliveryAssessment(false, false, false, sceneTypes, "不纳入统计", "采购订单状态不属于履约准时率有效统计范围");
        }

        if (containsAnyKeyword(observation.logisticsRemark(), DELIVERY_SPLIT_KEYWORDS)) {
            sceneTypes.add("分批送达/部分签收");
        }
        if (containsAnyKeyword(observation.logisticsRemark(), DELIVERY_REPLENISH_KEYWORDS)) {
            sceneTypes.add("补送");
        }
        if (containsAnyKeyword(observation.logisticsRemark(), DELIVERY_CHANGE_KEYWORDS)) {
            sceneTypes.add("改单/承诺到货时间变更");
        }

        if (observation.expectedDeliveryAt() == null) {
            sceneTypes.add("缺承诺到货时间");
            return new DeliveryAssessment(false, false, false, sceneTypes, "不纳入统计", "采购订单未维护承诺到货时间，不计入履约准时率分母和分子");
        }

        if (observation.actualDeliveryAt() == null) {
            sceneTypes.add("缺实际签收时间");
            return new DeliveryAssessment(true, false, false, sceneTypes, "计入逾期订单", "采购订单已存在承诺到货时间但缺少实际签收时间，按逾期订单计分");
        }

        if (observation.actualDeliveryAt().isBefore(observation.expectedDeliveryAt())) {
            sceneTypes.add("提前到货");
        }

        boolean severeRisk = hasSevereDeliveryRisk(observation);
        if (severeRisk) {
            sceneTypes.add("严重逾期/严重履约风险");
        }

        boolean onTime = !observation.actualDeliveryAt().isAfter(observation.expectedDeliveryAt());
        return new DeliveryAssessment(
                true,
                onTime,
                severeRisk,
                sceneTypes,
                onTime ? "计入准时订单" : "计入逾期订单",
                buildDeliveryAssessmentReason(sceneTypes, onTime)
        );
    }

    private boolean isDeliveryAssessmentExcludedStatus(String status) {
        return StrUtil.isNotBlank(status) && INVALID_DELIVERY_ORDER_STATUSES.contains(status.toLowerCase(Locale.ROOT));
    }

    private String buildDeliveryAssessmentReason(Set<String> sceneTypes, boolean onTime) {
        if (sceneTypes == null || sceneTypes.isEmpty()) {
            return null;
        }
        List<String> reasons = new ArrayList<>();
        if (sceneTypes.contains("分批送达/部分签收")) {
            reasons.add("按采购订单维度使用最后一次完整签收时间判定，不按物料分批单独计分");
        }
        if (sceneTypes.contains("补送")) {
            reasons.add("补送依附原采购订单履约时间判定，不新增独立有效订单样本");
        }
        if (sceneTypes.contains("改单/承诺到货时间变更")) {
            reasons.add("以采购订单当前最新承诺到货时间作为履约判定基准");
        }
        if (sceneTypes.contains("提前到货")) {
            reasons.add("实际签收时间早于承诺到货时间，按准时送达计入");
        }
        if (sceneTypes.contains("严重逾期/严重履约风险")) {
            reasons.add("命中严重履约逾期清零规则判定条件");
        }
        if (reasons.isEmpty()) {
            return onTime ? "按订单履约准时率标准口径计分" : "按订单履约逾期标准口径计分";
        }
        return String.join("；", reasons);
    }

    private void logDeliveryAssessment(DeliveryObservation observation, DeliveryAssessment assessment) {
        log.info(
                "供应商AI评分履约场景判定: orderNo={}, status={}, sceneTypes={}, expectedDeliveryAt={}, actualDeliveryAt={}, result={}, reason={}",
                observation != null ? observation.orderNo() : null,
                observation != null ? observation.status() : null,
                assessment.sceneTypes().isEmpty() ? "普通场景" : String.join("、", assessment.sceneTypes()),
                observation != null ? formatDateTime(observation.expectedDeliveryAt()) : null,
                observation != null ? formatDateTime(observation.actualDeliveryAt()) : null,
                assessment.result(),
                assessment.reason()
        );
    }

    private BigDecimal resolveRateScore(BigDecimal rate, int maxScore, int goodScore, int mediumScore, int warningScore, int lowerMaxScore) {
        if (rate.compareTo(BigDecimal.valueOf(0.98)) >= 0 && maxScore == 25) {
            return BigDecimal.valueOf(maxScore);
        }
        if (rate.compareTo(BigDecimal.ONE) >= 0 && maxScore == 20) {
            return BigDecimal.valueOf(maxScore);
        }
        if (rate.compareTo(BigDecimal.valueOf(0.95)) >= 0) {
            return BigDecimal.valueOf(goodScore);
        }
        if (rate.compareTo(BigDecimal.valueOf(0.90)) >= 0) {
            return BigDecimal.valueOf(mediumScore);
        }
        if (rate.compareTo(BigDecimal.valueOf(0.85)) >= 0) {
            return BigDecimal.valueOf(warningScore);
        }
        BigDecimal fallback = rate.multiply(BigDecimal.valueOf(lowerMaxScore))
                .divide(BigDecimal.valueOf(0.85), 4, RoundingMode.HALF_UP);
        return fallback.max(BigDecimal.ZERO).min(BigDecimal.valueOf(lowerMaxScore));
    }

    private BigDecimal resolvePriceFluctuationScore(BigDecimal fluctuation) {
        if (fluctuation.compareTo(BigDecimal.valueOf(0.03)) <= 0) {
            return BigDecimal.valueOf(25);
        }
        if (fluctuation.compareTo(BigDecimal.valueOf(0.05)) <= 0) {
            return BigDecimal.valueOf(20);
        }
        if (fluctuation.compareTo(BigDecimal.valueOf(0.08)) <= 0) {
            return BigDecimal.valueOf(15);
        }
        if (fluctuation.compareTo(BigDecimal.valueOf(0.12)) <= 0) {
            return BigDecimal.valueOf(8);
        }
        BigDecimal remaining = BigDecimal.valueOf(0.20).subtract(fluctuation).max(BigDecimal.ZERO);
        return remaining.multiply(BigDecimal.valueOf(5))
                .divide(BigDecimal.valueOf(0.08), 4, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(5));
    }

    private boolean hasThreeConsecutiveStableMonths(Map<YearMonth, BigDecimal> monthlyAverageMap) {
        List<Map.Entry<YearMonth, BigDecimal>> entries = monthlyAverageMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (int i = 2; i < entries.size(); i++) {
            if (isConsecutive(entries.get(i - 2).getKey(), entries.get(i - 1).getKey(), entries.get(i).getKey())
                    && isEqualPrice(entries.get(i - 2).getValue(), entries.get(i - 1).getValue())
                    && isEqualPrice(entries.get(i - 1).getValue(), entries.get(i).getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasThreeConsecutiveIncreasingMonths(Map<YearMonth, BigDecimal> monthlyAverageMap) {
        List<Map.Entry<YearMonth, BigDecimal>> entries = monthlyAverageMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (int i = 2; i < entries.size(); i++) {
            if (!isConsecutive(entries.get(i - 2).getKey(), entries.get(i - 1).getKey(), entries.get(i).getKey())) {
                continue;
            }
            BigDecimal first = entries.get(i - 2).getValue();
            BigDecimal second = entries.get(i - 1).getValue();
            BigDecimal third = entries.get(i).getValue();
            if (second.compareTo(first) > 0 && third.compareTo(second) > 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal averageMonthlyPrice(List<PriceObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalPrice = observations.stream()
                .map(PriceObservation::unitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal result = safeDivide(totalPrice, observations.size());
        return result == null ? BigDecimal.ZERO : result;
    }

    private boolean isConsecutive(YearMonth first, YearMonth second, YearMonth third) {
        return first.plusMonths(1).equals(second) && second.plusMonths(1).equals(third);
    }

    private boolean isEqualPrice(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.subtract(right).abs().compareTo(BigDecimal.valueOf(0.01)) <= 0;
    }

    private BigDecimal safeDivide(BigDecimal numerator, long denominator) {
        if (numerator == null || denominator <= 0) {
            return null;
        }
        return numerator.divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private String resolveAiLevel(BigDecimal overallScore) {
        BigDecimal score = defaultScore(overallScore);
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "A";
        }
        if (score.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "B";
        }
        if (score.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "C";
        }
        return "D";
    }

    private String resolveRecommendPriority(BigDecimal overallScore) {
        BigDecimal score = defaultScore(overallScore);
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "优先推荐";
        }
        if (score.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "重点推荐";
        }
        if (score.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "常规推荐";
        }
        return "谨慎选择";
    }

    private String resolveRiskWarningLevel(BigDecimal overallScore, boolean severeRisk, boolean dataInsufficient, boolean deliveryInsufficient) {
        BigDecimal score = defaultScore(overallScore);
        if (severeRisk || score.compareTo(BigDecimal.valueOf(60)) < 0) {
            return "high";
        }
        if (dataInsufficient || deliveryInsufficient || score.compareTo(BigDecimal.valueOf(80)) < 0) {
            return "medium";
        }
        return "low";
    }

    private List<String> resolveOptimizationSuggestions(
            DimensionResult qualificationResult,
            DimensionResult qualityResult,
            DimensionResult priceResult,
            DimensionResult deliveryResult,
            BigDecimal overallScore
    ) {
        List<String> suggestions = new ArrayList<>();
        if (qualificationResult.severeRisk()) {
            suggestions.add("关键资质已过期，请优先补齐并更新证照后再参与核心采购。");
        } else if (qualificationResult.score().compareTo(BigDecimal.valueOf(24)) < 0) {
            suggestions.add("建议补齐基础档案、检疫证明与合格检测报告，提升资质完整性。");
        }

        if (qualityResult.sampleInsufficient()) {
            suggestions.add("历史供货质量数据样本不足，建议规范收货验收与异常记录。");
        } else if (qualityResult.severeRisk()) {
            suggestions.add("近6个月存在严重质量风险，请立即复核不合格批次并闭环整改。");
        } else if (qualityResult.score().compareTo(BigDecimal.valueOf(18)) < 0) {
            suggestions.add("供货质量得分偏低，建议加强到货验收与退换货管理。");
        }

        if (priceResult.sampleInsufficient()) {
            suggestions.add("价格稳定性数据样本不足，建议补充近6个月有效采购订单。");
        } else if (priceResult.score().compareTo(BigDecimal.valueOf(18)) < 0) {
            suggestions.add("近6个月主材价格波动偏大，建议重点关注议价与比价策略。");
        }

        if (deliveryResult.sampleInsufficient()) {
            suggestions.add("履约准时率数据样本不足，建议补充预计到货与实际到货时间。");
        } else if (deliveryResult.severeRisk()) {
            suggestions.add("存在严重履约逾期风险，请重点跟踪交付稳定性与供餐保障能力。");
        } else if (deliveryResult.score().compareTo(BigDecimal.valueOf(12)) < 0) {
            suggestions.add("履约准时率偏低，建议加强配送时效监控与异常预警。");
        }

        if (suggestions.isEmpty() && defaultScore(overallScore).compareTo(BigDecimal.valueOf(90)) >= 0) {
            suggestions.add("近6个月综合表现稳定，可作为优先采购候选供应商。");
        }
        return suggestions;
    }

    private Map<String, Object> buildSummaryItem(Supplier supplier, SupplierScoreComputation computation) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("supplierId", supplier.getId());
        item.put("supplierCode", supplier.getSupplierCode());
        item.put("supplierName", supplier.getSupplierName());
        item.put("overallScore", computation.overallScore());
        item.put("riskWarningLevel", computation.riskWarningLevel());
        return item;
    }

    private Map<String, Object> buildFailureSummaryItem(Supplier supplier, String errorMessage) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("supplierId", supplier.getId());
        item.put("supplierCode", supplier.getSupplierCode());
        item.put("supplierName", supplier.getSupplierName());
        item.put("error", errorMessage);
        return item;
    }

    private Set<String> extractQualificationFileNames(String qualificationFilesJson) {
        if (StrUtil.isBlank(qualificationFilesJson)) {
            return Collections.emptySet();
        }
        try {
            List<SupplierQualificationFileVO> files = objectMapper.readValue(
                    qualificationFilesJson,
                    new TypeReference<List<SupplierQualificationFileVO>>() {
                    }
            );
            return files.stream()
                    .map(SupplierQualificationFileVO::getName)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception ex) {
            log.warn("解析供应商资质文件失败: supplierFiles={}", qualificationFilesJson);
            return Collections.emptySet();
        }
    }

    private boolean containsAnyKeyword(Set<String> values, Set<String> keywords) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        return values.stream().anyMatch(value -> containsAnyKeyword(value, keywords));
    }

    private boolean containsAnyKeyword(String value, Set<String> keywords) {
        if (StrUtil.isBlank(value) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(keyword -> normalized.contains(keyword.toLowerCase(Locale.ROOT)));
    }

    private boolean isExpired(LocalDateTime value, LocalDateTime now) {
        return value != null && value.isBefore(now);
    }

    private String mergeText(String... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        return java.util.Arrays.stream(values)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(" "));
    }

    private String buildStatisticsPeriod(LocalDate start, LocalDate end) {
        return "近6个月滚动数据（" + start.format(DATE_FORMATTER) + " 至 " + end.format(DATE_FORMATTER) + "）";
    }

    private void runWithSystemContextIfAbsent(Supplier supplier, Runnable action) {
        if (UserContext.get() != null) {
            action.run();
            return;
        }
        UserContext context = new UserContext();
        context.setUserId(0L);
        context.setUsername("system");
        context.setRealName("系统任务");
        context.setOrgId(supplier.getOrgId());
        context.setTenantId(supplier.getTenantId());
        UserContext.set(context);
        try {
            action.run();
        } finally {
            UserContext.clear();
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("序列化 AI 评分数据失败: {}", ex.getMessage());
            return null;
        }
    }

    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return Collections.emptyList();
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return Boolean.FALSE;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        return LocalDateTime.parse(String.valueOf(value), DATE_TIME_FORMATTER);
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value), DATE_FORMATTER);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private BigDecimal scaleScore(BigDecimal value) {
        return defaultScore(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultScore(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record ReceiptObservation(String result, String rejectReason, String remark) {
    }

    private record PriceObservation(Long materialId, BigDecimal unitPrice, LocalDate orderDate) {
    }

    private record DeliveryObservation(String orderNo, String status, LocalDateTime expectedDeliveryAt, LocalDateTime actualDeliveryAt, String logisticsRemark) {
    }

    private record DeliveryAssessment(
            boolean validSample,
            boolean onTime,
            boolean severeRisk,
            Set<String> sceneTypes,
            String result,
            String reason
    ) {
        private boolean shouldLog() {
            return sceneTypes != null && !sceneTypes.isEmpty();
        }
    }

    private record DimensionResult(BigDecimal score, boolean sampleInsufficient, boolean severeRisk, String note) {
    }

    private record SupplierScoreComputation(
            BigDecimal overallScore,
            BigDecimal qualificationScore,
            BigDecimal qualityScore,
            BigDecimal priceScore,
            BigDecimal deliveryScore,
            String aiLevel,
            String recommendPriority,
            String riskWarningLevel,
            List<String> optimizationSuggestions,
            boolean qualificationSampleInsufficient,
            boolean qualitySampleInsufficient,
            boolean priceSampleInsufficient,
            boolean deliverySampleInsufficient,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
    }

    private record SupplierScoreAuditSnapshot(
            String scoreUpdatedAt,
            String scoreStatisticsPeriod,
            String aiLevel,
            String recommendPriority,
            String riskWarningLevel,
            List<String> optimizationSuggestions,
            Boolean scoreQualitySampleInsufficient,
            Boolean scorePriceSampleInsufficient,
            Boolean scoreDeliverySampleInsufficient
    ) {
    }
}
