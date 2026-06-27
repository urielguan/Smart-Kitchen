package com.xykj.wms.service.support;

import com.xykj.wms.entity.InboundOrder;
import com.xykj.wms.entity.InboundOrderItem;
import com.xykj.wms.entity.Location;
import com.xykj.wms.entity.LocationAreaLedger;
import com.xykj.wms.entity.OutboundOrder;
import com.xykj.wms.entity.OutboundOrderItem;
import com.xykj.wms.entity.StocktakeOrder;
import com.xykj.wms.entity.StocktakeOrderItem;
import com.xykj.wms.mapper.InventoryMapper;
import com.xykj.wms.mapper.LocationAreaLedgerMapper;
import com.xykj.wms.mapper.LocationMapper;
import com.xykj.wms.mapper.MaterialMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LocationAreaPostingService {

    private final LocationAreaLedgerMapper locationAreaLedgerMapper;
    private final LocationMapper locationMapper;
    private final InventoryMapper inventoryMapper;
    private final MaterialMapper materialMapper;

    @Autowired
    public LocationAreaPostingService(LocationAreaLedgerMapper locationAreaLedgerMapper,
                                      LocationMapper locationMapper,
                                      InventoryMapper inventoryMapper,
                                      MaterialMapper materialMapper) {
        this.locationAreaLedgerMapper = locationAreaLedgerMapper;
        this.locationMapper = locationMapper;
        this.inventoryMapper = inventoryMapper;
        this.materialMapper = materialMapper;
    }

    public LocationAreaPostingService(LocationAreaLedgerMapper locationAreaLedgerMapper,
                                      LocationMapper locationMapper) {
        this(locationAreaLedgerMapper, locationMapper, null, null);
    }

    public void recordSkipped(SkipCommand command) {
        LocationAreaLedger ledger = new LocationAreaLedger();
        ledger.setBizType(command.bizType());
        ledger.setBizAction(command.bizAction());
        ledger.setBizOrderId(command.bizOrderId());
        ledger.setBizItemId(command.bizItemId());
        ledger.setWarehouseId(command.warehouseId());
        ledger.setLocationId(command.locationId());
        ledger.setMaterialId(command.materialId());
        ledger.setEffectiveQuantity(null);
        ledger.setAreaCoefficientSnapshot(null);
        ledger.setAreaDelta(null);
        ledger.setValidationMode("skipped");
        ledger.setDirection("none");
        ledger.setSkipReason(command.skipReason());
        ledger.setOrgId(command.orgId());
        ledger.setTenantId(command.tenantId());
        locationAreaLedgerMapper.insert(ledger);
    }

    public void postDelta(PostCommand command) {
        LocationAreaLedger ledger = new LocationAreaLedger();
        ledger.setBizType(command.bizType());
        ledger.setBizAction(command.bizAction());
        ledger.setBizOrderId(command.bizOrderId());
        ledger.setBizItemId(command.bizItemId());
        ledger.setWarehouseId(command.warehouseId());
        ledger.setLocationId(command.locationId());
        ledger.setMaterialId(command.materialId());
        ledger.setEffectiveQuantity(command.effectiveQuantity());
        ledger.setAreaCoefficientSnapshot(command.areaCoefficientSnapshot());
        ledger.setAreaDelta(command.areaDelta());
        ledger.setDirection(command.direction());
        ledger.setValidationMode(command.validationMode());
        ledger.setOrgId(command.orgId());
        ledger.setTenantId(command.tenantId());
        locationAreaLedgerMapper.insert(ledger);
        int updatedRows = locationMapper.incrementUsedCapacity(command.locationId(), command.areaDelta());
        if (updatedRows != 1) {
            throw new IllegalStateException("Failed to update location used capacity for area ledger posting");
        }
    }

    public BaselinePreviewResult previewBaseline(BaselineScope scope) {
        if (inventoryMapper == null || materialMapper == null) {
            throw new IllegalStateException("Baseline preview dependencies are not configured");
        }
        List<Map<String, Object>> rows = inventoryMapper.selectAreaBaselineSeeds(
                scope.warehouseIds(),
                scope.locationIds(),
                scope.orgId(),
                scope.tenantId()
        );
        return aggregateBaseline(rows);
    }

    public void replaceBaselineForLocations(BaselineReplacementBatch batch) {
        if (locationAreaLedgerMapper.deleteByBizActionAndLocations(batch.bizAction(), batch.locationIds()) < 0) {
            throw new IllegalStateException("Failed to delete existing baseline area ledgers");
        }
        for (LocationAreaLedger entry : batch.entries()) {
            locationAreaLedgerMapper.insert(entry);
        }
        for (LocationUsedCapacityTotal total : batch.locationTotals()) {
            if (locationMapper.replaceUsedCapacity(total.locationId(), total.usedCapacity()) != 1) {
                throw new IllegalStateException("Failed to replace location used capacity for baseline area recalculation");
            }
        }
    }

    public void postInboundApprovedArea(InboundOrder order, List<LocationAreaValidationService.PostingEntry> entries) {
        for (LocationAreaValidationService.PostingEntry entry : entries) {
            if ("skipped".equals(entry.validationMode())) {
                recordSkipped(new SkipCommand(
                        "inbound",
                        "approve",
                        order.getId(),
                        entry.itemId(),
                        entry.warehouseId(),
                        entry.locationId(),
                        entry.materialId(),
                        entry.skipReason(),
                        order.getOrgId(),
                        order.getTenantId()
                ));
                continue;
            }
            postDelta(new PostCommand(
                    "inbound",
                    "approve",
                    order.getId(),
                    entry.itemId(),
                    entry.warehouseId(),
                    entry.locationId(),
                    entry.materialId(),
                    entry.effectiveQuantity(),
                    entry.areaCoefficientSnapshot(),
                    entry.areaDelta(),
                    "increase",
                    entry.validationMode(),
                    order.getOrgId(),
                    order.getTenantId()
            ));
        }
    }

    public void postOutboundExecutedArea(OutboundOrder order,
                                         List<OutboundOrderItem> items,
                                         LocationAreaValidationService validationService) {
        LocationAreaValidationService.AreaValidationResult validation = validationService.validateOutboundOrderArea(order.getWarehouseId(), items);
        for (LocationAreaValidationService.PostingEntry entry : validation.postingEntries()) {
            if ("skipped".equals(entry.validationMode())) {
                recordSkipped(new SkipCommand(
                        "outbound",
                        "execute",
                        order.getId(),
                        entry.itemId(),
                        entry.warehouseId(),
                        entry.locationId(),
                        entry.materialId(),
                        entry.skipReason(),
                        order.getOrgId(),
                        order.getTenantId()
                ));
                continue;
            }
            postDelta(new PostCommand(
                    "outbound",
                    "execute",
                    order.getId(),
                    entry.itemId(),
                    entry.warehouseId(),
                    entry.locationId(),
                    entry.materialId(),
                    entry.effectiveQuantity(),
                    entry.areaCoefficientSnapshot(),
                    entry.areaDelta().negate(),
                    "decrease",
                    entry.validationMode(),
                    order.getOrgId(),
                    order.getTenantId()
            ));
        }
    }

    public void postStocktakeApprovedArea(StocktakeOrder order,
                                          List<StocktakeOrderItem> items,
                                          LocationAreaValidationService validationService) {
        LocationAreaValidationService.AreaValidationResult validation = validationService.validateStocktakeOrderArea(order.getWarehouseId(), items);
        for (LocationAreaValidationService.PostingEntry entry : validation.postingEntries()) {
            StocktakeOrderItem item = items.stream()
                    .filter(candidate -> java.util.Objects.equals(candidate.getId(), entry.itemId()))
                    .findFirst()
                    .orElse(null);
            if (item == null) {
                continue;
            }
            BigDecimal diffQty = item.getDiffQty() == null ? BigDecimal.ZERO : item.getDiffQty();
            if (diffQty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            if ("skipped".equals(entry.validationMode())) {
                recordSkipped(new SkipCommand(
                        "stocktake",
                        "approve",
                        order.getId(),
                        entry.itemId(),
                        entry.warehouseId(),
                        entry.locationId(),
                        entry.materialId(),
                        entry.skipReason(),
                        order.getOrgId(),
                        order.getTenantId()
                ));
                continue;
            }
            BigDecimal signedAreaDelta = diffQty.compareTo(BigDecimal.ZERO) > 0 ? entry.areaDelta() : entry.areaDelta().negate();
            String direction = diffQty.compareTo(BigDecimal.ZERO) > 0 ? "increase" : "decrease";
            postDelta(new PostCommand(
                    "stocktake",
                    "approve",
                    order.getId(),
                    entry.itemId(),
                    entry.warehouseId(),
                    entry.locationId(),
                    entry.materialId(),
                    diffQty.abs(),
                    entry.areaCoefficientSnapshot(),
                    signedAreaDelta,
                    direction,
                    entry.validationMode(),
                    order.getOrgId(),
                    order.getTenantId()
            ));
        }
    }

    public void reverseInboundApprovedArea(InboundOrder order, List<InboundOrderItem> items) {
        for (InboundOrderItem item : items) {
            LocationAreaLedger originalLedger = locationAreaLedgerMapper.selectLatestByBizItemForUpdate(
                    "inbound",
                    "approve",
                    order.getId(),
                    item.getId()
            );
            if (originalLedger == null || originalLedger.getReversedLedgerId() != null) {
                throw new IllegalStateException("Missing reversible inbound approve area ledger");
            }
            if ("skipped".equals(originalLedger.getValidationMode())) {
                continue;
            }
            BigDecimal reverseDelta = originalLedger.getAreaDelta() == null ? null : originalLedger.getAreaDelta().negate();
            PostCommand reverseCommand = new PostCommand(
                    "inbound",
                    "reverse",
                    order.getId(),
                    item.getId(),
                    originalLedger.getWarehouseId(),
                    originalLedger.getLocationId(),
                    originalLedger.getMaterialId(),
                    originalLedger.getEffectiveQuantity(),
                    originalLedger.getAreaCoefficientSnapshot(),
                    reverseDelta,
                    "decrease",
                    originalLedger.getValidationMode(),
                    order.getOrgId(),
                    order.getTenantId()
            );
            LocationAreaLedger reversedLedger = buildLedger(reverseCommand);
            int insertedRows = locationAreaLedgerMapper.insert(reversedLedger);
            if (reversedLedger.getId() == null && insertedRows > 1) {
                reversedLedger.setId((long) insertedRows);
            }
            int updatedRows = locationMapper.incrementUsedCapacity(reversedLedger.getLocationId(), reversedLedger.getAreaDelta());
            if (updatedRows != 1) {
                throw new IllegalStateException("Failed to update location used capacity for area ledger posting");
            }
            if (locationAreaLedgerMapper.markReversed(originalLedger.getId(), reversedLedger.getId()) != 1) {
                throw new IllegalStateException("Failed to mark location area ledger as reversed");
            }
        }
    }

    public void reverseOutboundExecutedArea(OutboundOrder order, List<OutboundOrderItem> items) {
        for (OutboundOrderItem item : items) {
            LocationAreaLedger originalLedger = locationAreaLedgerMapper.selectLatestByBizItemForUpdate(
                    "outbound",
                    "execute",
                    order.getId(),
                    item.getId()
            );
            if (originalLedger == null || originalLedger.getReversedLedgerId() != null) {
                throw new IllegalStateException("Missing reversible outbound execute area ledger");
            }
            if ("skipped".equals(originalLedger.getValidationMode())) {
                continue;
            }
            BigDecimal reverseDelta = originalLedger.getAreaDelta() == null ? null : originalLedger.getAreaDelta().negate();
            PostCommand reverseCommand = new PostCommand(
                    "outbound",
                    "reverse",
                    order.getId(),
                    item.getId(),
                    originalLedger.getWarehouseId(),
                    originalLedger.getLocationId(),
                    originalLedger.getMaterialId(),
                    originalLedger.getEffectiveQuantity(),
                    originalLedger.getAreaCoefficientSnapshot(),
                    reverseDelta,
                    "increase",
                    originalLedger.getValidationMode(),
                    order.getOrgId(),
                    order.getTenantId()
            );
            LocationAreaLedger reversedLedger = buildLedger(reverseCommand);
            int insertedRows = locationAreaLedgerMapper.insert(reversedLedger);
            if (reversedLedger.getId() == null && insertedRows > 1) {
                reversedLedger.setId((long) insertedRows);
            }
            int updatedRows = locationMapper.incrementUsedCapacity(reversedLedger.getLocationId(), reversedLedger.getAreaDelta());
            if (updatedRows != 1) {
                throw new IllegalStateException("Failed to update location used capacity for area ledger posting");
            }
            if (locationAreaLedgerMapper.markReversed(originalLedger.getId(), reversedLedger.getId()) != 1) {
                throw new IllegalStateException("Failed to mark location area ledger as reversed");
            }
        }
    }

    private BaselinePreviewResult aggregateBaseline(List<Map<String, Object>> rows) {
        List<Long> materialIds = rows.stream()
                .map(row -> toLong(row.get("materialId")))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<Long> locationIds = rows.stream()
                .map(row -> toLong(row.get("locationId")))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, BigDecimal> coefficientMap = new LinkedHashMap<>();
        if (!materialIds.isEmpty()) {
            for (MaterialMapper.AreaValidationMaterial material : materialMapper.selectAreaValidationMaterials(materialIds)) {
                coefficientMap.put(material.getMaterialId(), material.getAreaCoefficient());
            }
        }

        Map<Long, Location> locationMap = new LinkedHashMap<>();
        if (!locationIds.isEmpty()) {
            for (Location location : locationMapper.selectAreaValidationLocations(locationIds)) {
                locationMap.put(location.getId(), location);
            }
        }

        List<BaselineEntry> entries = new ArrayList<>();
        List<BaselineEntry> skippedEntries = new ArrayList<>();
        BigDecimal totalArea = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (Map<String, Object> row : rows) {
            Long warehouseId = toLong(row.get("warehouseId"));
            Long locationId = toLong(row.get("locationId"));
            Long materialId = toLong(row.get("materialId"));
            BigDecimal quantity = scaled(toBigDecimal(row.get("quantity")));
            Long orgId = toLong(row.get("orgId"));
            Long tenantId = toLong(row.get("tenantId"));
            Location location = locationMap.get(locationId);
            BigDecimal coefficient = coefficientMap.get(materialId);
            if (location != null && !isSquareMeter(location.getCapacityUnit())) {
                BaselineEntry skipped = new BaselineEntry(warehouseId, locationId, materialId, quantity, null, null, "skipped", "NON_SQUARE_METER_CAPACITY_UNIT", orgId, tenantId);
                entries.add(skipped);
                skippedEntries.add(skipped);
                continue;
            }
            if (coefficient == null) {
                BaselineEntry skipped = new BaselineEntry(warehouseId, locationId, materialId, quantity, null, null, "skipped", "MISSING_AREA_COEFFICIENT", orgId, tenantId);
                entries.add(skipped);
                skippedEntries.add(skipped);
                continue;
            }
            BigDecimal areaDelta = scaled(quantity.multiply(coefficient));
            BaselineEntry entry = new BaselineEntry(warehouseId, locationId, materialId, quantity, scaled(coefficient), areaDelta, "calculated", null, orgId, tenantId);
            entries.add(entry);
            totalArea = totalArea.add(areaDelta);
        }

        return new BaselinePreviewResult(scaled(totalArea), entries, skippedEntries);
    }

    private boolean isSquareMeter(String capacityUnit) {
        return capacityUnit == null || capacityUnit.isBlank() || "㎡".equals(capacityUnit);
    }

    private BigDecimal scaled(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }

    private Long toLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value == null) {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    private LocationAreaLedger buildLedger(PostCommand command) {
        LocationAreaLedger ledger = new LocationAreaLedger();
        ledger.setBizType(command.bizType());
        ledger.setBizAction(command.bizAction());
        ledger.setBizOrderId(command.bizOrderId());
        ledger.setBizItemId(command.bizItemId());
        ledger.setWarehouseId(command.warehouseId());
        ledger.setLocationId(command.locationId());
        ledger.setMaterialId(command.materialId());
        ledger.setEffectiveQuantity(command.effectiveQuantity());
        ledger.setAreaCoefficientSnapshot(command.areaCoefficientSnapshot());
        ledger.setAreaDelta(command.areaDelta());
        ledger.setDirection(command.direction());
        ledger.setValidationMode(command.validationMode());
        ledger.setOrgId(command.orgId());
        ledger.setTenantId(command.tenantId());
        return ledger;
    }

    public record BaselineScope(
            List<Long> warehouseIds,
            List<Long> locationIds,
            Long orgId,
            Long tenantId
    ) {
    }

    public record BaselinePreviewResult(
            BigDecimal totalArea,
            List<BaselineEntry> entries,
            List<BaselineEntry> skippedEntries
    ) {
    }

    public record BaselineEntry(
            Long warehouseId,
            Long locationId,
            Long materialId,
            BigDecimal quantity,
            BigDecimal areaCoefficient,
            BigDecimal areaDelta,
            String validationMode,
            String skipReason,
            Long orgId,
            Long tenantId
    ) {
    }

    public record BaselineReplacementBatch(
            String bizAction,
            List<Long> locationIds,
            List<LocationAreaLedger> entries,
            List<LocationUsedCapacityTotal> locationTotals
    ) {
    }

    public record LocationUsedCapacityTotal(
            Long locationId,
            BigDecimal usedCapacity
    ) {
    }

    public record SkipCommand(
            String bizType,
            String bizAction,
            Long bizOrderId,
            Long bizItemId,
            Long warehouseId,
            Long locationId,
            Long materialId,
            String skipReason,
            Long orgId,
            Long tenantId
    ) {
    }

    public record PostCommand(
            String bizType,
            String bizAction,
            Long bizOrderId,
            Long bizItemId,
            Long warehouseId,
            Long locationId,
            Long materialId,
            BigDecimal effectiveQuantity,
            BigDecimal areaCoefficientSnapshot,
            BigDecimal areaDelta,
            String direction,
            String validationMode,
            Long orgId,
            Long tenantId
    ) {
    }
}
