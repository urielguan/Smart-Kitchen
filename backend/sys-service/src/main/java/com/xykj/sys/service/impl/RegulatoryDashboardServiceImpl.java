package com.xykj.sys.service.impl;

import com.xykj.common.annotation.DataScope;
import com.xykj.sys.dto.RegulatoryDashboardQueryDTO;
import com.xykj.sys.service.RegulatoryDashboardService;
import com.xykj.sys.vo.RegulatoryDashboardDataVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 监管看板首页服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatoryDashboardServiceImpl implements RegulatoryDashboardService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal VIOLATION_TARGET = BigDecimal.valueOf(2);
    private static final BigDecimal WASTE_TARGET = BigDecimal.valueOf(2);
    private static final BigDecimal TRACE_TARGET_MINUTES = BigDecimal.valueOf(15);
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HM_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MD_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private final JdbcTemplate jdbcTemplate;

    @Override
    @DataScope
    public RegulatoryDashboardDataVO getHomeSnapshot(RegulatoryDashboardQueryDTO query) {
        return buildLiveSnapshot(query);
    }

    private RegulatoryDashboardDataVO buildLiveSnapshot(RegulatoryDashboardQueryDTO query) {
        DateWindow window = resolveWindow(query);
        List<Long> orgIds = resolveOrgIds(query);
        RegulatoryDashboardDataVO result = new RegulatoryDashboardDataVO();
        String now = DATETIME_FMT.format(LocalDateTime.now());
        result.setSnapshotAt(now);
        result.setLastUpdatedAt(now);
        result.setOverviewMetrics(buildOverviewMetrics(window, orgIds));
        result.setDomainSections(buildDomainSections(window, orgIds));
        result.setRiskEvents(buildRiskEvents(window, orgIds, query));
        result.setTrendSeries(buildTrendSeries(window, orgIds, query.getQuickRange()));
        result.setAlarmDistribution(buildAlarmDistribution(window, orgIds));
        result.setExecutionSeries(buildExecutionSeries(window, orgIds, query.getQuickRange()));
        result.setServiceQuality(buildServiceQuality(window, orgIds));
        result.setHeatCards(buildHeatCards(window, orgIds));
        result.setReportTemplates(loadReportTemplates(orgIds));
        result.setExternalShares(loadExternalShares(orgIds));
        result.setApiSubscriptions(loadApiSubscriptions(orgIds));
        return result;
    }

    private List<RegulatoryDashboardDataVO.OverviewMetric> buildOverviewMetrics(DateWindow window, List<Long> orgIds) {
        long dinerCount = queryForLong(
                "SELECT COALESCE(SUM(expected_count), 0) FROM recipe_plan WHERE deleted = 0 AND plan_date BETWEEN ? AND ? " +
                        "AND status IN ('approved','cooking','completed')" + orgCondition("org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id")
        );
        long reviewCount = queryForLong(
                "SELECT COUNT(*) FROM sys_meal_review WHERE deleted = 0 AND review_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id")
        );
        long reviewedEmployees = queryForLong(
                "SELECT COUNT(DISTINCT employee_id) FROM sys_meal_review WHERE deleted = 0 AND review_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id")
        );
        long recipeCount = queryForLong(
                "SELECT COUNT(DISTINCT i.recipe_id) " +
                        "FROM recipe_plan_item i " +
                        "JOIN recipe_plan p ON p.id = i.plan_id AND p.deleted = 0 " +
                        "WHERE i.deleted = 0 AND p.plan_date BETWEEN ? AND ? " +
                        "AND p.status IN ('approved','cooking','completed')" + orgCondition("p.org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "p.org_id")
        );
        long recipeItemCount = queryForLong(
                "SELECT COUNT(*) " +
                        "FROM recipe_plan_item i " +
                        "JOIN recipe_plan p ON p.id = i.plan_id AND p.deleted = 0 " +
                        "WHERE i.deleted = 0 AND p.plan_date BETWEEN ? AND ? " +
                        "AND p.status IN ('approved','cooking','completed')" + orgCondition("p.org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "p.org_id")
        );
        long cookCount = queryForLong(
                "SELECT COUNT(*) " +
                        "FROM cook_task t " +
                        "JOIN recipe_plan p ON p.id = t.plan_id AND p.deleted = 0 " +
                        "WHERE t.deleted = 0 AND p.plan_date BETWEEN ? AND ?" + orgCondition("p.org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "p.org_id")
        );
        long cookOvertime = queryForLong(
                "SELECT COUNT(*) " +
                        "FROM cook_task t " +
                        "JOIN recipe_plan p ON p.id = t.plan_id AND p.deleted = 0 " +
                        "WHERE t.deleted = 0 AND t.cooking_duration > 90 AND p.plan_date BETWEEN ? AND ?" + orgCondition("p.org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "p.org_id")
        );
        long sampleCount = queryForLong(
                "SELECT COUNT(*) FROM sample_record WHERE deleted = 0 AND sample_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id")
        );
        long disposedCount = queryForLong(
                "SELECT COUNT(*) FROM sample_record WHERE deleted = 0 AND status IN ('disposed','archived') AND disposal_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withTimeArgs(orgIds, window)
        );
        long morningCount = queryForLong(
                "SELECT COUNT(*) FROM health_check_record WHERE check_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id")
        );
        long morningAbnormal = queryForLong(
                "SELECT COUNT(*) FROM health_check_record WHERE check_result = 'fail' AND check_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id")
        );
        long totalDevices = queryForLong(
                "SELECT COUNT(*) FROM device_info WHERE deleted = 0 AND status = 'active'" + orgCondition("org_id", orgIds),
                withOrgArgs(orgIds)
        );
        long onlineDevices = queryForLong(
                "SELECT COUNT(*) FROM device_info WHERE deleted = 0 AND status = 'active' AND online_status = 'online'" + orgCondition("org_id", orgIds),
                withOrgArgs(orgIds)
        );
        long abnormalDevices = totalDevices - onlineDevices;
        long alertCount = queryForLong(
                "SELECT COUNT(*) FROM device_alert WHERE deleted = 0 AND triggered_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withTimeArgs(orgIds, window)
        );
        long urgentAlerts = queryForLong(
                "SELECT COUNT(*) FROM device_alert WHERE deleted = 0 AND alert_level IN ('critical','error','urgent','danger') AND triggered_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withTimeArgs(orgIds, window)
        );
        long complaintTotal = queryForLong(
                "SELECT COUNT(*) FROM sys_complaint WHERE deleted = 0 AND created_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withTimeArgs(orgIds, window)
        );
        long complaintClosed = queryForLong(
                "SELECT COUNT(*) FROM sys_complaint WHERE deleted = 0 AND status = 'closed' AND created_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withTimeArgs(orgIds, window)
        );
        BigDecimal complaintClosedRate = percent(complaintClosed, complaintTotal);
        long goodReviewCount = queryForLong(
                "SELECT COUNT(*) FROM sys_meal_review WHERE deleted = 0 AND overall_score >= 4 AND review_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id")
        );
        BigDecimal goodReviewRate = percent(goodReviewCount, reviewCount);

        List<RegulatoryDashboardDataVO.OverviewMetric> list = new ArrayList<>();
        list.add(metric("diner", "今日就餐人数", formatNumber(dinerCount), null, "评价员工 " + formatNumber(reviewedEmployees) + " 人", dinerCount > 0 ? "正常" : "预警", "就餐与排餐"));
        list.add(metric("recipe", "菜谱执行数", formatNumber(recipeCount), "个", "排餐明细 " + formatNumber(recipeItemCount) + " 项", recipeCount > 0 ? "正常" : "预警", "菜谱与烹饪"));
        list.add(metric("cook", "烹饪任务数", formatNumber(cookCount), "项", "超时 " + formatNumber(cookOvertime) + " 项", cookOvertime > 0 ? "预警" : "正常", "烹饪记录"));
        list.add(metric("sample", "留样 / 销样数", formatNumber(sampleCount) + " / " + formatNumber(disposedCount), null, "销样及时率 " + formatPercent(percent(disposedCount, sampleCount)), sampleCount == 0 || sampleCount <= disposedCount ? "正常" : "预警", "留样管理"));
        list.add(metric("morning", "晨检人数", formatNumber(morningCount), "人", "异常 " + formatNumber(morningAbnormal) + " 人", morningAbnormal > 0 ? "异常" : "正常", "晨检系统"));
        list.add(metric("device", "设备在线率", formatDecimal(percent(onlineDevices, totalDevices)) , "%", "异常设备 " + formatNumber(abnormalDevices) + " 台", abnormalDevices > 0 ? "预警" : "正常", "设备状态"));
        list.add(metric("alarm", "今日告警总数", formatNumber(alertCount), "次", "紧急 " + formatNumber(urgentAlerts) + " 条", urgentAlerts > 0 ? "紧急" : alertCount > 0 ? "预警" : "正常", "告警中心"));
        list.add(metric("comment", "投诉闭环率", formatDecimal(complaintClosedRate), "%", "好评率 " + formatDecimal(goodReviewRate) + "%", complaintClosedRate.compareTo(BigDecimal.valueOf(90)) >= 0 ? "正常" : "预警", "评价申诉"));
        return list;
    }

    private List<RegulatoryDashboardDataVO.DomainSection> buildDomainSections(DateWindow window, List<Long> orgIds) {
        long shouldCheckEmployees = queryForLong(
                "SELECT COUNT(*) FROM health_check_task_linkage WHERE should_check = 1 AND check_date BETWEEN ? AND ?" + orgCondition("current_org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "current_org_id")
        );
        long morningCount = queryForLong("SELECT COUNT(*) FROM health_check_record WHERE check_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        long morningAbnormal = queryForLong("SELECT COUNT(*) FROM health_check_record WHERE check_result = 'fail' AND check_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        long certExpired = queryForLong("SELECT COUNT(*) FROM health_certificate WHERE deleted = 0 AND status = 'expired'" + orgCondition("org_id", orgIds), withOrgArgs(orgIds));
        BigDecimal morningRate = percent(morningCount, shouldCheckEmployees);

        long sampleTotal = queryForLong("SELECT COUNT(*) FROM sample_record WHERE deleted = 0 AND sample_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        long sampleCompliant = queryForLong("SELECT COUNT(*) FROM sample_record WHERE deleted = 0 AND status NOT IN ('pending_sample','overdue','voided') AND sample_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        long sampleMissing = queryForLong("SELECT COUNT(*) FROM sample_record WHERE deleted = 0 AND status = 'pending_sample' AND sample_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        long sampleDelayed = queryForLong("SELECT COUNT(*) FROM sample_record WHERE deleted = 0 AND status = 'overdue' AND sample_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        BigDecimal sampleRate = percent(sampleCompliant, sampleTotal);

        long tempTotal = queryForLong("SELECT COUNT(*) FROM cook_temperature_record WHERE record_time BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window, "record_time"));
        long tempAbnormal = queryForLong("SELECT COUNT(*) FROM cook_temperature_record WHERE abnormal = 1 AND record_time BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window, "record_time"));
        long cookOvertime = queryForLong(
                "SELECT COUNT(*) FROM cook_task t JOIN recipe_plan p ON p.id = t.plan_id AND p.deleted = 0 " +
                        "WHERE t.deleted = 0 AND t.cooking_duration > 90 AND p.plan_date BETWEEN ? AND ?" + orgCondition("p.org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "p.org_id")
        );
        int maxDeviation = queryForInt(
                "SELECT COALESCE(MAX(ABS(temperature - 75)), 0) FROM cook_temperature_record WHERE record_time BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withTimeArgs(orgIds, window, "record_time")
        );
        BigDecimal cookRate = percent(tempTotal - tempAbnormal, tempTotal);

        Map<String, Long> violationBreakdown = queryForMap(
                "SELECT alert_type, COUNT(*) AS cnt FROM device_alert WHERE deleted = 0 AND alert_type = 'ai_violation' AND triggered_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds) + " GROUP BY alert_type",
                withTimeArgs(orgIds, window)
        );
        long aiViolationCount = violationBreakdown.values().stream().mapToLong(Long::longValue).sum();
        long pendingAiViolationCount = queryForLong(
                "SELECT COUNT(*) FROM device_alert WHERE deleted = 0 AND alert_type = 'ai_violation' AND status IN ('pending','assigned','handling') AND triggered_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withTimeArgs(orgIds, window)
        );
        String aiHint = "累计 " + formatNumber(aiViolationCount) + " 次，待处置 " + formatNumber(pendingAiViolationCount) + " 条";

        long lowStock = queryForLong(
                "SELECT COUNT(*) FROM wms_inventory i LEFT JOIN wms_material m ON m.id = i.material_id " +
                        "WHERE (m.deleted = 0 OR m.id IS NULL) AND i.quantity < COALESCE(m.min_stock, 0) AND i.status <> 'expired'" + orgCondition("i.org_id", orgIds),
                withOrgArgs(orgIds)
        );
        long expiringStock = queryForLong(
                "SELECT COUNT(*) FROM wms_inventory WHERE expiry_date IS NOT NULL AND expiry_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds),
                withDateArgs(orgIds, LocalDate.now(), LocalDate.now().plusDays(7), "org_id")
        );
        long expiredStock = queryForLong(
                "SELECT COUNT(*) FROM wms_inventory WHERE expiry_date IS NOT NULL AND expiry_date < ?" + orgCondition("org_id", orgIds),
                withDateArgs(orgIds, LocalDate.now(), null, "org_id")
        );
        long ledgerTotal = queryForLong(
                "SELECT COUNT(*) FROM (" +
                        "SELECT id FROM wms_inbound_order WHERE deleted = 0 AND created_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds) +
                        " UNION ALL SELECT id FROM wms_outbound_order WHERE deleted = 0 AND created_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds) +
                        ") t",
                withDoubleTimeArgs(orgIds, window)
        );
        long ledgerWithItems = queryForLong(
                "SELECT COUNT(*) FROM (" +
                        "SELECT o.id FROM wms_inbound_order o WHERE o.deleted = 0 AND o.created_at BETWEEN ? AND ?" + orgCondition("o.org_id", orgIds) + " AND EXISTS (SELECT 1 FROM wms_inbound_order_item i WHERE i.inbound_id = o.id) " +
                        "UNION ALL " +
                        "SELECT o.id FROM wms_outbound_order o WHERE o.deleted = 0 AND o.created_at BETWEEN ? AND ?" + orgCondition("o.org_id", orgIds) + " AND EXISTS (SELECT 1 FROM wms_outbound_order_item i WHERE i.outbound_id = o.id) " +
                        ") t",
                withDoubleTimeArgs(orgIds, window)
        );
        BigDecimal ledgerRate = percentOrFull(ledgerWithItems, ledgerTotal);
        long traceableSamples = queryForLong(
                "SELECT COUNT(DISTINCT s.id) " +
                        "FROM sample_record s " +
                        "WHERE s.deleted = 0 AND s.sample_date BETWEEN ? AND ? " +
                        "AND s.trace_batch_id IS NOT NULL AND s.trace_batch_id <> '' " +
                        "AND EXISTS (SELECT 1 FROM wms_inventory i WHERE i.trace_batch_id = s.trace_batch_id AND i.org_id = s.org_id) " +
                        "AND EXISTS (SELECT 1 FROM scm_purchase_order p WHERE p.deleted = 0 AND p.trace_batch_id = s.trace_batch_id AND p.org_id = s.org_id)" +
                        orgCondition("s.org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "s.org_id")
        );
        BigDecimal traceCoverage = percent(traceableSamples, sampleTotal);

        long pendingAlerts = queryForLong("SELECT COUNT(*) FROM device_alert WHERE deleted = 0 AND status IN ('pending','assigned','handling') AND triggered_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window));
        long overtimeDispatch = queryForLong("SELECT COUNT(*) FROM device_alert_dispatch WHERE deleted = 0 AND deadline IS NOT NULL AND deadline < NOW() AND status IN ('pending','processing') AND created_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window));
        long dispatchTotal = queryForLong("SELECT COUNT(*) FROM device_alert_dispatch WHERE deleted = 0 AND created_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window));
        long dispatchCompleted = queryForLong("SELECT COUNT(*) FROM device_alert_dispatch WHERE deleted = 0 AND status IN ('completed','reviewed') AND created_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window));
        long dispatchReviewed = queryForLong("SELECT COUNT(*) FROM device_alert_dispatch WHERE deleted = 0 AND status = 'reviewed' AND created_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window));
        BigDecimal rectificationRate = percent(dispatchCompleted, dispatchTotal);
        BigDecimal reviewRate = percent(dispatchReviewed, dispatchCompleted);

        List<RegulatoryDashboardDataVO.DomainSection> sections = new ArrayList<>();
        sections.add(section(
                "食品安全专项监管",
                "晨检、留样、烹饪温度、AI违规识别",
                List.of(
                        domainMetric("晨检完成率", formatDecimal(morningRate) + "%", "应检 " + formatNumber(shouldCheckEmployees) + " 人，异常 " + formatNumber(morningAbnormal) + " 人，健康证过期 " + formatNumber(certExpired) + " 人", morningAbnormal > 0 ? "预警" : "正常"),
                        domainMetric("留样合规率", formatDecimal(sampleRate) + "%", "留样缺失 " + formatNumber(sampleMissing) + " 条，销样延迟 " + formatNumber(sampleDelayed) + " 条", sampleDelayed > 0 || sampleMissing > 0 ? "预警" : "正常"),
                        domainMetric("烹饪温度达标率", formatDecimal(cookRate) + "%", "超时烹饪 " + formatNumber(cookOvertime) + " 次，最高偏差 " + maxDeviation + "°C", tempAbnormal > 0 ? "异常" : "正常"),
                        domainMetric("AI违规识别趋势", formatNumber(aiViolationCount) + " 次", aiHint, aiViolationCount > 5 ? "紧急" : aiViolationCount > 0 ? "预警" : "正常")
                )));
        sections.add(section(
                "物资与库存监管",
                "库存预警、过期风险、台账完整性、采购溯源",
                List.of(
                        domainMetric("库存预警物料", formatNumber(lowStock) + " 项", "低库存 " + formatNumber(lowStock) + "，临期 " + formatNumber(expiringStock) + "，过期 " + formatNumber(expiredStock), expiredStock > 0 ? "异常" : lowStock > 0 ? "预警" : "正常"),
                        domainMetric("过期物料统计", formatNumber(expiredStock) + " 批次", "临期批次 " + formatNumber(expiringStock) + "，已过期 " + formatNumber(expiredStock), expiredStock > 0 ? "紧急" : expiringStock > 0 ? "预警" : "正常"),
                        domainMetric("出入库台账完整性", formatDecimal(ledgerRate) + "%", "缺失台账 " + formatNumber(Math.max(ledgerTotal - ledgerWithItems, 0)) + " 笔，待补录", ledgerTotal == 0 || ledgerRate.compareTo(BigDecimal.valueOf(99)) >= 0 ? "正常" : "预警"),
                        domainMetric("食材采购溯源覆盖率", formatDecimal(traceCoverage) + "%", "可穿透至采购单、库存批次、留样批次", sampleTotal == 0 || traceCoverage.compareTo(BigDecimal.valueOf(95)) >= 0 ? "正常" : "预警")
                )));
        sections.add(section(
                "告警与整改监管",
                "未处理告警、超时工单、整改完成率、复查通过率",
                List.of(
                        domainMetric("未处理告警", formatNumber(pendingAlerts) + " 条", "超时工单 " + formatNumber(overtimeDispatch) + " 条需优先督办", pendingAlerts > 5 ? "紧急" : pendingAlerts > 0 ? "预警" : "正常"),
                        domainMetric("超时未处理工单", formatNumber(overtimeDispatch) + " 单", "SLA 已超时，待负责人督办", overtimeDispatch > 0 ? "异常" : "正常"),
                        domainMetric("整改完成率", formatDecimal(rectificationRate) + "%", "本周期已闭环 " + formatNumber(dispatchCompleted) + " / " + formatNumber(dispatchTotal), dispatchTotal == 0 || rectificationRate.compareTo(BigDecimal.valueOf(90)) >= 0 ? "正常" : "预警"),
                        domainMetric("复查通过率", formatDecimal(reviewRate) + "%", "已复核通过 " + formatNumber(dispatchReviewed) + " 单", dispatchCompleted == 0 || reviewRate.compareTo(BigDecimal.valueOf(90)) >= 0 ? "正常" : "预警")
                )));
        return sections;
    }

    private List<RegulatoryDashboardDataVO.RiskEvent> buildRiskEvents(DateWindow window, List<Long> orgIds, RegulatoryDashboardQueryDTO query) {
        List<RegulatoryDashboardDataVO.RiskEvent> result = new ArrayList<>();

        String alertSql = "SELECT a.id, a.alert_no, a.alert_type, a.alert_level, a.alert_content, a.device_id, a.device_name, a.triggered_at, a.status, " +
                "a.assigned_to, a.handled_by, di.location_desc, " +
                "d.id AS dispatch_id, d.dispatch_no, d.handler_id, d.handler_name, d.deadline, d.priority, d.status AS dispatch_status " +
                "FROM device_alert a " +
                "LEFT JOIN device_info di ON di.id = a.device_id AND di.deleted = 0 " +
                "LEFT JOIN device_alert_dispatch d ON d.alert_id = a.id AND d.deleted = 0 " +
                "WHERE a.deleted = 0 AND a.triggered_at BETWEEN ? AND ? " + orgCondition("a.org_id", orgIds) +
                " ORDER BY " +
                "CASE " +
                "  WHEN COALESCE(d.status, '') IN ('pending','processing') OR a.status IN ('pending','assigned','handling') THEN 0 " +
                "  WHEN COALESCE(d.status, '') IN ('completed','reviewed') OR a.status IN ('handled','reviewed','closed') THEN 2 " +
                "  ELSE 1 " +
                "END ASC, " +
                "CASE " +
                "  WHEN a.alert_level IN ('critical','danger','urgent','error') THEN 0 " +
                "  WHEN a.alert_level = 'warning' THEN 1 " +
                "  ELSE 2 " +
                "END ASC, " +
                "a.triggered_at DESC LIMIT 8";
        List<Map<String, Object>> alertRows = jdbcTemplate.queryForList(alertSql, withTimeArgs(orgIds, window, "a.org_id").toArray());
        for (Map<String, Object> row : alertRows) {
            RegulatoryDashboardDataVO.RiskEvent event = new RegulatoryDashboardDataVO.RiskEvent();
            event.setId("alert-" + row.get("id"));
            event.setType(resolveAlertRiskType(asString(row.get("alert_type"))));
            event.setTitle(asString(row.get("alert_content")));
            event.setTraceBatchId(asString(row.get("alert_no")));
            event.setLevel(mapRiskLevel(asString(row.get("alert_level"))));
            event.setLocation(firstNonBlank(asString(row.get("location_desc")), asString(row.get("device_name")), "未标注位置"));
            event.setTime(formatDateTime(toDateTime(row.get("triggered_at"))));
            event.setStatus(mapRiskStatus(asString(row.get("status")), asString(row.get("dispatch_status"))));
            event.setOwner(firstNonBlank(
                    asString(row.get("handler_name")),
                    resolveEmployeeName(toLong(row.get("handler_id"))),
                    resolveEmployeeName(toLong(row.get("assigned_to"))),
                    resolveEmployeeName(toLong(row.get("handled_by"))),
                    "待指派"
            ));
            event.setSourceModule(resolveAlertSourceModule(asString(row.get("alert_type"))));
            event.setSourceTerminals(resolveAlertSourceTerminals(toLong(row.get("device_id"))));
            event.setConsistency("一致");
            event.setOvertime(isOvertime(toDateTime(row.get("deadline")), asString(row.get("dispatch_status"))));
            RegulatoryDashboardDataVO.DrillDown drillDown = new RegulatoryDashboardDataVO.DrillDown();
            drillDown.setType(row.get("dispatch_id") != null ? "dispatch" : "alert");
            drillDown.setAlertId(toLong(row.get("id")));
            drillDown.setAlertNo(asString(row.get("alert_no")));
            drillDown.setDispatchId(toLong(row.get("dispatch_id")));
            drillDown.setDispatchNo(asString(row.get("dispatch_no")));
            drillDown.setMetric(row.get("dispatch_id") != null ? "dispatches" : "alerts");
            drillDown.setTab(row.get("dispatch_id") != null ? "dispatches" : "alerts");
            drillDown.setStatus(row.get("dispatch_id") != null ? asString(row.get("dispatch_status")) : asString(row.get("status")));
            drillDown.setAlertLevel(asString(row.get("alert_level")));
            drillDown.setOverdue(event.isOvertime());
            event.setDrillDown(drillDown);
            result.add(event);
        }

        String sampleSql = "SELECT s.id, s.sample_no, s.trace_batch_id, s.menu_name, s.storage_location, s.record_origin_type, s.status, " +
                "s.created_at, s.sampled_at, s.disposal_due_at, s.sampled_by, e.real_name AS sampled_by_name, " +
                "o.org_name, " +
                "(SELECT GROUP_CONCAT(DISTINCT sol.terminal ORDER BY sol.created_at DESC SEPARATOR ',') FROM sample_operation_log sol WHERE sol.record_id = s.id) AS terminals " +
                "FROM sample_record s " +
                "LEFT JOIN sys_employee e ON e.id = s.sampled_by AND e.deleted = 0 " +
                "LEFT JOIN sys_organization o ON o.id = s.org_id AND o.deleted = 0 " +
                "WHERE s.deleted = 0 AND s.status IN ('pending_sample','overdue') AND s.created_at <= ? " +
                orgCondition("s.org_id", orgIds) +
                " ORDER BY " +
                "CASE WHEN s.status = 'overdue' THEN 0 ELSE 1 END ASC, " +
                "COALESCE(s.disposal_due_at, s.sampled_at, s.created_at) ASC LIMIT 3";
        List<Object> sampleArgs = new ArrayList<>();
        sampleArgs.add(Timestamp.valueOf(window.end));
        appendOrgArgs(sampleArgs, orgIds);
        List<Map<String, Object>> sampleRows = jdbcTemplate.queryForList(sampleSql, sampleArgs.toArray());
        for (Map<String, Object> row : sampleRows) {
            RegulatoryDashboardDataVO.RiskEvent event = new RegulatoryDashboardDataVO.RiskEvent();
            event.setId("sample-" + row.get("id"));
            String sampleStatus = asString(row.get("status"));
            event.setType(resolveSampleRiskType(sampleStatus));
            event.setTitle(resolveSampleRiskTitle(asString(row.get("menu_name")), sampleStatus));
            event.setTraceBatchId(firstNonBlank(asString(row.get("trace_batch_id")), asString(row.get("sample_no"))));
            event.setLevel("overdue".equals(sampleStatus) ? "紧急" : "严重");
            event.setLocation(resolveSampleLocation(asString(row.get("storage_location")), asString(row.get("org_name"))));
            event.setTime(formatDateTime(firstNonNullDateTime(
                    toDateTime(row.get("disposal_due_at")),
                    toDateTime(row.get("sampled_at")),
                    toDateTime(row.get("created_at"))
            )));
            event.setStatus(mapSampleRiskStatus(sampleStatus));
            event.setOwner(firstNonBlank(asString(row.get("sampled_by_name")), resolveEmployeeName(toLong(row.get("sampled_by"))), "待认领"));
            event.setSourceModule(resolveSampleSourceModule(asString(row.get("record_origin_type"))));
            event.setSourceTerminals(resolveSampleSourceTerminals(asString(row.get("terminals")), asString(row.get("record_origin_type"))));
            event.setConsistency(event.getTraceBatchId().isBlank() ? "待校验" : "一致");
            event.setOvertime("overdue".equals(sampleStatus));
            RegulatoryDashboardDataVO.DrillDown drillDown = new RegulatoryDashboardDataVO.DrillDown();
            drillDown.setType("sample");
            drillDown.setRecordId(toLong(row.get("id")));
            drillDown.setTraceBatchId(event.getTraceBatchId());
            event.setDrillDown(drillDown);
            result.add(event);
        }

        String invSql = "SELECT i.id, i.trace_batch_id, i.material_name, i.batch_no, i.expiry_date, i.status, i.source_type, i.quantity, i.unit, " +
                "w.warehouse_name, l.location_name, w.manager_name " +
                "FROM wms_inventory i " +
                "LEFT JOIN wms_warehouse w ON w.id = i.warehouse_id AND w.deleted = 0 " +
                "LEFT JOIN wms_location l ON l.id = i.location_id AND l.deleted = 0 " +
                "WHERE i.status IN ('warning','expired') " + orgCondition("i.org_id", orgIds) +
                " ORDER BY CASE WHEN i.status = 'expired' THEN 0 ELSE 1 END ASC, i.expiry_date ASC LIMIT 3";
        List<Map<String, Object>> invRows = jdbcTemplate.queryForList(invSql, withOrgArgs(orgIds).toArray());
        for (Map<String, Object> row : invRows) {
            RegulatoryDashboardDataVO.RiskEvent event = new RegulatoryDashboardDataVO.RiskEvent();
            event.setId("inv-" + row.get("id"));
            event.setType("库存风险");
            event.setTitle(resolveInventoryRiskTitle(asString(row.get("material_name")), asString(row.get("batch_no")), asString(row.get("status"))));
            event.setTraceBatchId(firstNonBlank(asString(row.get("trace_batch_id")), asString(row.get("batch_no"))));
            event.setLevel("expired".equals(asString(row.get("status"))) ? "紧急" : "一般");
            event.setLocation(resolveInventoryLocation(
                    asString(row.get("warehouse_name")),
                    asString(row.get("location_name")),
                    asString(row.get("batch_no"))
            ));
            event.setTime(formatDate(row.get("expiry_date")));
            event.setStatus(mapInventoryRiskStatus(asString(row.get("status"))));
            event.setOwner(firstNonBlank(asString(row.get("manager_name")), "待认领"));
            event.setSourceModule(resolveInventorySourceModule(asString(row.get("source_type"))));
            event.setSourceTerminals(List.of("Web"));
            event.setConsistency("一致");
            event.setOvertime("expired".equals(asString(row.get("status"))));
            RegulatoryDashboardDataVO.DrillDown drillDown = new RegulatoryDashboardDataVO.DrillDown();
            drillDown.setType("purchase");
            drillDown.setTraceBatchId(event.getTraceBatchId());
            event.setDrillDown(drillDown);
            result.add(event);
        }

        return applyLocationFilters(result, query).stream()
                .sorted((left, right) -> {
                    int statusCompare = Integer.compare(riskEventStatusOrder(left.getStatus()), riskEventStatusOrder(right.getStatus()));
                    if (statusCompare != 0) {
                        return statusCompare;
                    }
                    int overtimeCompare = Boolean.compare(right.isOvertime(), left.isOvertime());
                    if (overtimeCompare != 0) {
                        return overtimeCompare;
                    }
                    int levelCompare = Integer.compare(riskLevelOrder(left.getLevel()), riskLevelOrder(right.getLevel()));
                    if (levelCompare != 0) {
                        return levelCompare;
                    }
                    LocalDateTime leftTime = parseDateTimeSilently(left.getTime());
                    LocalDateTime rightTime = parseDateTimeSilently(right.getTime());
                    if (leftTime == null && rightTime == null) {
                        return 0;
                    }
                    if (leftTime == null) {
                        return 1;
                    }
                    if (rightTime == null) {
                        return -1;
                    }
                    return rightTime.compareTo(leftTime);
                })
                .limit(8)
                .toList();
    }

    private List<RegulatoryDashboardDataVO.TrendPoint> buildTrendSeries(DateWindow window, List<Long> orgIds, String quickRange) {
        List<RegulatoryDashboardDataVO.TrendPoint> list = new ArrayList<>();
        if ("today".equals(quickRange)) {
            LocalDateTime cursor = window.start;
            while (!cursor.isAfter(window.end)) {
                LocalDateTime end = cursor.plusHours(4).minusSeconds(1);
                RegulatoryDashboardDataVO.TrendPoint point = new RegulatoryDashboardDataVO.TrendPoint();
                point.setLabel(HM_FMT.format(cursor));
                point.setAlarm((int) queryForLong("SELECT COUNT(*) FROM device_alert WHERE deleted = 0 AND triggered_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, new DateWindow(cursor, min(end, window.end)))));
                point.setReview((int) queryForLong("SELECT COUNT(*) FROM sys_meal_review WHERE deleted = 0 AND created_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, new DateWindow(cursor, min(end, window.end)))));
                list.add(point);
                cursor = cursor.plusHours(4);
            }
            return list;
        }

        if ("7d".equals(quickRange)) {
            LocalDate cursor = window.start.toLocalDate();
            while (!cursor.isAfter(window.end.toLocalDate())) {
                DateWindow bucket = new DateWindow(cursor.atStartOfDay(), cursor.atTime(LocalTime.MAX));
                RegulatoryDashboardDataVO.TrendPoint point = new RegulatoryDashboardDataVO.TrendPoint();
                point.setLabel(MD_FMT.format(cursor));
                point.setAlarm((int) queryForLong("SELECT COUNT(*) FROM device_alert WHERE deleted = 0 AND triggered_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, bucket)));
                point.setReview((int) queryForLong("SELECT COUNT(*) FROM sys_meal_review WHERE deleted = 0 AND review_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, cursor, cursor, "org_id")));
                list.add(point);
                cursor = cursor.plusDays(1);
            }
            return list;
        }

        List<DateWindow> weeklyBuckets = splitWeeks(window);
        for (int i = 0; i < weeklyBuckets.size(); i++) {
            DateWindow bucket = weeklyBuckets.get(i);
            RegulatoryDashboardDataVO.TrendPoint point = new RegulatoryDashboardDataVO.TrendPoint();
            point.setLabel("第" + (i + 1) + "周");
            point.setAlarm((int) queryForLong("SELECT COUNT(*) FROM device_alert WHERE deleted = 0 AND triggered_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, bucket)));
            point.setReview((int) queryForLong("SELECT COUNT(*) FROM sys_meal_review WHERE deleted = 0 AND review_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, bucket.start.toLocalDate(), bucket.end.toLocalDate(), "org_id")));
            list.add(point);
        }
        return list;
    }

    private List<RegulatoryDashboardDataVO.DistributionItem> buildAlarmDistribution(DateWindow window, List<Long> orgIds) {
        String sql = "SELECT alert_level, COUNT(*) cnt FROM device_alert WHERE deleted = 0 AND triggered_at BETWEEN ? AND ?" +
                orgCondition("org_id", orgIds) + " GROUP BY alert_level";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, withTimeArgs(orgIds, window).toArray());
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("紧急", 0);
        counts.put("异常", 0);
        counts.put("预警", 0);
        counts.put("正常跟踪", 0);
        for (Map<String, Object> row : rows) {
            String key = switch (asString(row.get("alert_level"))) {
                case "critical", "danger", "urgent", "error" -> "紧急";
                case "warning" -> "预警";
                case "info" -> "正常跟踪";
                default -> "异常";
            };
            counts.put(key, counts.getOrDefault(key, 0) + toInt(row.get("cnt")));
        }
        return counts.entrySet().stream().map(entry -> {
            RegulatoryDashboardDataVO.DistributionItem item = new RegulatoryDashboardDataVO.DistributionItem();
            item.setName(entry.getKey());
            item.setValue(entry.getValue());
            return item;
        }).toList();
    }

    private List<RegulatoryDashboardDataVO.BarItem> buildExecutionSeries(DateWindow window, List<Long> orgIds, String quickRange) {
        if ("today".equals(quickRange)) {
            int overallMorningRate = overallCheckRate(orgIds, window);
            return List.of(
                    barItem("早餐", overallMorningRate, cookingRateForMeal(orgIds, window, "breakfast")),
                    barItem("午餐", overallMorningRate, cookingRateForMeal(orgIds, window, "lunch")),
                    barItem("晚餐", overallMorningRate, cookingRateForMeal(orgIds, window, "dinner"))
            );
        }

        if ("7d".equals(quickRange)) {
            List<RegulatoryDashboardDataVO.BarItem> list = new ArrayList<>();
            LocalDate cursor = window.start.toLocalDate();
            while (!cursor.isAfter(window.end.toLocalDate())) {
                DateWindow bucket = new DateWindow(cursor.atStartOfDay(), cursor.atTime(LocalTime.MAX));
                list.add(barItem(
                        "周" + dayOfWeekZh(cursor.getDayOfWeek().getValue()),
                        overallCheckRate(orgIds, bucket),
                        overallCookingRate(orgIds, bucket)
                ));
                cursor = cursor.plusDays(1);
            }
            return list;
        }

        List<RegulatoryDashboardDataVO.BarItem> list = new ArrayList<>();
        List<DateWindow> buckets = splitWeeks(window);
        for (int i = 0; i < buckets.size(); i++) {
            DateWindow bucket = buckets.get(i);
            list.add(barItem("第" + (i + 1) + "周", overallCheckRate(orgIds, bucket), overallCookingRate(orgIds, bucket)));
        }
        return list;
    }

    private List<RegulatoryDashboardDataVO.QualityMetric> buildServiceQuality(DateWindow window, List<Long> orgIds) {
        long alerts = queryForLong("SELECT COUNT(*) FROM device_alert WHERE deleted = 0 AND alert_type = 'ai_violation' AND triggered_at BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window));
        long tasks = queryForLong(
                "SELECT COUNT(*) FROM cook_task t JOIN recipe_plan p ON p.id = t.plan_id AND p.deleted = 0 " +
                        "WHERE t.deleted = 0 AND p.plan_date BETWEEN ? AND ?" + orgCondition("p.org_id", orgIds),
                withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "p.org_id")
        );
        BigDecimal violationRate = percent(alerts, tasks);

        long wasteTotal = queryForLong("SELECT COUNT(*) FROM wms_outbound_order WHERE deleted = 0 AND COALESCE(completed_at, created_at) BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window));
        long wasteCount = queryForLong("SELECT COUNT(*) FROM wms_outbound_order WHERE deleted = 0 AND outbound_type IN ('loss','scrap') AND COALESCE(completed_at, created_at) BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window));
        BigDecimal wasteRate = percent(wasteCount, wasteTotal);

        long sampleTotal = queryForLong("SELECT COUNT(*) FROM sample_record WHERE deleted = 0 AND sample_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        long traceable = queryForLong("SELECT COUNT(*) FROM sample_record WHERE deleted = 0 AND trace_batch_id IS NOT NULL AND trace_batch_id <> '' AND sample_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        BigDecimal traceMinutes = averageTraceMinutes(window, orgIds);

        long reviewTotal = queryForLong("SELECT COUNT(*) FROM sys_meal_review WHERE deleted = 0 AND review_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        long goodReview = queryForLong("SELECT COUNT(*) FROM sys_meal_review WHERE deleted = 0 AND overall_score >= 4 AND review_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        BigDecimal avgScore = queryForDecimal("SELECT COALESCE(AVG(overall_score), 0) FROM sys_meal_review WHERE deleted = 0 AND review_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        BigDecimal satisfaction = avgScore.multiply(BigDecimal.valueOf(20)).setScale(1, RoundingMode.HALF_UP);

        return List.of(
                qualityMetric("违规率", formatDecimal(violationRate) + "%", "AI违规 " + formatNumber(alerts) + " / 烹饪任务 " + formatNumber(tasks), "目标 ≤ " + VIOLATION_TARGET.stripTrailingZeros().toPlainString() + "%", violationRate.compareTo(VIOLATION_TARGET) <= 0 ? "正常" : "预警"),
                qualityMetric("溯源响应时长", formatDecimal(traceMinutes) + " 分钟", "溯源覆盖 " + formatDecimal(percent(traceable, sampleTotal)) + "%", "目标 ≤ " + TRACE_TARGET_MINUTES.stripTrailingZeros().toPlainString() + " 分钟", traceMinutes.compareTo(TRACE_TARGET_MINUTES) <= 0 ? "正常" : "预警"),
                qualityMetric("食材浪费率", formatDecimal(wasteRate) + "%", "报损报废 " + formatNumber(wasteCount) + " 单", "目标 ≤ " + WASTE_TARGET.stripTrailingZeros().toPlainString() + "%", wasteRate.compareTo(WASTE_TARGET) <= 0 ? "正常" : "预警"),
                qualityMetric("就餐满意度", formatDecimal(satisfaction) + " 分", "4星及以上 " + formatNumber(goodReview) + " 条", "评价总数 " + formatNumber(reviewTotal) + " 条", satisfaction.compareTo(BigDecimal.valueOf(95)) >= 0 ? "正常" : "预警")
        );
    }

    private List<RegulatoryDashboardDataVO.HeatCard> buildHeatCards(DateWindow window, List<Long> orgIds) {
        String sql = "SELECT location_name, COUNT(*) AS cnt FROM (" +
                "SELECT COALESCE(di.location_desc, a.device_name, '未标注区域') AS location_name " +
                "FROM device_alert a " +
                "LEFT JOIN device_info di ON di.id = a.device_id AND di.deleted = 0 " +
                "WHERE a.deleted = 0 AND a.triggered_at BETWEEN ? AND ?" + orgCondition("a.org_id", orgIds) +
                " UNION ALL " +
                "SELECT CASE " +
                "WHEN NULLIF(TRIM(s.storage_location), '') IS NULL OR TRIM(s.storage_location) REGEXP '^[0-9]+$' " +
                "THEN COALESCE(CONCAT(o.org_name, ' / 留样间'), '留样间') " +
                "ELSE TRIM(s.storage_location) " +
                "END AS location_name " +
                "FROM sample_record s " +
                "LEFT JOIN sys_organization o ON o.id = s.org_id AND o.deleted = 0 " +
                "WHERE s.deleted = 0 AND s.status IN ('pending_sample','overdue') AND s.sample_date BETWEEN ? AND ?" + orgCondition("s.org_id", orgIds) +
                " UNION ALL " +
                "SELECT COALESCE(CONCAT_WS(' / ', w.warehouse_name, l.location_name), w.warehouse_name, '库存区域') AS location_name " +
                "FROM wms_inventory i " +
                "LEFT JOIN wms_warehouse w ON w.id = i.warehouse_id AND w.deleted = 0 " +
                "LEFT JOIN wms_location l ON l.id = i.location_id AND l.deleted = 0 " +
                "WHERE i.status IN ('warning','expired')" + orgCondition("i.org_id", orgIds) +
                ") t GROUP BY location_name ORDER BY cnt DESC LIMIT 4";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, withHeatCardArgs(orgIds, window).toArray());
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(row -> {
            int count = toInt(row.get("cnt"));
            return heatCard(
                    asString(row.get("location_name")),
                    count >= 5 ? "高风险" : count >= 3 ? "中风险" : "关注",
                    formatNumber(count) + " 起风险事件",
                    count >= 5 ? "紧急" : count >= 3 ? "异常" : "预警"
            );
        }).toList();
    }

    private List<RegulatoryDashboardDataVO.ReportTemplate> loadReportTemplates(List<Long> orgIds) {
        String sql = "SELECT template_name, scope_desc, updated_at FROM sys_regulatory_report_template WHERE deleted = 0 " +
                optionalGlobalOrgCondition("org_id", orgIds) + " ORDER BY sort_order ASC, id ASC";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, withGlobalOrgArgs(orgIds).toArray());
            if (rows.isEmpty()) {
                return Collections.emptyList();
            }
            return rows.stream().map(row -> reportTemplate(
                    asString(row.get("template_name")),
                    asString(row.get("scope_desc")),
                    formatDateTime(toDateTime(row.get("updated_at")))
            )).toList();
        } catch (DataAccessException ex) {
            log.warn("监管看板报表模板配置读取失败：{}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<RegulatoryDashboardDataVO.ExternalShare> loadExternalShares(List<Long> orgIds) {
        String sql = "SELECT target_name, share_mode, expire_at, status FROM sys_regulatory_external_share WHERE deleted = 0 " +
                optionalGlobalOrgCondition("org_id", orgIds) + " ORDER BY sort_order ASC, id ASC";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, withGlobalOrgArgs(orgIds).toArray());
            if (rows.isEmpty()) {
                return Collections.emptyList();
            }
            return rows.stream().map(row -> externalShare(
                    asString(row.get("target_name")),
                    asString(row.get("share_mode")),
                    row.get("expire_at") == null ? "长期" : formatDateTime(toDateTime(row.get("expire_at"))),
                    asString(row.get("status"))
            )).toList();
        } catch (DataAccessException ex) {
            log.warn("监管看板外部共享配置读取失败：{}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<RegulatoryDashboardDataVO.ApiSubscription> loadApiSubscriptions(List<Long> orgIds) {
        String sql = "SELECT app_code, api_path, rate_limit_desc, status FROM sys_regulatory_api_subscription WHERE deleted = 0 " +
                optionalGlobalOrgCondition("org_id", orgIds) + " ORDER BY sort_order ASC, id ASC";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, withGlobalOrgArgs(orgIds).toArray());
            if (rows.isEmpty()) {
                return Collections.emptyList();
            }
            return rows.stream().map(row -> apiSubscription(
                    asString(row.get("app_code")),
                    asString(row.get("api_path")),
                    asString(row.get("rate_limit_desc")),
                    asString(row.get("status"))
            )).toList();
        } catch (DataAccessException ex) {
            log.warn("监管看板API订阅配置读取失败：{}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private DateWindow resolveWindow(RegulatoryDashboardQueryDTO query) {
        if (query.getStartDate() != null && query.getEndDate() != null) {
            LocalDate start = LocalDate.parse(query.getStartDate());
            LocalDate end = LocalDate.parse(query.getEndDate());
            return new DateWindow(start.atStartOfDay(), end.atTime(LocalTime.MAX));
        }
        LocalDate today = LocalDate.now();
        return switch (query.getQuickRange()) {
            case "7d" -> new DateWindow(today.minusDays(6).atStartOfDay(), today.atTime(LocalTime.MAX));
            case "30d" -> {
                yield new DateWindow(today.minusDays(29).atStartOfDay(), today.atTime(LocalTime.MAX));
            }
            default -> new DateWindow(today.atStartOfDay(), today.atTime(LocalTime.MAX));
        };
    }

    private List<Long> resolveOrgIds(RegulatoryDashboardQueryDTO query) {
        List<Long> scopedOrgIds = null;
        if (query.getOrgId() != null) {
            scopedOrgIds = List.of(query.getOrgId());
        } else if (query.getOrgIds() != null) {
            scopedOrgIds = query.getOrgIds();
        }

        String organization = normalizeScopeName(query.getOrganization(), "全部组织");
        String canteen = normalizeScopeName(query.getCanteen(), "全部食堂");
        if (organization == null && canteen == null) {
            return scopedOrgIds;
        }

        Set<Long> resolvedIds = null;
        if (organization != null) {
            resolvedIds = new LinkedHashSet<>(resolveOrgScopeByName(organization, null));
        }
        if (canteen != null) {
            Set<Long> canteenIds = new LinkedHashSet<>(resolveOrgScopeByName(canteen, "canteen"));
            if (resolvedIds == null) {
                resolvedIds = canteenIds;
            } else {
                resolvedIds.retainAll(canteenIds);
            }
        }
        if (resolvedIds == null) {
            return scopedOrgIds;
        }
        if (scopedOrgIds != null) {
            resolvedIds.retainAll(new LinkedHashSet<>(scopedOrgIds));
        }
        return new ArrayList<>(resolvedIds);
    }

    private List<Long> resolveOrgScopeByName(String orgName, String orgType) {
        if (orgName == null) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id FROM sys_organization WHERE deleted = 0 AND (org_name = ? OR path LIKE ?)"
        );
        List<Object> args = new ArrayList<>();
        args.add(orgName);
        args.add("%/" + orgName + "/%");
        if (orgType != null && !orgType.isBlank()) {
            sql.append(" AND (org_type = ? OR path LIKE ?)");
            args.add(orgType);
            args.add("%/" + orgName + "/%");
        }
        List<Long> ids = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> rs.getLong("id"), args.toArray());
        return ids.stream().distinct().toList();
    }

    private List<Object> withTimeArgs(List<Long> orgIds, DateWindow window) {
        return withTimeArgs(orgIds, window, "org_id");
    }

    private List<Object> withTimeArgs(List<Long> orgIds, DateWindow window, String orgColumn) {
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(window.start));
        args.add(Timestamp.valueOf(window.end));
        appendOrgArgs(args, orgIds);
        return args;
    }

    private List<Object> withDoubleTimeArgs(List<Long> orgIds, DateWindow window) {
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(window.start));
        args.add(Timestamp.valueOf(window.end));
        args.add(Timestamp.valueOf(window.start));
        args.add(Timestamp.valueOf(window.end));
        appendOrgArgs(args, orgIds);
        appendOrgArgs(args, orgIds);
        return args;
    }

    private List<Object> withDateArgs(List<Long> orgIds, LocalDate start, LocalDate end, String orgColumn) {
        List<Object> args = new ArrayList<>();
        args.add(java.sql.Date.valueOf(start));
        if (end != null) {
            args.add(java.sql.Date.valueOf(end));
        }
        appendOrgArgs(args, orgIds);
        return args;
    }

    private List<Object> withOrgArgs(List<Long> orgIds) {
        List<Object> args = new ArrayList<>();
        appendOrgArgs(args, orgIds);
        return args;
    }

    private List<Object> withGlobalOrgArgs(List<Long> orgIds) {
        return withOrgArgs(orgIds);
    }

    private void appendOrgArgs(List<Object> args, List<Long> orgIds) {
        if (orgIds != null && !orgIds.isEmpty()) {
            args.addAll(orgIds);
        }
    }

    private String orgCondition(String column, List<Long> orgIds) {
        if (orgIds == null) {
            return "";
        }
        if (orgIds.isEmpty()) {
            return " AND 1 = 0";
        }
        return " AND " + column + " IN (" + orgIds.stream().map(id -> "?").collect(Collectors.joining(",")) + ")";
    }

    private String optionalGlobalOrgCondition(String column, List<Long> orgIds) {
        if (orgIds == null) {
            return "";
        }
        if (orgIds.isEmpty()) {
            return " AND 1 = 0";
        }
        return " AND (" + column + " IS NULL OR " + column + " IN (" + orgIds.stream().map(id -> "?").collect(Collectors.joining(",")) + "))";
    }

    private List<DateWindow> splitWeeks(DateWindow window) {
        List<DateWindow> list = new ArrayList<>();
        LocalDate cursor = window.start.toLocalDate();
        LocalDate end = window.end.toLocalDate();
        while (!cursor.isAfter(end)) {
            LocalDate weekEnd = cursor.plusDays(6);
            if (weekEnd.isAfter(end)) {
                weekEnd = end;
            }
            list.add(new DateWindow(cursor.atStartOfDay(), weekEnd.atTime(LocalTime.MAX)));
            cursor = weekEnd.plusDays(1);
        }
        return list;
    }

    private int checkRateForMeal(List<Long> orgIds, DateWindow window, String mealType) {
        return overallCheckRate(orgIds, window);
    }

    private int cookingRateForMeal(List<Long> orgIds, DateWindow window, String mealType) {
        long total = queryForLong(
                "SELECT COUNT(*) " +
                        "FROM cook_task t " +
                        "JOIN recipe_plan p ON p.id = t.plan_id AND p.deleted = 0 " +
                        "WHERE t.deleted = 0 AND p.meal_type = ? AND p.plan_date BETWEEN ? AND ?" + orgCondition("p.org_id", orgIds),
                withLeadingArg(mealType, withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "p.org_id"))
        );
        long normal = queryForLong(
                "SELECT COUNT(*) " +
                        "FROM cook_task t " +
                        "JOIN recipe_plan p ON p.id = t.plan_id AND p.deleted = 0 " +
                        "WHERE t.deleted = 0 AND p.meal_type = ? AND p.plan_date BETWEEN ? AND ? " +
                        "AND NOT EXISTS (SELECT 1 FROM cook_temperature_record r WHERE r.task_id = t.id AND r.abnormal = 1)" +
                        orgCondition("p.org_id", orgIds),
                withLeadingArg(mealType, withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "p.org_id"))
        );
        return percent(normal, total).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private int overallCheckRate(List<Long> orgIds, DateWindow window) {
        long total = queryForLong("SELECT COUNT(*) FROM health_check_record WHERE check_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        long normal = queryForLong("SELECT COUNT(*) FROM health_check_record WHERE check_result = 'pass' AND check_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id"));
        return percent(normal, total).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private int overallCookingRate(List<Long> orgIds, DateWindow window) {
        long total = queryForLong("SELECT COUNT(*) FROM cook_temperature_record WHERE record_time BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window, "org_id"));
        long normal = queryForLong("SELECT COUNT(*) FROM cook_temperature_record WHERE abnormal = 0 AND record_time BETWEEN ? AND ?" + orgCondition("org_id", orgIds), withTimeArgs(orgIds, window, "org_id"));
        return percent(normal, total).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private BigDecimal averageTraceMinutes(DateWindow window, List<Long> orgIds) {
        String sql = "SELECT COALESCE(sampled_at, created_at) AS started_at, COALESCE(archived_at, disposal_at) AS finished_at " +
                "FROM sample_record WHERE deleted = 0 AND sample_date BETWEEN ? AND ?" + orgCondition("org_id", orgIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, withDateArgs(orgIds, window.start.toLocalDate(), window.end.toLocalDate(), "org_id").toArray());
        long count = 0;
        long totalMinutes = 0;
        for (Map<String, Object> row : rows) {
            LocalDateTime start = toDateTime(row.get("started_at"));
            LocalDateTime end = toDateTime(row.get("finished_at"));
            if (start != null && end != null && !end.isBefore(start)) {
                totalMinutes += Duration.between(start, end).toMinutes();
                count++;
            }
        }
        if (count == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private List<Object> withLeadingArg(Object value, List<Object> args) {
        List<Object> result = new ArrayList<>();
        result.add(value);
        result.addAll(args);
        return result;
    }

    private List<Object> withHeatCardArgs(List<Long> orgIds, DateWindow window) {
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(window.start));
        args.add(Timestamp.valueOf(window.end));
        appendOrgArgs(args, orgIds);
        args.add(java.sql.Date.valueOf(window.start.toLocalDate()));
        args.add(java.sql.Date.valueOf(window.end.toLocalDate()));
        appendOrgArgs(args, orgIds);
        appendOrgArgs(args, orgIds);
        return args;
    }

    private List<RegulatoryDashboardDataVO.RiskEvent> applyLocationFilters(List<RegulatoryDashboardDataVO.RiskEvent> events, RegulatoryDashboardQueryDTO query) {
        return events.stream().filter(event -> {
            boolean canteenMatch = query.getCanteen() == null || "全部食堂".equals(query.getCanteen()) || event.getLocation().contains(query.getCanteen());
            boolean areaMatch = query.getArea() == null || "全部区域".equals(query.getArea()) || event.getLocation().contains(query.getArea());
            return canteenMatch && areaMatch;
        }).toList();
    }

    private long queryForLong(String sql, List<Object> args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return value == null ? 0L : value;
    }

    private int queryForInt(String sql, List<Object> args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args.toArray());
        return value == null ? 0 : value;
    }

    private BigDecimal queryForDecimal(String sql, List<Object> args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args.toArray());
        return value == null ? BigDecimal.ZERO : value;
    }

    private Map<String, Long> queryForMap(String sql, List<Object> args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(asString(row.get("alert_type")), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }

    private RegulatoryDashboardDataVO.OverviewMetric metric(String id, String title, String value, String unit, String compare, String status, String source) {
        RegulatoryDashboardDataVO.OverviewMetric metric = new RegulatoryDashboardDataVO.OverviewMetric();
        metric.setId(id);
        metric.setTitle(title);
        metric.setValue(value);
        metric.setUnit(unit);
        metric.setCompare(compare);
        metric.setStatus(status);
        metric.setSource(source);
        return metric;
    }

    private RegulatoryDashboardDataVO.DomainSection section(String title, String subtitle, List<RegulatoryDashboardDataVO.DomainMetric> metrics) {
        RegulatoryDashboardDataVO.DomainSection section = new RegulatoryDashboardDataVO.DomainSection();
        section.setTitle(title);
        section.setSubtitle(subtitle);
        section.setMetrics(metrics);
        return section;
    }

    private RegulatoryDashboardDataVO.DomainMetric domainMetric(String name, String value, String hint, String status) {
        RegulatoryDashboardDataVO.DomainMetric metric = new RegulatoryDashboardDataVO.DomainMetric();
        metric.setName(name);
        metric.setValue(value);
        metric.setHint(hint);
        metric.setStatus(status);
        return metric;
    }

    private RegulatoryDashboardDataVO.BarItem barItem(String label, int morningCheck, int cooking) {
        RegulatoryDashboardDataVO.BarItem item = new RegulatoryDashboardDataVO.BarItem();
        item.setLabel(label);
        item.setMorningCheck(morningCheck);
        item.setCooking(cooking);
        return item;
    }

    private RegulatoryDashboardDataVO.QualityMetric qualityMetric(String label, String value, String compare, String target, String status) {
        RegulatoryDashboardDataVO.QualityMetric metric = new RegulatoryDashboardDataVO.QualityMetric();
        metric.setLabel(label);
        metric.setValue(value);
        metric.setCompare(compare);
        metric.setTarget(target);
        metric.setStatus(status);
        return metric;
    }

    private RegulatoryDashboardDataVO.HeatCard heatCard(String name, String level, String value, String status) {
        RegulatoryDashboardDataVO.HeatCard card = new RegulatoryDashboardDataVO.HeatCard();
        card.setName(name);
        card.setLevel(level);
        card.setValue(value);
        card.setStatus(status);
        return card;
    }

    private RegulatoryDashboardDataVO.ReportTemplate reportTemplate(String name, String scope, String updatedAt) {
        RegulatoryDashboardDataVO.ReportTemplate template = new RegulatoryDashboardDataVO.ReportTemplate();
        template.setName(name);
        template.setScope(scope);
        template.setUpdatedAt(updatedAt);
        return template;
    }

    private RegulatoryDashboardDataVO.ExternalShare externalShare(String target, String mode, String expireAt, String status) {
        RegulatoryDashboardDataVO.ExternalShare share = new RegulatoryDashboardDataVO.ExternalShare();
        share.setTarget(target);
        share.setMode(mode);
        share.setExpireAt(expireAt);
        share.setStatus(status);
        return share;
    }

    private RegulatoryDashboardDataVO.ApiSubscription apiSubscription(String app, String path, String limit, String status) {
        RegulatoryDashboardDataVO.ApiSubscription subscription = new RegulatoryDashboardDataVO.ApiSubscription();
        subscription.setApp(app);
        subscription.setPath(path);
        subscription.setLimit(limit);
        subscription.setStatus(status);
        return subscription;
    }

    private RegulatoryDashboardDataVO.DistributionItem distribution(String name, int value) {
        RegulatoryDashboardDataVO.DistributionItem item = new RegulatoryDashboardDataVO.DistributionItem();
        item.setName(name);
        item.setValue(value);
        return item;
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal percentOrFull(long numerator, long denominator) {
        if (denominator <= 0) {
            return HUNDRED.setScale(1, RoundingMode.HALF_UP);
        }
        return percent(numerator, denominator);
    }

    private String formatPercent(BigDecimal value) {
        return formatDecimal(value) + "%";
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? "0.0" : value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatNumber(long value) {
        return Long.toString(value);
    }

    private String mapRiskLevel(String alertLevel) {
        return switch (alertLevel) {
            case "critical", "danger", "urgent", "error" -> "紧急";
            case "warning" -> "严重";
            default -> "一般";
        };
    }

    private String mapRiskStatus(String alertStatus, String dispatchStatus) {
        if ("closed".equals(alertStatus) || "handled".equals(alertStatus) || "reviewed".equals(alertStatus) || "completed".equals(dispatchStatus) || "reviewed".equals(dispatchStatus)) {
            return "已闭环";
        }
        if ("cancelled".equals(dispatchStatus)) {
            return "已挂起";
        }
        if ("assigned".equals(alertStatus) || "handling".equals(alertStatus) || "processing".equals(dispatchStatus)) {
            return "处理中";
        }
        return "待处理";
    }

    private boolean isOvertime(LocalDateTime deadline, String dispatchStatus) {
        return deadline != null
                && deadline.isBefore(LocalDateTime.now())
                && !"completed".equals(dispatchStatus)
                && !"reviewed".equals(dispatchStatus)
                && !"cancelled".equals(dispatchStatus);
    }

    private String resolveAlertRiskType(String alertType) {
        return switch (alertType) {
            case "ai_violation" -> "违规行为";
            case "temp_abnormal", "humidity_abnormal", "threshold_exceed" -> "设备告警";
            case "device_offline", "device_fault" -> "设备风险";
            default -> "告警异常";
        };
    }

    private String resolveAlertSourceModule(String alertType) {
        return switch (alertType) {
            case "ai_violation" -> "AI违规识别";
            case "temp_abnormal", "humidity_abnormal", "threshold_exceed" -> "设备监测";
            case "device_offline", "device_fault" -> "设备管理";
            default -> "告警中心";
        };
    }

    private List<String> resolveAlertSourceTerminals(Long deviceId) {
        return deviceId == null ? List.of("Web") : List.of("Device");
    }

    private String resolveSampleRiskType(String sampleStatus) {
        return "overdue".equals(sampleStatus) ? "留样超期" : "留样待补录";
    }

    private String resolveSampleRiskTitle(String menuName, String sampleStatus) {
        String prefix = "overdue".equals(sampleStatus) ? "留样超期未销样" : "留样待补录";
        return firstNonBlank(menuName) == null || firstNonBlank(menuName).isBlank()
                ? prefix
                : prefix + "：" + menuName;
    }

    private String mapSampleRiskStatus(String sampleStatus) {
        return switch (sampleStatus) {
            case "overdue" -> "待处理";
            case "pending_sample" -> "处理中";
            case "voided" -> "已挂起";
            case "disposed", "archived" -> "已闭环";
            default -> "处理中";
        };
    }

    private String resolveSampleSourceModule(String recordOriginType) {
        if (recordOriginType == null || recordOriginType.isBlank()) {
            return "留样管理";
        }
        return switch (recordOriginType) {
            case "auto", "system_backfill" -> "系统留样";
            case "manual_daily", "manual_history" -> "人工留样";
            case "offline_delayed" -> "离线补录";
            default -> "留样管理";
        };
    }

    private List<String> resolveSampleSourceTerminals(String terminals, String recordOriginType) {
        if (terminals != null && !terminals.isBlank()) {
            return List.of(terminals.split(",")).stream()
                    .map(this::normalizeTerminalName)
                    .distinct()
                    .toList();
        }
        if (recordOriginType == null || recordOriginType.isBlank()) {
            return List.of("Web");
        }
        return switch (recordOriginType) {
            case "offline_delayed" -> List.of("Mobile");
            case "auto", "system_backfill" -> List.of("System");
            default -> List.of("Web");
        };
    }

    private String normalizeTerminalName(String terminal) {
        return switch (terminal == null ? "" : terminal.trim().toLowerCase()) {
            case "mobile" -> "Mobile";
            case "system" -> "System";
            default -> "Web";
        };
    }

    private String resolveSampleLocation(String storageLocation, String orgName) {
        if (storageLocation != null) {
            String normalized = storageLocation.trim();
            if (!normalized.isBlank() && !normalized.matches("^\\d+$")) {
                return normalized;
            }
        }
        String normalizedOrgName = firstNonBlank(orgName);
        if (normalizedOrgName.isBlank()) {
            return "留样间";
        }
        return normalizedOrgName + " / 留样间";
    }

    private int riskEventStatusOrder(String status) {
        if ("待处理".equals(status)) {
            return 0;
        }
        if ("处理中".equals(status)) {
            return 1;
        }
        if ("已挂起".equals(status)) {
            return 2;
        }
        return 3;
    }

    private int riskLevelOrder(String level) {
        if ("紧急".equals(level)) {
            return 0;
        }
        if ("严重".equals(level)) {
            return 1;
        }
        return 2;
    }

    private LocalDateTime parseDateTimeSilently(String value) {
        if (value == null || value.isBlank() || value.length() != 19) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATETIME_FMT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveInventoryRiskTitle(String materialName, String batchNo, String status) {
        String suffix = "expired".equals(status) ? "批次已过期" : "批次临期预警";
        String batchLabel = batchNo == null || batchNo.isBlank() ? "" : "（批次 " + batchNo + "）";
        return firstNonBlank(materialName, "未命名物料") + batchLabel + suffix;
    }

    private String resolveInventoryLocation(String warehouseName, String locationName, String batchNo) {
        List<String> parts = new ArrayList<>();
        if (warehouseName != null && !warehouseName.isBlank()) {
            parts.add(warehouseName);
        }
        if (locationName != null && !locationName.isBlank()) {
            parts.add(locationName);
        }
        if (batchNo != null && !batchNo.isBlank()) {
            parts.add("批次 " + batchNo);
        }
        return parts.isEmpty() ? "未标注位置" : String.join(" / ", parts);
    }

    private String mapInventoryRiskStatus(String inventoryStatus) {
        return switch (inventoryStatus) {
            case "expired" -> "待处理";
            case "locked" -> "已挂起";
            case "normal" -> "已闭环";
            default -> "处理中";
        };
    }

    private String resolveInventorySourceModule(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "仓储管理";
        }
        return switch (sourceType) {
            case "purchase" -> "采购入库";
            case "return" -> "退货入库";
            case "transfer" -> "调拨入库";
            case "stocktake" -> "盘盈入库";
            default -> "仓储管理";
        };
    }

    private String resolveEmployeeName(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        List<String> rows = jdbcTemplate.query("SELECT real_name FROM sys_employee WHERE id = ? AND deleted = 0 LIMIT 1",
                (rs, rowNum) -> rs.getString("real_name"),
                employeeId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String statusByThreshold(boolean warning, boolean normal) {
        return warning ? "正常" : normal ? "预警" : "异常";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String normalizeScopeName(String value, String allLabel) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank() || normalized.equals(allLabel)) {
            return null;
        }
        return normalized;
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? "" : DATETIME_FMT.format(time);
    }

    private String formatDate(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        return value.toString();
    }

    private LocalDateTime toDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        return null;
    }

    private LocalDateTime firstNonNullDateTime(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).longValue();
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        return ((Number) value).intValue();
    }

    private String dayOfWeekZh(int day) {
        return switch (day) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            default -> "日";
        };
    }

    private LocalDateTime min(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    private static class DateWindow {
        private final LocalDateTime start;
        private final LocalDateTime end;

        private DateWindow(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }
}
