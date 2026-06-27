package com.xykj.scm.support;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 采购需求预测与采购计划关联数量核算支持类。
 */
@Component
@RequiredArgsConstructor
public class PurchaseDemandForecastLinkageSupport {

    public static final String STATUS_UNUSED = "未占用";
    public static final String STATUS_PARTIAL = "部分占用";
    public static final String STATUS_FULL = "全部占用";

    private static final BigDecimal ZERO_QUANTITY = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);

    private final JdbcTemplate jdbcTemplate;

    public Map<Long, ForecastLinkageSnapshot> loadByForecastIds(
            Collection<Long> forecastIds,
            Long tenantId,
            Long excludePlanId
    ) {
        List<Long> validForecastIds = normalizeIds(forecastIds);
        if (validForecastIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, ForecastHeader> headerMap = queryForecastHeadersByIds(validForecastIds, tenantId);
        if (headerMap.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ForecastItemRow> itemRows = queryForecastItemRowsByIds(validForecastIds, tenantId, false);
        Map<String, BigDecimal> occupiedQtyMap = queryOccupiedQtyMap(
                headerMap.values().stream().map(ForecastHeader::getForecastNo).collect(Collectors.toCollection(LinkedHashSet::new)),
                tenantId,
                excludePlanId
        );
        return buildSnapshotMap(headerMap, itemRows, occupiedQtyMap);
    }

    public ForecastLinkageSnapshot loadByForecastNo(
            String forecastNo,
            Long tenantId,
            Long excludePlanId,
            boolean lockRows
    ) {
        String normalizedForecastNo = StrUtil.trimToNull(forecastNo);
        if (normalizedForecastNo == null) {
            return null;
        }

        ForecastHeader header = queryForecastHeaderByNo(normalizedForecastNo, tenantId);
        if (header == null) {
            return null;
        }

        List<ForecastItemRow> itemRows = queryForecastItemRowsByForecastNo(normalizedForecastNo, tenantId, lockRows);
        Map<String, BigDecimal> occupiedQtyMap = queryOccupiedQtyMap(
                Collections.singleton(normalizedForecastNo),
                tenantId,
                excludePlanId
        );
        return buildSnapshot(header, itemRows, occupiedQtyMap);
    }

    private Map<Long, ForecastHeader> queryForecastHeadersByIds(Collection<Long> forecastIds, Long tenantId) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(forecastIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, forecast_no AS forecastNo, org_id AS orgId " +
                        "FROM scm_purchase_demand_forecast " +
                        "WHERE deleted = 0 AND tenant_id = ? AND id IN (" + placeholders(forecastIds.size()) + ")",
                args.toArray()
        );

        Map<Long, ForecastHeader> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long forecastId = toLong(row.get("id"));
            String forecastNo = asString(row.get("forecastNo"));
            if (forecastId == null || StrUtil.isBlank(forecastNo)) {
                continue;
            }
            result.put(forecastId, new ForecastHeader(forecastId, forecastNo, toLong(row.get("orgId"))));
        }
        return result;
    }

    private ForecastHeader queryForecastHeaderByNo(String forecastNo, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, forecast_no AS forecastNo, org_id AS orgId " +
                        "FROM scm_purchase_demand_forecast " +
                        "WHERE deleted = 0 AND tenant_id = ? AND forecast_no = ? LIMIT 1",
                tenantId,
                forecastNo
        );
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = rows.get(0);
        Long forecastId = toLong(row.get("id"));
        String normalizedForecastNo = asString(row.get("forecastNo"));
        if (forecastId == null || StrUtil.isBlank(normalizedForecastNo)) {
            return null;
        }
        return new ForecastHeader(forecastId, normalizedForecastNo, toLong(row.get("orgId")));
    }

    private List<ForecastItemRow> queryForecastItemRowsByIds(Collection<Long> forecastIds, Long tenantId, boolean lockRows) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(tenantId);
        args.addAll(forecastIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT fi.id AS forecastDetailId, fi.forecast_id AS forecastId, " +
                        "fi.material_id AS materialId, fi.material_name AS materialName, fi.material_spec AS materialSpec, " +
                        "fi.material_unit AS unit, fi.suggested_qty AS originalQty, fi.sort_order AS sortOrder " +
                        "FROM scm_purchase_demand_forecast_item fi " +
                        "JOIN scm_purchase_demand_forecast f ON f.id = fi.forecast_id AND f.deleted = 0 AND f.tenant_id = ? " +
                        "WHERE fi.deleted = 0 AND fi.tenant_id = ? " +
                        "  AND fi.suggested_qty > 0 " +
                        "  AND fi.forecast_id IN (" + placeholders(forecastIds.size()) + ") " +
                        "ORDER BY fi.forecast_id ASC, fi.sort_order ASC, fi.id ASC" +
                        (lockRows ? " FOR UPDATE" : ""),
                args.toArray()
        );
        return toForecastItemRows(rows);
    }

    private List<ForecastItemRow> queryForecastItemRowsByForecastNo(String forecastNo, Long tenantId, boolean lockRows) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT fi.id AS forecastDetailId, fi.forecast_id AS forecastId, " +
                        "fi.material_id AS materialId, fi.material_name AS materialName, fi.material_spec AS materialSpec, " +
                        "fi.material_unit AS unit, fi.suggested_qty AS originalQty, fi.sort_order AS sortOrder " +
                        "FROM scm_purchase_demand_forecast_item fi " +
                        "JOIN scm_purchase_demand_forecast f ON f.id = fi.forecast_id AND f.deleted = 0 AND f.tenant_id = ? " +
                        "WHERE fi.deleted = 0 AND fi.tenant_id = ? " +
                        "  AND fi.suggested_qty > 0 " +
                        "  AND f.forecast_no = ? " +
                        "ORDER BY fi.sort_order ASC, fi.id ASC" +
                        (lockRows ? " FOR UPDATE" : ""),
                tenantId,
                tenantId,
                forecastNo
        );
        return toForecastItemRows(rows);
    }

    private List<ForecastItemRow> toForecastItemRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ForecastItemRow> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long forecastId = toLong(row.get("forecastId"));
            Long materialId = toLong(row.get("materialId"));
            BigDecimal originalQty = scaleQuantity(toBigDecimal(row.get("originalQty")));
            if (forecastId == null || materialId == null || originalQty.compareTo(ZERO_QUANTITY) <= 0) {
                continue;
            }
            result.add(new ForecastItemRow(
                    toLong(row.get("forecastDetailId")),
                    forecastId,
                    materialId,
                    asString(row.get("materialName")),
                    asString(row.get("materialSpec")),
                    asString(row.get("unit")),
                    originalQty,
                    toInteger(row.get("sortOrder"))
            ));
        }
        return result;
    }

    private Map<String, BigDecimal> queryOccupiedQtyMap(
            Collection<String> forecastNos,
            Long tenantId,
            Long excludePlanId
    ) {
        List<String> validForecastNos = forecastNos == null
                ? Collections.emptyList()
                : forecastNos.stream().filter(StrUtil::isNotBlank).distinct().toList();
        if (validForecastNos.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(validForecastNos);
        StringBuilder sql = new StringBuilder(
                "SELECT p.related_document AS forecastNo, i.material_id AS materialId, COALESCE(SUM(i.plan_qty), 0) AS occupiedQty " +
                        "FROM scm_purchase_plan p " +
                        "JOIN scm_purchase_plan_item i ON i.plan_id = p.id " +
                        "WHERE p.deleted = 0 AND p.tenant_id = ? " +
                        "  AND p.status IN ('draft', 'pending', 'approved') " +
                        "  AND p.related_document IN (" + placeholders(validForecastNos.size()) + ")"
        );
        if (excludePlanId != null && excludePlanId > 0L) {
            sql.append(" AND p.id <> ?");
            args.add(excludePlanId);
        }
        sql.append(" GROUP BY p.related_document, i.material_id");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String forecastNo = asString(row.get("forecastNo"));
            Long materialId = toLong(row.get("materialId"));
            if (StrUtil.isBlank(forecastNo) || materialId == null) {
                continue;
            }
            result.put(buildOccupancyKey(forecastNo, materialId), scaleQuantity(toBigDecimal(row.get("occupiedQty"))));
        }
        return result;
    }

    private Map<Long, ForecastLinkageSnapshot> buildSnapshotMap(
            Map<Long, ForecastHeader> headerMap,
            List<ForecastItemRow> itemRows,
            Map<String, BigDecimal> occupiedQtyMap
    ) {
        Map<Long, List<ForecastItemRow>> itemRowMap = itemRows.stream()
                .collect(Collectors.groupingBy(ForecastItemRow::getForecastId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, ForecastLinkageSnapshot> result = new LinkedHashMap<>();
        for (Map.Entry<Long, ForecastHeader> entry : headerMap.entrySet()) {
            result.put(entry.getKey(), buildSnapshot(
                    entry.getValue(),
                    itemRowMap.getOrDefault(entry.getKey(), Collections.emptyList()),
                    occupiedQtyMap
            ));
        }
        return result;
    }

    private ForecastLinkageSnapshot buildSnapshot(
            ForecastHeader header,
            List<ForecastItemRow> itemRows,
            Map<String, BigDecimal> occupiedQtyMap
    ) {
        Map<Long, ForecastMaterialAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (ForecastItemRow row : itemRows) {
            ForecastMaterialAccumulator accumulator = accumulatorMap.computeIfAbsent(
                    row.getMaterialId(),
                    key -> new ForecastMaterialAccumulator(row)
            );
            accumulator.addOriginalQty(row.getOriginalQty());
        }

        List<ForecastMaterialLinkage> items = new ArrayList<>();
        for (ForecastMaterialAccumulator accumulator : accumulatorMap.values()) {
            BigDecimal originalQty = scaleQuantity(accumulator.getOriginalQty());
            BigDecimal occupiedQty = scaleQuantity(occupiedQtyMap.get(buildOccupancyKey(header.getForecastNo(), accumulator.getMaterialId())));
            BigDecimal availableQty = originalQty.subtract(occupiedQty);
            if (availableQty.compareTo(ZERO_QUANTITY) < 0) {
                availableQty = ZERO_QUANTITY;
            }

            ForecastMaterialLinkage item = new ForecastMaterialLinkage();
            item.setForecastDetailId(accumulator.getForecastDetailId());
            item.setMaterialId(accumulator.getMaterialId());
            item.setMaterialName(accumulator.getMaterialName());
            item.setMaterialSpec(accumulator.getMaterialSpec());
            item.setUnit(accumulator.getUnit());
            item.setOriginalQty(originalQty);
            item.setOccupiedQty(occupiedQty);
            item.setAvailableQty(scaleQuantity(availableQty));
            item.setMaterialPlanStatus(resolveMaterialPlanStatus(originalQty, availableQty));
            item.setSortOrder(accumulator.getSortOrder());
            items.add(item);
        }

        ForecastLinkageSnapshot snapshot = new ForecastLinkageSnapshot();
        snapshot.setForecastId(header.getForecastId());
        snapshot.setForecastNo(header.getForecastNo());
        snapshot.setOrgId(header.getOrgId());
        snapshot.setItems(items);
        snapshot.setMaterialPlanStatus(resolveOverallStatus(items));
        return snapshot;
    }

    public String resolveOverallStatus(Collection<ForecastMaterialLinkage> items) {
        if (items == null || items.isEmpty()) {
            return STATUS_UNUSED;
        }

        boolean allUnused = true;
        boolean allFull = true;
        for (ForecastMaterialLinkage item : items) {
            if (item == null || item.getOriginalQty() == null || item.getOriginalQty().compareTo(ZERO_QUANTITY) <= 0) {
                continue;
            }
            BigDecimal originalQty = scaleQuantity(item.getOriginalQty());
            BigDecimal availableQty = scaleQuantity(item.getAvailableQty());
            if (availableQty.compareTo(originalQty) < 0) {
                allUnused = false;
            }
            if (availableQty.compareTo(ZERO_QUANTITY) > 0) {
                allFull = false;
            }
        }
        if (allUnused) {
            return STATUS_UNUSED;
        }
        if (allFull) {
            return STATUS_FULL;
        }
        return STATUS_PARTIAL;
    }

    public String resolveMaterialPlanStatus(BigDecimal originalQty, BigDecimal availableQty) {
        BigDecimal normalizedOriginalQty = scaleQuantity(originalQty);
        BigDecimal normalizedAvailableQty = scaleQuantity(availableQty);
        if (normalizedOriginalQty.compareTo(ZERO_QUANTITY) <= 0) {
            return STATUS_UNUSED;
        }
        if (normalizedAvailableQty.compareTo(normalizedOriginalQty) >= 0) {
            return STATUS_UNUSED;
        }
        if (normalizedAvailableQty.compareTo(ZERO_QUANTITY) <= 0) {
            return STATUS_FULL;
        }
        return STATUS_PARTIAL;
    }

    private String buildOccupancyKey(String forecastNo, Long materialId) {
        return forecastNo + "#" + materialId;
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0L)
                .distinct()
                .toList();
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal scaleQuantity(BigDecimal value) {
        return value == null ? ZERO_QUANTITY : value.setScale(3, RoundingMode.HALF_UP);
    }

    @Getter
    private static final class ForecastHeader {

        private final Long forecastId;
        private final String forecastNo;
        private final Long orgId;

        private ForecastHeader(Long forecastId, String forecastNo, Long orgId) {
            this.forecastId = forecastId;
            this.forecastNo = forecastNo;
            this.orgId = orgId;
        }
    }

    @Getter
    private static final class ForecastItemRow {

        private final Long forecastDetailId;
        private final Long forecastId;
        private final Long materialId;
        private final String materialName;
        private final String materialSpec;
        private final String unit;
        private final BigDecimal originalQty;
        private final Integer sortOrder;

        private ForecastItemRow(
                Long forecastDetailId,
                Long forecastId,
                Long materialId,
                String materialName,
                String materialSpec,
                String unit,
                BigDecimal originalQty,
                Integer sortOrder
        ) {
            this.forecastDetailId = forecastDetailId;
            this.forecastId = forecastId;
            this.materialId = materialId;
            this.materialName = materialName;
            this.materialSpec = materialSpec;
            this.unit = unit;
            this.originalQty = originalQty;
            this.sortOrder = sortOrder;
        }
    }

    @Getter
    private static final class ForecastMaterialAccumulator {

        private Long forecastDetailId;
        private final Long materialId;
        private final String materialName;
        private final String materialSpec;
        private final String unit;
        private final Integer sortOrder;
        private BigDecimal originalQty;

        private ForecastMaterialAccumulator(ForecastItemRow row) {
            this.forecastDetailId = row.getForecastDetailId();
            this.materialId = row.getMaterialId();
            this.materialName = row.getMaterialName();
            this.materialSpec = row.getMaterialSpec();
            this.unit = row.getUnit();
            this.sortOrder = row.getSortOrder();
            this.originalQty = ZERO_QUANTITY;
        }

        private void addOriginalQty(BigDecimal quantity) {
            this.originalQty = this.originalQty.add(quantity == null ? ZERO_QUANTITY : quantity);
        }
    }

    @Getter
    public static class ForecastLinkageSnapshot {

        private Long forecastId;
        private String forecastNo;
        private Long orgId;
        private String materialPlanStatus;
        private List<ForecastMaterialLinkage> items = new ArrayList<>();

        public Map<Long, ForecastMaterialLinkage> toMaterialMap() {
            Map<Long, ForecastMaterialLinkage> result = new LinkedHashMap<>();
            for (ForecastMaterialLinkage item : items) {
                if (item == null || item.getMaterialId() == null) {
                    continue;
                }
                result.put(item.getMaterialId(), item);
            }
            return result;
        }

        public void setForecastId(Long forecastId) {
            this.forecastId = forecastId;
        }

        public void setForecastNo(String forecastNo) {
            this.forecastNo = forecastNo;
        }

        public void setOrgId(Long orgId) {
            this.orgId = orgId;
        }

        public void setMaterialPlanStatus(String materialPlanStatus) {
            this.materialPlanStatus = materialPlanStatus;
        }

        public void setItems(List<ForecastMaterialLinkage> items) {
            this.items = items == null ? new ArrayList<>() : items;
        }
    }

    @Getter
    public static class ForecastMaterialLinkage {

        private Long forecastDetailId;
        private Long materialId;
        private String materialName;
        private String materialSpec;
        private String unit;
        private BigDecimal originalQty;
        private BigDecimal occupiedQty;
        private BigDecimal availableQty;
        private String materialPlanStatus;
        private Integer sortOrder;

        public void setForecastDetailId(Long forecastDetailId) {
            this.forecastDetailId = forecastDetailId;
        }

        public void setMaterialId(Long materialId) {
            this.materialId = materialId;
        }

        public void setMaterialName(String materialName) {
            this.materialName = materialName;
        }

        public void setMaterialSpec(String materialSpec) {
            this.materialSpec = materialSpec;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public void setOriginalQty(BigDecimal originalQty) {
            this.originalQty = originalQty;
        }

        public void setOccupiedQty(BigDecimal occupiedQty) {
            this.occupiedQty = occupiedQty;
        }

        public void setAvailableQty(BigDecimal availableQty) {
            this.availableQty = availableQty;
        }

        public void setMaterialPlanStatus(String materialPlanStatus) {
            this.materialPlanStatus = materialPlanStatus;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}
