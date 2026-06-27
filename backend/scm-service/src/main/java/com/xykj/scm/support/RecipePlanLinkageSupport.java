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
 * 菜谱计划与采购计划关联数量核算支持类。
 */
@Component
@RequiredArgsConstructor
public class RecipePlanLinkageSupport {

    public static final String STATUS_UNUSED = "未占用";
    public static final String STATUS_PARTIAL = "部分占用";
    public static final String STATUS_FULL = "全部占用";

    private static final BigDecimal ZERO_QUANTITY = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);

    private final JdbcTemplate jdbcTemplate;

    public Map<Long, RecipePlanLinkageSnapshot> loadByRecipePlanIds(
            Collection<Long> recipePlanIds,
            Long tenantId,
            Long excludePlanId
    ) {
        List<Long> validRecipePlanIds = normalizeIds(recipePlanIds);
        if (validRecipePlanIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, RecipePlanHeader> headerMap = queryRecipePlanHeadersByIds(validRecipePlanIds, tenantId);
        if (headerMap.isEmpty()) {
            return Collections.emptyMap();
        }

        List<RecipePlanItemRow> itemRows = queryRecipePlanItemRowsByIds(validRecipePlanIds, tenantId, false);
        Map<String, BigDecimal> occupiedQtyMap = queryOccupiedQtyMap(
                headerMap.values().stream().map(RecipePlanHeader::getPlanCode).collect(Collectors.toCollection(LinkedHashSet::new)),
                tenantId,
                excludePlanId
        );
        return buildSnapshotMap(headerMap, itemRows, occupiedQtyMap);
    }

    public RecipePlanLinkageSnapshot loadByRecipePlanId(
            Long recipePlanId,
            Long tenantId,
            Long excludePlanId,
            boolean lockRows
    ) {
        if (recipePlanId == null || recipePlanId <= 0L) {
            return null;
        }

        RecipePlanHeader header = queryRecipePlanHeaderById(recipePlanId, tenantId);
        if (header == null) {
            return null;
        }

        List<RecipePlanItemRow> itemRows = queryRecipePlanItemRowsByIds(Collections.singletonList(recipePlanId), tenantId, lockRows);
        Map<String, BigDecimal> occupiedQtyMap = queryOccupiedQtyMap(
                Collections.singleton(header.getPlanCode()),
                tenantId,
                excludePlanId
        );
        return buildSnapshot(header, itemRows, occupiedQtyMap);
    }

    public RecipePlanLinkageSnapshot loadByRecipePlanCode(
            String planCode,
            Long tenantId,
            Long excludePlanId,
            boolean lockRows
    ) {
        String normalizedPlanCode = StrUtil.trimToNull(planCode);
        if (normalizedPlanCode == null) {
            return null;
        }

        RecipePlanHeader header = queryRecipePlanHeaderByCode(normalizedPlanCode, tenantId);
        if (header == null) {
            return null;
        }

        List<RecipePlanItemRow> itemRows = queryRecipePlanItemRowsByPlanCode(normalizedPlanCode, tenantId, lockRows);
        Map<String, BigDecimal> occupiedQtyMap = queryOccupiedQtyMap(
                Collections.singleton(normalizedPlanCode),
                tenantId,
                excludePlanId
        );
        return buildSnapshot(header, itemRows, occupiedQtyMap);
    }

    private Map<Long, RecipePlanHeader> queryRecipePlanHeadersByIds(Collection<Long> recipePlanIds, Long tenantId) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(recipePlanIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, plan_code AS planCode, org_id AS orgId " +
                        "FROM recipe_plan " +
                        "WHERE deleted = 0 AND (tenant_id = ? OR tenant_id IS NULL OR tenant_id = 0) " +
                        "  AND id IN (" + placeholders(recipePlanIds.size()) + ")",
                args.toArray()
        );

        Map<Long, RecipePlanHeader> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long recipePlanId = toLong(row.get("id"));
            String planCode = asString(row.get("planCode"));
            if (recipePlanId == null || StrUtil.isBlank(planCode)) {
                continue;
            }
            result.put(recipePlanId, new RecipePlanHeader(recipePlanId, planCode, toLong(row.get("orgId"))));
        }
        return result;
    }

    private RecipePlanHeader queryRecipePlanHeaderById(Long recipePlanId, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, plan_code AS planCode, org_id AS orgId " +
                        "FROM recipe_plan " +
                        "WHERE deleted = 0 AND (tenant_id = ? OR tenant_id IS NULL OR tenant_id = 0) " +
                        "  AND id = ? LIMIT 1",
                tenantId,
                recipePlanId
        );
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = rows.get(0);
        Long id = toLong(row.get("id"));
        String planCode = asString(row.get("planCode"));
        if (id == null || StrUtil.isBlank(planCode)) {
            return null;
        }
        return new RecipePlanHeader(id, planCode, toLong(row.get("orgId")));
    }

    private RecipePlanHeader queryRecipePlanHeaderByCode(String planCode, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, plan_code AS planCode, org_id AS orgId " +
                        "FROM recipe_plan " +
                        "WHERE deleted = 0 AND (tenant_id = ? OR tenant_id IS NULL OR tenant_id = 0) " +
                        "  AND plan_code = ? LIMIT 1",
                tenantId,
                planCode
        );
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = rows.get(0);
        Long id = toLong(row.get("id"));
        String normalizedPlanCode = asString(row.get("planCode"));
        if (id == null || StrUtil.isBlank(normalizedPlanCode)) {
            return null;
        }
        return new RecipePlanHeader(id, normalizedPlanCode, toLong(row.get("orgId")));
    }

    private List<RecipePlanItemRow> queryRecipePlanItemRowsByIds(
            Collection<Long> recipePlanIds,
            Long tenantId,
            boolean lockRows
    ) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(recipePlanIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT rp.id AS recipePlanId, ri.material_id AS materialId, " +
                        "MAX(ri.material_name) AS materialName, " +
                        "COALESCE(MAX(NULLIF(ri.material_spec, '')), MAX(NULLIF(m.spec, '')), '') AS materialSpec, " +
                        "COALESCE(MAX(NULLIF(ri.unit, '')), MAX(NULLIF(m.unit, '')), '') AS unit, " +
                        "COALESCE(SUM(COALESCE(ri.quantity, 0) * COALESCE(rpi.planned_servings, 0)), 0) AS originalQty, " +
                        "MIN(COALESCE(rpi.sort_order, 0)) AS planSortOrder, " +
                        "MIN(COALESCE(ri.sort_order, 0)) AS ingredientSortOrder " +
                        "FROM recipe_plan rp " +
                        "JOIN recipe_plan_item rpi ON rpi.plan_id = rp.id AND rpi.deleted = 0 " +
                        "JOIN recipe_ingredient ri ON ri.recipe_id = rpi.recipe_id AND ri.deleted = 0 " +
                        "LEFT JOIN wms_material m ON m.id = ri.material_id AND m.deleted = 0 " +
                        "WHERE rp.deleted = 0 AND (rp.tenant_id = ? OR rp.tenant_id IS NULL OR rp.tenant_id = 0) " +
                        "  AND rp.id IN (" + placeholders(recipePlanIds.size()) + ") " +
                        "GROUP BY rp.id, ri.material_id " +
                        "ORDER BY planSortOrder ASC, ingredientSortOrder ASC, ri.material_id ASC" +
                        (lockRows ? " FOR UPDATE" : ""),
                args.toArray()
        );
        return toRecipePlanItemRows(rows);
    }

    private List<RecipePlanItemRow> queryRecipePlanItemRowsByPlanCode(
            String planCode,
            Long tenantId,
            boolean lockRows
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT rp.id AS recipePlanId, ri.material_id AS materialId, " +
                        "MAX(ri.material_name) AS materialName, " +
                        "COALESCE(MAX(NULLIF(ri.material_spec, '')), MAX(NULLIF(m.spec, '')), '') AS materialSpec, " +
                        "COALESCE(MAX(NULLIF(ri.unit, '')), MAX(NULLIF(m.unit, '')), '') AS unit, " +
                        "COALESCE(SUM(COALESCE(ri.quantity, 0) * COALESCE(rpi.planned_servings, 0)), 0) AS originalQty, " +
                        "MIN(COALESCE(rpi.sort_order, 0)) AS planSortOrder, " +
                        "MIN(COALESCE(ri.sort_order, 0)) AS ingredientSortOrder " +
                        "FROM recipe_plan rp " +
                        "JOIN recipe_plan_item rpi ON rpi.plan_id = rp.id AND rpi.deleted = 0 " +
                        "JOIN recipe_ingredient ri ON ri.recipe_id = rpi.recipe_id AND ri.deleted = 0 " +
                        "LEFT JOIN wms_material m ON m.id = ri.material_id AND m.deleted = 0 " +
                        "WHERE rp.deleted = 0 AND (rp.tenant_id = ? OR rp.tenant_id IS NULL OR rp.tenant_id = 0) " +
                        "  AND rp.plan_code = ? " +
                        "GROUP BY rp.id, ri.material_id " +
                        "ORDER BY planSortOrder ASC, ingredientSortOrder ASC, ri.material_id ASC" +
                        (lockRows ? " FOR UPDATE" : ""),
                tenantId,
                planCode
        );
        return toRecipePlanItemRows(rows);
    }

    private List<RecipePlanItemRow> toRecipePlanItemRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<RecipePlanItemRow> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long recipePlanId = toLong(row.get("recipePlanId"));
            Long materialId = toLong(row.get("materialId"));
            BigDecimal originalQty = scaleQuantity(toBigDecimal(row.get("originalQty")));
            if (recipePlanId == null || materialId == null || originalQty.compareTo(ZERO_QUANTITY) <= 0) {
                continue;
            }
            result.add(new RecipePlanItemRow(
                    recipePlanId,
                    materialId,
                    asString(row.get("materialName")),
                    asString(row.get("materialSpec")),
                    asString(row.get("unit")),
                    originalQty,
                    toInteger(row.get("planSortOrder"))
            ));
        }
        return result;
    }

    private Map<String, BigDecimal> queryOccupiedQtyMap(
            Collection<String> planCodes,
            Long tenantId,
            Long excludePlanId
    ) {
        List<String> validPlanCodes = planCodes == null
                ? Collections.emptyList()
                : planCodes.stream().filter(StrUtil::isNotBlank).distinct().toList();
        if (validPlanCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(validPlanCodes);
        StringBuilder sql = new StringBuilder(
                "SELECT p.related_document AS planCode, i.material_id AS materialId, COALESCE(SUM(i.plan_qty), 0) AS occupiedQty " +
                        "FROM scm_purchase_plan p " +
                        "JOIN scm_purchase_plan_item i ON i.plan_id = p.id " +
                        "WHERE p.deleted = 0 AND p.tenant_id = ? " +
                        "  AND p.status IN ('draft', 'pending', 'approved') " +
                        "  AND p.related_document IN (" + placeholders(validPlanCodes.size()) + ")"
        );
        if (excludePlanId != null && excludePlanId > 0L) {
            sql.append(" AND p.id <> ?");
            args.add(excludePlanId);
        }
        sql.append(" GROUP BY p.related_document, i.material_id");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String planCode = asString(row.get("planCode"));
            Long materialId = toLong(row.get("materialId"));
            if (StrUtil.isBlank(planCode) || materialId == null) {
                continue;
            }
            result.put(buildOccupancyKey(planCode, materialId), scaleQuantity(toBigDecimal(row.get("occupiedQty"))));
        }
        return result;
    }

    private Map<Long, RecipePlanLinkageSnapshot> buildSnapshotMap(
            Map<Long, RecipePlanHeader> headerMap,
            List<RecipePlanItemRow> itemRows,
            Map<String, BigDecimal> occupiedQtyMap
    ) {
        Map<Long, List<RecipePlanItemRow>> itemRowMap = itemRows.stream()
                .collect(Collectors.groupingBy(RecipePlanItemRow::getRecipePlanId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, RecipePlanLinkageSnapshot> result = new LinkedHashMap<>();
        for (Map.Entry<Long, RecipePlanHeader> entry : headerMap.entrySet()) {
            result.put(entry.getKey(), buildSnapshot(
                    entry.getValue(),
                    itemRowMap.getOrDefault(entry.getKey(), Collections.emptyList()),
                    occupiedQtyMap
            ));
        }
        return result;
    }

    private RecipePlanLinkageSnapshot buildSnapshot(
            RecipePlanHeader header,
            List<RecipePlanItemRow> itemRows,
            Map<String, BigDecimal> occupiedQtyMap
    ) {
        Map<Long, RecipeMaterialAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (RecipePlanItemRow row : itemRows) {
            RecipeMaterialAccumulator accumulator = accumulatorMap.computeIfAbsent(
                    row.getMaterialId(),
                    key -> new RecipeMaterialAccumulator(row)
            );
            accumulator.addOriginalQty(row.getOriginalQty());
        }

        List<RecipeMaterialLinkage> items = new ArrayList<>();
        for (RecipeMaterialAccumulator accumulator : accumulatorMap.values()) {
            BigDecimal originalQty = scaleQuantity(accumulator.getOriginalQty());
            BigDecimal occupiedQty = scaleQuantity(occupiedQtyMap.get(buildOccupancyKey(header.getPlanCode(), accumulator.getMaterialId())));
            BigDecimal availableQty = originalQty.subtract(occupiedQty);
            if (availableQty.compareTo(ZERO_QUANTITY) < 0) {
                availableQty = ZERO_QUANTITY;
            }

            RecipeMaterialLinkage item = new RecipeMaterialLinkage();
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

        RecipePlanLinkageSnapshot snapshot = new RecipePlanLinkageSnapshot();
        snapshot.setRecipePlanId(header.getRecipePlanId());
        snapshot.setPlanCode(header.getPlanCode());
        snapshot.setOrgId(header.getOrgId());
        snapshot.setItems(items);
        snapshot.setMaterialPlanStatus(resolveOverallStatus(items));
        return snapshot;
    }

    public String resolveOverallStatus(Collection<RecipeMaterialLinkage> items) {
        if (items == null || items.isEmpty()) {
            return STATUS_UNUSED;
        }

        boolean allUnused = true;
        boolean allFull = true;
        for (RecipeMaterialLinkage item : items) {
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

    private String buildOccupancyKey(String planCode, Long materialId) {
        return planCode + "#" + materialId;
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
    private static final class RecipePlanHeader {

        private final Long recipePlanId;
        private final String planCode;
        private final Long orgId;

        private RecipePlanHeader(Long recipePlanId, String planCode, Long orgId) {
            this.recipePlanId = recipePlanId;
            this.planCode = planCode;
            this.orgId = orgId;
        }
    }

    @Getter
    private static final class RecipePlanItemRow {

        private final Long recipePlanId;
        private final Long materialId;
        private final String materialName;
        private final String materialSpec;
        private final String unit;
        private final BigDecimal originalQty;
        private final Integer sortOrder;

        private RecipePlanItemRow(
                Long recipePlanId,
                Long materialId,
                String materialName,
                String materialSpec,
                String unit,
                BigDecimal originalQty,
                Integer sortOrder
        ) {
            this.recipePlanId = recipePlanId;
            this.materialId = materialId;
            this.materialName = materialName;
            this.materialSpec = materialSpec;
            this.unit = unit;
            this.originalQty = originalQty;
            this.sortOrder = sortOrder;
        }
    }

    @Getter
    private static final class RecipeMaterialAccumulator {

        private final Long materialId;
        private final String materialName;
        private final String materialSpec;
        private final String unit;
        private final Integer sortOrder;
        private BigDecimal originalQty;

        private RecipeMaterialAccumulator(RecipePlanItemRow row) {
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
    public static class RecipePlanLinkageSnapshot {

        private Long recipePlanId;
        private String planCode;
        private Long orgId;
        private String materialPlanStatus;
        private List<RecipeMaterialLinkage> items = new ArrayList<>();

        public Map<Long, RecipeMaterialLinkage> toMaterialMap() {
            Map<Long, RecipeMaterialLinkage> result = new LinkedHashMap<>();
            for (RecipeMaterialLinkage item : items) {
                if (item == null || item.getMaterialId() == null) {
                    continue;
                }
                result.put(item.getMaterialId(), item);
            }
            return result;
        }

        public void setRecipePlanId(Long recipePlanId) {
            this.recipePlanId = recipePlanId;
        }

        public void setPlanCode(String planCode) {
            this.planCode = planCode;
        }

        public void setOrgId(Long orgId) {
            this.orgId = orgId;
        }

        public void setMaterialPlanStatus(String materialPlanStatus) {
            this.materialPlanStatus = materialPlanStatus;
        }

        public void setItems(List<RecipeMaterialLinkage> items) {
            this.items = items == null ? new ArrayList<>() : items;
        }
    }

    @Getter
    public static class RecipeMaterialLinkage {

        private Long materialId;
        private String materialName;
        private String materialSpec;
        private String unit;
        private BigDecimal originalQty;
        private BigDecimal occupiedQty;
        private BigDecimal availableQty;
        private String materialPlanStatus;
        private Integer sortOrder;

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
