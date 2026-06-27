package com.xykj.sys.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.DataScopeService;
import com.xykj.sys.dto.SupervisionDashboardQueryDTO;
import com.xykj.sys.dto.SupervisionTraceQueryDTO;
import com.xykj.sys.dto.SupervisionViolationQueryDTO;
import com.xykj.sys.service.SupervisionService;
import com.xykj.sys.vo.SupervisionDashboardVO;
import com.xykj.sys.vo.SupervisionTraceVO;
import com.xykj.sys.vo.SupervisionViolationDetailVO;
import com.xykj.sys.vo.SupervisionViolationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 数据监管服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupervisionServiceImpl implements SupervisionService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal VIOLATION_TARGET = BigDecimal.valueOf(3);
    private static final BigDecimal TRACE_TARGET_HOURS = BigDecimal.valueOf(4);
    private static final BigDecimal WASTE_TARGET = BigDecimal.valueOf(8);
    private static final BigDecimal SATISFACTION_TARGET = BigDecimal.valueOf(90);
    private static final String DEFAULT_SUPPLIER = "待补全";

    private static final Map<String, String> ALERT_TYPE_NAME_MAP = Map.ofEntries(
            Map.entry("device_offline", "设备离线"),
            Map.entry("device_fault", "设备故障"),
            Map.entry("temp_abnormal", "温度异常"),
            Map.entry("humidity_abnormal", "湿度异常"),
            Map.entry("ai_violation", "AI违规识别"),
            Map.entry("threshold_exceed", "阈值超限")
    );

    private static final Map<String, String> STATUS_NAME_MAP = Map.ofEntries(
            Map.entry("pending", "待处理"),
            Map.entry("assigned", "已指派"),
            Map.entry("handling", "处理中"),
            Map.entry("handled", "已处置"),
            Map.entry("reviewed", "已复核"),
            Map.entry("closed", "已关闭")
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DataScopeService dataScopeService;

    @Override
    @DataScope
    public SupervisionDashboardVO getDashboard(SupervisionDashboardQueryDTO query) {
        DateWindow current = resolveDateWindow(query.getDateRange());
        DateWindow previous = current.previous();
        DateWindow yoy = current.samePeriodLastYear();

        BigDecimal currentViolationRate = computeViolationRate(query.getOrgId(), query.getOrgIds(), current);
        BigDecimal previousViolationRate = computeViolationRate(query.getOrgId(), query.getOrgIds(), previous);
        BigDecimal yoyViolationRate = computeViolationRate(query.getOrgId(), query.getOrgIds(), yoy);

        BigDecimal currentTraceHours = computeTraceResponseHours(query.getOrgId(), query.getOrgIds(), current);
        BigDecimal previousTraceHours = computeTraceResponseHours(query.getOrgId(), query.getOrgIds(), previous);
        BigDecimal yoyTraceHours = computeTraceResponseHours(query.getOrgId(), query.getOrgIds(), yoy);

        BigDecimal currentWasteRate = computeWasteRate(query.getOrgId(), query.getOrgIds(), current);
        BigDecimal previousWasteRate = computeWasteRate(query.getOrgId(), query.getOrgIds(), previous);
        BigDecimal yoyWasteRate = computeWasteRate(query.getOrgId(), query.getOrgIds(), yoy);

        BigDecimal currentSatisfactionRate = computeSatisfactionRate(query.getOrgId(), query.getOrgIds(), current);
        BigDecimal previousSatisfactionRate = computeSatisfactionRate(query.getOrgId(), query.getOrgIds(), previous);
        BigDecimal yoySatisfactionRate = computeSatisfactionRate(query.getOrgId(), query.getOrgIds(), yoy);

        SupervisionDashboardVO vo = new SupervisionDashboardVO();
        vo.setViolationRate(currentViolationRate);
        vo.setViolationRateMom(computeChangePercent(currentViolationRate, previousViolationRate));
        vo.setViolationRateYoy(computeChangePercent(currentViolationRate, yoyViolationRate));
        vo.setViolationRateTarget(VIOLATION_TARGET);

        vo.setTraceResponseTime(currentTraceHours);
        vo.setTraceResponseMom(computeChangePercent(currentTraceHours, previousTraceHours));
        vo.setTraceResponseYoy(computeChangePercent(currentTraceHours, yoyTraceHours));
        vo.setTraceResponseTarget(TRACE_TARGET_HOURS);

        vo.setWasteRate(currentWasteRate);
        vo.setWasteRateMom(computeChangePercent(currentWasteRate, previousWasteRate));
        vo.setWasteRateYoy(computeChangePercent(currentWasteRate, yoyWasteRate));
        vo.setWasteRateTarget(WASTE_TARGET);

        vo.setSatisfactionRate(currentSatisfactionRate);
        vo.setSatisfactionMom(computeChangePercent(currentSatisfactionRate, previousSatisfactionRate));
        vo.setSatisfactionYoy(computeChangePercent(currentSatisfactionRate, yoySatisfactionRate));
        vo.setSatisfactionTarget(SATISFACTION_TARGET);

        vo.setSnapshotAt(LocalDateTime.now());
        vo.setRecentViolations(fetchViolations(query.getOrgId(), query.getOrgIds(), null, null, current.getStart(), current.getEnd(), 1, 5));
        vo.setRecentTraces(fetchTraces(query.getOrgId(), query.getOrgIds(), null, null, current.getStart(), current.getEnd(), 1, 5));
        vo.setTrendData(buildTrendData(query.getOrgId(), query.getOrgIds(), current));
        return vo;
    }

    @Override
    @DataScope
    public PageResult<SupervisionViolationVO> listViolations(SupervisionViolationQueryDTO query) {
        List<SupervisionViolationVO> list = fetchViolations(
                query.getOrgId(),
                query.getOrgIds(),
                query.getStatus(),
                query.getViolationType(),
                parseDateTime(query.getStartTime()),
                parseDateTime(query.getEndTime()),
                query.getPageNum(),
                query.getPageSize()
        );
        long total = countViolations(
                query.getOrgId(),
                query.getOrgIds(),
                query.getStatus(),
                query.getViolationType(),
                parseDateTime(query.getStartTime()),
                parseDateTime(query.getEndTime())
        );
        return PageResult.of(list, query.getPageNum().longValue(), query.getPageSize().longValue(), total);
    }

    @Override
    public SupervisionViolationDetailVO getViolationDetail(Long id) {
        Map<String, Object> row = jdbcTemplate.query(
                "SELECT id, alert_no, alert_type, alert_content, alert_detail, alert_images, handle_images, triggered_at, " +
                        "status, handled_by, handled_at, handle_result, org_id " +
                        "FROM device_alert WHERE id = ? AND deleted = 0 LIMIT 1",
                rs -> rs.next() ? mapRow(rs) : null,
                id
        );
        if (row == null) {
            throw BizException.notFound("违规记录不存在");
        }
        checkOrgAccess(toLong(row.get("org_id")));

        Map<String, Object> detail = parseObjectMap(asString(row.get("alert_detail")));
        SupervisionViolationDetailVO vo = new SupervisionViolationDetailVO();
        vo.setId(toLong(row.get("id")));
        vo.setViolationNo(asString(row.get("alert_no")));
        vo.setViolationType(asString(row.get("alert_type")));
        vo.setViolationTypeName(resolveViolationTypeName(vo.getViolationType()));
        vo.setTitle(resolveViolationTitle(asString(row.get("alert_content")), detail, vo.getViolationTypeName()));
        vo.setDescription(asString(row.get("alert_content")));
        vo.setLocation(resolveLocation(detail));
        vo.setTriggeredAt(toLocalDateTime(row.get("triggered_at")));
        vo.setStatus(asString(row.get("status")));
        vo.setViolationImages(parseStringList(asString(row.get("alert_images"))));
        vo.setRelatedData(detail);
        vo.setHandledBy(toLong(row.get("handled_by")));
        vo.setHandledByName(resolveEmployeeName(vo.getHandledBy()));
        vo.setHandledAt(toLocalDateTime(row.get("handled_at")));
        vo.setHandleResult(asString(row.get("handle_result")));
        vo.setHandleImages(parseStringList(asString(row.get("handle_images"))));
        return vo;
    }

    @Override
    @DataScope
    public PageResult<SupervisionTraceVO> listTraces(SupervisionTraceQueryDTO query) {
        List<SupervisionTraceVO> list = fetchTraces(
                query.getOrgId(),
                query.getOrgIds(),
                query.getStatus(),
                query.getMaterialName(),
                parseDateTime(query.getStartTime()),
                parseDateTime(query.getEndTime()),
                query.getPageNum(),
                query.getPageSize()
        );
        long total = countTraces(
                query.getOrgId(),
                query.getOrgIds(),
                query.getStatus(),
                query.getMaterialName(),
                parseDateTime(query.getStartTime()),
                parseDateTime(query.getEndTime())
        );
        return PageResult.of(list, query.getPageNum().longValue(), query.getPageSize().longValue(), total);
    }

    private BigDecimal computeViolationRate(Long orgId, List<Long> orgIds, DateWindow window) {
        long alerts = queryForLong(buildViolationMetricSql(orgId, orgIds, window), buildViolationMetricArgs(orgId, orgIds, window));
        long tasks = queryForLong(buildCookTaskMetricSql(orgId, orgIds, window), buildCookTaskMetricArgs(orgId, orgIds, window));
        if (tasks <= 0) {
            return alerts > 0 ? HUNDRED : BigDecimal.ZERO;
        }
        return percentage(BigDecimal.valueOf(alerts), BigDecimal.valueOf(tasks));
    }

    private BigDecimal computeTraceResponseHours(Long orgId, List<Long> orgIds, DateWindow window) {
        List<TraceAggregate> traces = queryTraceAggregates(orgId, orgIds, null, null, window.getStart(), window.getEnd(), false);
        List<BigDecimal> durations = traces.stream()
                .map(TraceAggregate::getResponseHours)
                .filter(Objects::nonNull)
                .toList();
        if (durations.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = durations.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(durations.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeWasteRate(Long orgId, List<Long> orgIds, DateWindow window) {
        StringBuilder base = new StringBuilder(
                " FROM wms_outbound_order WHERE deleted = 0 " +
                        "AND COALESCE(completed_at, created_at) >= ? " +
                        "AND COALESCE(completed_at, created_at) <= ? "
        );
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(window.getStart()));
        args.add(Timestamp.valueOf(window.getEnd()));
        appendOrgFilter(base, args, orgId, orgIds);
        BigDecimal totalAmount = queryForDecimal("SELECT COALESCE(SUM(total_amount), 0)" + base, args);
        BigDecimal wasteAmount = queryForDecimal(
                "SELECT COALESCE(SUM(total_amount), 0)" + base + " AND outbound_type IN ('loss', 'scrap')",
                args
        );
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return percentage(wasteAmount, totalAmount);
    }

    private BigDecimal computeSatisfactionRate(Long orgId, List<Long> orgIds, DateWindow window) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS total_count, " +
                        "SUM(CASE WHEN overall_score >= 4 THEN 1 ELSE 0 END) AS satisfied_count " +
                        "FROM sys_meal_review WHERE deleted = 0 AND created_at >= ? AND created_at <= ? "
        );
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(window.getStart()));
        args.add(Timestamp.valueOf(window.getEnd()));
        appendOrgFilter(sql, args, orgId, orgIds);
        Map<String, Object> result = jdbcTemplate.query(sql.toString(), rs -> rs.next() ? Map.of(
                "total_count", rs.getLong("total_count"),
                "satisfied_count", rs.getLong("satisfied_count")
        ) : Collections.emptyMap(), args.toArray());
        long total = ((Number) result.getOrDefault("total_count", 0L)).longValue();
        long satisfied = ((Number) result.getOrDefault("satisfied_count", 0L)).longValue();
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return percentage(BigDecimal.valueOf(satisfied), BigDecimal.valueOf(total));
    }

    private List<SupervisionDashboardVO.TrendPoint> buildTrendData(Long orgId, List<Long> orgIds, DateWindow current) {
        List<DateWindow> buckets = buildWeeklyBuckets(current);
        List<SupervisionDashboardVO.TrendPoint> result = new ArrayList<>(buckets.size());
        for (int i = 0; i < buckets.size(); i++) {
            DateWindow bucket = buckets.get(i);
            SupervisionDashboardVO.TrendPoint point = new SupervisionDashboardVO.TrendPoint();
            point.setWeekLabel("W" + (i + 1));
            point.setViolationRate(computeViolationRate(orgId, orgIds, bucket));
            point.setWasteRate(computeWasteRate(orgId, orgIds, bucket));
            point.setSatisfactionRate(computeSatisfactionRate(orgId, orgIds, bucket));
            result.add(point);
        }
        return result;
    }

    private List<SupervisionViolationVO> fetchViolations(Long orgId,
                                                         List<Long> orgIds,
                                                         String status,
                                                         String violationType,
                                                         LocalDateTime start,
                                                         LocalDateTime end,
                                                         Integer pageNum,
                                                         Integer pageSize) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, alert_no, alert_type, alert_content, alert_detail, triggered_at, status, handled_at, handled_by " +
                        "FROM device_alert WHERE deleted = 0 "
        );
        List<Object> args = new ArrayList<>();
        appendOrgFilter(sql, args, orgId, orgIds);
        if (StrUtil.isNotBlank(status)) {
            sql.append(" AND status = ? ");
            args.add(status);
        }
        if (StrUtil.isNotBlank(violationType)) {
            sql.append(" AND alert_type = ? ");
            args.add(violationType);
        }
        if (start != null) {
            sql.append(" AND triggered_at >= ? ");
            args.add(Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND triggered_at <= ? ");
            args.add(Timestamp.valueOf(end));
        }
        sql.append(" ORDER BY triggered_at DESC LIMIT ? OFFSET ? ");
        args.add(pageSize);
        args.add((Math.max(pageNum, 1) - 1) * pageSize);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> detail = parseObjectMap(rs.getString("alert_detail"));
            SupervisionViolationVO vo = new SupervisionViolationVO();
            vo.setId(rs.getLong("id"));
            vo.setViolationNo(rs.getString("alert_no"));
            vo.setViolationType(rs.getString("alert_type"));
            vo.setViolationTypeName(resolveViolationTypeName(vo.getViolationType()));
            vo.setTitle(resolveViolationTitle(rs.getString("alert_content"), detail, vo.getViolationTypeName()));
            vo.setDescription(rs.getString("alert_content"));
            vo.setLocation(resolveLocation(detail));
            vo.setTriggeredAt(toLocalDateTime(rs.getTimestamp("triggered_at")));
            vo.setStatus(rs.getString("status"));
            vo.setHandledAt(toLocalDateTime(rs.getTimestamp("handled_at")));
            vo.setHandlerName(resolveEmployeeName(toLong(rs.getObject("handled_by"))));
            return vo;
        }, args.toArray());
    }

    private long countViolations(Long orgId,
                                 List<Long> orgIds,
                                 String status,
                                 String violationType,
                                 LocalDateTime start,
                                 LocalDateTime end) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM device_alert WHERE deleted = 0 ");
        List<Object> args = new ArrayList<>();
        appendOrgFilter(sql, args, orgId, orgIds);
        if (StrUtil.isNotBlank(status)) {
            sql.append(" AND status = ? ");
            args.add(status);
        }
        if (StrUtil.isNotBlank(violationType)) {
            sql.append(" AND alert_type = ? ");
            args.add(violationType);
        }
        if (start != null) {
            sql.append(" AND triggered_at >= ? ");
            args.add(Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND triggered_at <= ? ");
            args.add(Timestamp.valueOf(end));
        }
        return queryForLong(sql.toString(), args);
    }

    private List<SupervisionTraceVO> fetchTraces(Long orgId,
                                                 List<Long> orgIds,
                                                 String status,
                                                 String materialName,
                                                 LocalDateTime start,
                                                 LocalDateTime end,
                                                 Integer pageNum,
                                                 Integer pageSize) {
        List<TraceAggregate> aggregates = queryTraceAggregates(orgId, orgIds, status, materialName, start, end, true);
        int fromIndex = Math.max((Math.max(pageNum, 1) - 1) * pageSize, 0);
        if (fromIndex >= aggregates.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + pageSize, aggregates.size());
        return aggregates.subList(fromIndex, toIndex).stream().map(this::toTraceVO).toList();
    }

    private long countTraces(Long orgId,
                             List<Long> orgIds,
                             String status,
                             String materialName,
                             LocalDateTime start,
                             LocalDateTime end) {
        return queryTraceAggregates(orgId, orgIds, status, materialName, start, end, false).size();
    }

    private List<TraceAggregate> queryTraceAggregates(Long orgId,
                                                      List<Long> orgIds,
                                                      String status,
                                                      String materialName,
                                                      LocalDateTime start,
                                                      LocalDateTime end,
                                                      boolean ordered) {
        StringBuilder sql = new StringBuilder(
                "SELECT s.trace_batch_id, MAX(s.id) AS record_id, MAX(s.created_at) AS latest_created_at, " +
                        "MIN(s.created_at) AS request_time, " +
                        "MAX(COALESCE(s.archived_at, s.disposal_at)) AS completed_at, " +
                        "MIN(inv.batch_no) AS batch_no, " +
                        "MIN(COALESCE(inv.material_name, s.menu_name)) AS material_name, " +
                        "MIN(COALESCE(io.supplier_name, '").append(DEFAULT_SUPPLIER).append("')) AS supplier_name " +
                        "FROM sample_record s " +
                        "LEFT JOIN wms_inventory inv ON inv.trace_batch_id = s.trace_batch_id AND inv.org_id = s.org_id " +
                        "LEFT JOIN wms_inbound_order io ON io.id = inv.source_id AND inv.source_type = 'purchase' AND io.deleted = 0 " +
                        "WHERE s.deleted = 0 AND s.trace_batch_id IS NOT NULL AND s.trace_batch_id <> '' "
        );
        List<Object> args = new ArrayList<>();
        appendOrgFilter(sql, args, orgId, orgIds, "s.org_id");
        if (start != null) {
            sql.append(" AND s.created_at >= ? ");
            args.add(Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND s.created_at <= ? ");
            args.add(Timestamp.valueOf(end));
        }
        if (StrUtil.isNotBlank(materialName)) {
            sql.append(" AND (COALESCE(inv.material_name, s.menu_name) LIKE ?) ");
            args.add("%" + materialName.trim() + "%");
        }
        sql.append(" GROUP BY s.trace_batch_id ");
        if (ordered) {
            sql.append(" ORDER BY latest_created_at DESC ");
        }

        List<TraceAggregate> aggregates = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            TraceAggregate aggregate = new TraceAggregate();
            aggregate.setId(rs.getLong("record_id"));
            aggregate.setTraceBatchId(rs.getString("trace_batch_id"));
            aggregate.setRequestTime(toLocalDateTime(rs.getTimestamp("request_time")));
            aggregate.setCompletedAt(toLocalDateTime(rs.getTimestamp("completed_at")));
            aggregate.setBatchNo(rs.getString("batch_no"));
            aggregate.setMaterialName(rs.getString("material_name"));
            aggregate.setSupplierName(rs.getString("supplier_name"));
            return aggregate;
        }, args.toArray());

        return aggregates.stream()
                .filter(item -> {
                    String resolvedStatus = resolveTraceStatus(item);
                    return StrUtil.isBlank(status) || resolvedStatus.equals(status);
                })
                .collect(Collectors.toList());
    }

    private SupervisionTraceVO toTraceVO(TraceAggregate aggregate) {
        SupervisionTraceVO vo = new SupervisionTraceVO();
        vo.setId(aggregate.getId());
        vo.setTraceNo("TRC-" + aggregate.getTraceBatchId());
        vo.setMaterialName(aggregate.getMaterialName());
        vo.setBatchNo(aggregate.getBatchNo());
        vo.setSupplierName(StrUtil.blankToDefault(aggregate.getSupplierName(), DEFAULT_SUPPLIER));
        vo.setRequestTime(aggregate.getRequestTime());
        vo.setCompletedAt(aggregate.getCompletedAt());
        vo.setResponseTime(aggregate.getResponseHours());
        vo.setResponseTimeRange(formatTraceRange(aggregate.getResponseHours()));
        vo.setStatus(resolveTraceStatus(aggregate));
        return vo;
    }

    private String resolveTraceStatus(TraceAggregate aggregate) {
        BigDecimal hours = aggregate.getResponseHours();
        if (hours == null) {
            if (aggregate.getRequestTime() == null) {
                return "completed";
            }
            BigDecimal pendingHours = BigDecimal.valueOf(Duration.between(aggregate.getRequestTime(), LocalDateTime.now()).toMinutes())
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            return pendingHours.compareTo(TRACE_TARGET_HOURS) > 0 ? "timeout" : "completed";
        }
        return hours.compareTo(TRACE_TARGET_HOURS) > 0 ? "timeout" : "completed";
    }

    private String formatTraceRange(BigDecimal responseHours) {
        if (responseHours == null) {
            return "0.0-0.0小时";
        }
        BigDecimal min = responseHours.multiply(BigDecimal.valueOf(0.8)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal max = responseHours.multiply(BigDecimal.valueOf(1.2)).setScale(1, RoundingMode.HALF_UP);
        return min.toPlainString() + "-" + max.toPlainString() + "小时";
    }

    private String buildViolationMetricSql(Long orgId, List<Long> orgIds, DateWindow window) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM device_alert WHERE deleted = 0 AND triggered_at >= ? AND triggered_at <= ? ");
        List<Object> ignored = new ArrayList<>();
        appendOrgFilter(sql, ignored, orgId, orgIds);
        return sql.toString();
    }

    private List<Object> buildViolationMetricArgs(Long orgId, List<Long> orgIds, DateWindow window) {
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(window.getStart()));
        args.add(Timestamp.valueOf(window.getEnd()));
        appendOrgArgs(args, orgId, orgIds);
        return args;
    }

    private String buildCookTaskMetricSql(Long orgId, List<Long> orgIds, DateWindow window) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM cook_task WHERE deleted = 0 AND created_at >= ? AND created_at <= ? ");
        List<Object> ignored = new ArrayList<>();
        appendOrgFilter(sql, ignored, orgId, orgIds);
        return sql.toString();
    }

    private List<Object> buildCookTaskMetricArgs(Long orgId, List<Long> orgIds, DateWindow window) {
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(window.getStart()));
        args.add(Timestamp.valueOf(window.getEnd()));
        appendOrgArgs(args, orgId, orgIds);
        return args;
    }

    private void appendOrgFilter(StringBuilder sql, List<Object> args, Long orgId, List<Long> orgIds) {
        appendOrgFilter(sql, args, orgId, orgIds, "org_id");
    }

    private void appendOrgFilter(StringBuilder sql, List<Object> args, Long orgId, List<Long> orgIds, String column) {
        if (orgId != null) {
            sql.append(" AND ").append(column).append(" = ? ");
            args.add(orgId);
            return;
        }
        if (orgIds == null) {
            return;
        }
        if (orgIds.isEmpty()) {
            sql.append(" AND 1 = 0 ");
            return;
        }
        sql.append(" AND ").append(column).append(" IN (")
                .append(orgIds.stream().map(id -> "?").collect(Collectors.joining(",")))
                .append(") ");
        args.addAll(orgIds);
    }

    private void appendOrgArgs(List<Object> args, Long orgId, List<Long> orgIds) {
        if (orgId != null) {
            args.add(orgId);
            return;
        }
        if (orgIds != null && !orgIds.isEmpty()) {
            args.addAll(orgIds);
        }
    }

    private void checkOrgAccess(Long orgId) {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllAccess() && (orgId == null || !scope.isAllowed(orgId))) {
            throw BizException.forbidden("无权限访问该组织数据");
        }
    }

    private String resolveViolationTypeName(String violationType) {
        return ALERT_TYPE_NAME_MAP.getOrDefault(violationType, violationType);
    }

    private String resolveViolationTitle(String content, Map<String, Object> detail, String typeName) {
        Object title = detail == null ? null : detail.get("title");
        if (title != null && StrUtil.isNotBlank(title.toString())) {
            return title.toString();
        }
        if (StrUtil.isNotBlank(content)) {
            String trimmed = content.trim();
            return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
        }
        return typeName;
    }

    private String resolveLocation(Map<String, Object> detail) {
        if (detail == null) {
            return "未标注区域";
        }
        Object location = detail.get("location");
        if (location != null && StrUtil.isNotBlank(location.toString())) {
            return location.toString();
        }
        Object area = detail.get("area");
        return area == null ? "未标注区域" : area.toString();
    }

    private String resolveEmployeeName(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        List<String> names = jdbcTemplate.query(
                "SELECT real_name FROM sys_employee WHERE id = ? AND deleted = 0 LIMIT 1",
                (rs, rowNum) -> rs.getString("real_name"),
                employeeId
        );
        return names.isEmpty() ? null : names.get(0);
    }

    private Map<String, Object> parseObjectMap(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("解析JSON对象失败: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<String> parseStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            log.warn("解析JSON数组失败: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private LocalDateTime parseDateTime(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        return LocalDateTime.parse(text.trim().replace(" ", "T"));
    }

    private List<DateWindow> buildWeeklyBuckets(DateWindow current) {
        List<DateWindow> buckets = new ArrayList<>();
        LocalDate cursor = current.getStart().toLocalDate();
        LocalDate endDate = current.getEnd().toLocalDate();
        while (!cursor.isAfter(endDate)) {
            LocalDate weekEnd = cursor.plusDays(6);
            if (weekEnd.isAfter(endDate)) {
                weekEnd = endDate;
            }
            buckets.add(new DateWindow(cursor.atStartOfDay(), weekEnd.atTime(LocalTime.MAX)));
            cursor = weekEnd.plusDays(1);
        }
        return buckets;
    }

    private DateWindow resolveDateWindow(String range) {
        LocalDate today = LocalDate.now();
        String normalized = StrUtil.blankToDefault(range, "month").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "today" -> new DateWindow(today.atStartOfDay(), today.atTime(LocalTime.MAX));
            case "week" -> {
                LocalDate start = today.with(DayOfWeek.MONDAY);
                LocalDate end = today.with(DayOfWeek.SUNDAY);
                yield new DateWindow(start.atStartOfDay(), end.atTime(LocalTime.MAX));
            }
            case "quarter" -> {
                int quarter = ((today.getMonthValue() - 1) / 3) + 1;
                int startMonth = (quarter - 1) * 3 + 1;
                LocalDate start = LocalDate.of(today.getYear(), startMonth, 1);
                LocalDate end = start.plusMonths(3).minusDays(1);
                yield new DateWindow(start.atStartOfDay(), end.atTime(LocalTime.MAX));
            }
            case "year" -> {
                LocalDate start = today.with(TemporalAdjusters.firstDayOfYear());
                LocalDate end = today.with(TemporalAdjusters.lastDayOfYear());
                yield new DateWindow(start.atStartOfDay(), end.atTime(LocalTime.MAX));
            }
            default -> {
                YearMonth month = YearMonth.from(today);
                yield new DateWindow(month.atDay(1).atStartOfDay(), month.atEndOfMonth().atTime(LocalTime.MAX));
            }
        };
    }

    private BigDecimal computeChangePercent(BigDecimal current, BigDecimal baseline) {
        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : HUNDRED;
        }
        return current.subtract(baseline)
                .multiply(HUNDRED)
                .divide(baseline.abs(), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(HUNDRED).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal queryForDecimal(String sql, List<Object> args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args.toArray());
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private long queryForLong(String sql, List<Object> args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return value == null ? 0L : value;
    }

    private Map<String, Object> mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        int count = rs.getMetaData().getColumnCount();
        for (int i = 1; i <= count; i++) {
            row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
        }
        return row;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private static class TraceAggregate {
        private Long id;
        private String traceBatchId;
        private LocalDateTime requestTime;
        private LocalDateTime completedAt;
        private String batchNo;
        private String materialName;
        private String supplierName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTraceBatchId() {
            return traceBatchId;
        }

        public void setTraceBatchId(String traceBatchId) {
            this.traceBatchId = traceBatchId;
        }

        public LocalDateTime getRequestTime() {
            return requestTime;
        }

        public void setRequestTime(LocalDateTime requestTime) {
            this.requestTime = requestTime;
        }

        public LocalDateTime getCompletedAt() {
            return completedAt;
        }

        public void setCompletedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
        }

        public String getBatchNo() {
            return batchNo;
        }

        public void setBatchNo(String batchNo) {
            this.batchNo = batchNo;
        }

        public String getMaterialName() {
            return materialName;
        }

        public void setMaterialName(String materialName) {
            this.materialName = materialName;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public BigDecimal getResponseHours() {
            if (requestTime == null || completedAt == null) {
                return null;
            }
            long minutes = Duration.between(requestTime, completedAt).toMinutes();
            if (minutes < 0) {
                minutes = 0;
            }
            return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }
    }

    private static class DateWindow {
        private final LocalDateTime start;
        private final LocalDateTime end;

        private DateWindow(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        public LocalDateTime getStart() {
            return start;
        }

        public LocalDateTime getEnd() {
            return end;
        }

        public DateWindow previous() {
            Duration duration = Duration.between(start, end).plusSeconds(1);
            LocalDateTime previousEnd = start.minusSeconds(1);
            return new DateWindow(previousEnd.minus(duration).plusSeconds(1), previousEnd);
        }

        public DateWindow samePeriodLastYear() {
            return new DateWindow(start.minusYears(1), end.minusYears(1));
        }
    }
}
