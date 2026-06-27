package com.xykj.wms.service.support;

import com.xykj.common.exception.BizException;
import com.xykj.wms.dto.InboundAreaValidationPreviewDTO;
import com.xykj.wms.entity.InboundOrderItem;
import com.xykj.wms.entity.Location;
import com.xykj.wms.entity.StocktakeOrderItem;
import com.xykj.wms.mapper.LocationMapper;
import com.xykj.wms.mapper.MaterialMapper;
import com.xykj.wms.vo.InboundAreaValidationPreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LocationAreaValidationService {

    private static final String EXCEEDED_MESSAGE = "当前选择仓位空间容量不足，本次入库后将超出仓位最大面积上限，请减少入库数量或更换其他仓位";
    private static final String SKIPPED_MESSAGE = "当前物料未配置物料类别统一面积系数，本次暂不执行仓位面积校验，可继续提交入库；请尽快在系统管理-字典分类维护中补齐配置。";
    private static final String NON_SQUARE_METER_MESSAGE = "当前仓位容量单位不是㎡，暂不支持面积增量校验，请先修正仓位容量单位后再操作";
    private static final String SKIP_REASON_MISSING_AREA_COEFFICIENT = "MISSING_AREA_COEFFICIENT";
    private static final String SKIP_REASON_NON_SQUARE_METER_CAPACITY_UNIT = "NON_SQUARE_METER_CAPACITY_UNIT";

    private final MaterialMapper materialMapper;
    private final LocationMapper locationMapper;

    public InboundAreaValidationPreviewVO previewInbound(InboundAreaValidationPreviewDTO dto) {
        List<ValidationCandidate> candidates = (dto.getItems() == null ? List.<InboundAreaValidationPreviewDTO.Item>of() : dto.getItems()).stream()
                .map(item -> new ValidationCandidate(
                        null,
                        item.getLineKey(),
                        item.getWarehouseId(),
                        item.getLocationId(),
                        item.getMaterialId(),
                        item.getQuantity()
                ))
                .toList();
        return toPreviewVO(validateInboundCandidates(dto.getWarehouseId(), candidates));
    }

    public AreaValidationResult validateInboundOrderArea(Long headerWarehouseId, List<InboundOrderItem> items) {
        List<ValidationCandidate> candidates = (items == null ? List.<InboundOrderItem>of() : items).stream()
                .map(item -> new ValidationCandidate(
                        item.getId(),
                        null,
                        item.getWarehouseId(),
                        item.getLocationId(),
                        item.getMaterialId(),
                        item.getQuantity()
                ))
                .toList();
        return validateInboundCandidates(headerWarehouseId, candidates);
    }

    public AreaValidationResult validateStocktakeOrderArea(Long headerWarehouseId, List<StocktakeOrderItem> items) {
        List<ValidationCandidate> candidates = (items == null ? List.<StocktakeOrderItem>of() : items).stream()
                .filter(item -> defaultZero(item.getDiffQty()).compareTo(BigDecimal.ZERO) != 0)
                .map(item -> new ValidationCandidate(
                        item.getId(),
                        null,
                        item.getWarehouseId(),
                        item.getLocationId(),
                        item.getMaterialId(),
                        item.getDiffQty().abs()
                ))
                .toList();
        return validateInboundCandidates(headerWarehouseId, candidates);
    }

    public AreaValidationResult validateOutboundOrderArea(Long headerWarehouseId, List<com.xykj.wms.entity.OutboundOrderItem> items) {
        List<ValidationCandidate> candidates = (items == null ? List.<com.xykj.wms.entity.OutboundOrderItem>of() : items).stream()
                .map(item -> new ValidationCandidate(
                        item.getId(),
                        null,
                        item.getWarehouseId(),
                        item.getLocationId(),
                        item.getMaterialId(),
                        item.getQuantity()
                ))
                .toList();
        return validateInboundCandidates(headerWarehouseId, candidates);
    }

    private AreaValidationResult validateInboundCandidates(Long headerWarehouseId, List<ValidationCandidate> items) {
        List<Long> materialIds = items.stream().map(ValidationCandidate::materialId).filter(Objects::nonNull).distinct().toList();
        List<Long> locationIds = items.stream().map(ValidationCandidate::locationId).filter(Objects::nonNull).distinct().toList();

        Map<Long, MaterialMapper.AreaValidationMaterial> materialMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            for (MaterialMapper.AreaValidationMaterial material : materialMapper.selectAreaValidationMaterials(materialIds)) {
                materialMap.put(material.getMaterialId(), material);
            }
        }

        Map<Long, Location> locationMap = new HashMap<>();
        if (!locationIds.isEmpty()) {
            for (Location location : locationMapper.selectAreaValidationLocations(locationIds)) {
                locationMap.put(location.getId(), location);
            }
        }

        Map<String, LocationAccumulator> summaryMap = new LinkedHashMap<>();
        List<ValidatedItem> validatedItems = new ArrayList<>();
        boolean hasSkipped = false;

        for (ValidationCandidate item : items) {
            Long locationId = item.locationId();
            Location location = locationMap.get(locationId);
            MaterialMapper.AreaValidationMaterial material = materialMap.get(item.materialId());
            Long warehouseId = resolveWarehouseId(headerWarehouseId, item.warehouseId(), location);
            BigDecimal currentOccupiedArea = scaled(location != null ? defaultZero(location.getUsedCapacity()) : BigDecimal.ZERO);
            BigDecimal locationCapacity = scaled(location != null ? defaultZero(location.getCapacity()) : BigDecimal.ZERO);

            LocationAccumulator accumulator = summaryMap.computeIfAbsent(
                    summaryKey(warehouseId, locationId),
                    key -> new LocationAccumulator(warehouseId, locationId, location != null ? location.getLocationName() : null,
                            currentOccupiedArea, locationCapacity)
            );

            if (location != null && !isSquareMeter(location.getCapacityUnit())) {
                accumulator.hasSkippedItems = true;
                accumulator.message = NON_SQUARE_METER_MESSAGE;
                validatedItems.add(new ValidatedItem(
                        item.itemId(),
                        item.lineKey(),
                        warehouseId,
                        locationId,
                        item.materialId(),
                        null,
                        null,
                        currentOccupiedArea,
                        null,
                        locationCapacity,
                        "skipped",
                        NON_SQUARE_METER_MESSAGE,
                        SKIP_REASON_NON_SQUARE_METER_CAPACITY_UNIT
                ));
                hasSkipped = true;
                continue;
            }

            BigDecimal areaCoefficient = material != null ? material.getAreaCoefficient() : null;
            if (areaCoefficient == null) {
                accumulator.hasSkippedItems = true;
                accumulator.message = SKIPPED_MESSAGE;
                validatedItems.add(new ValidatedItem(
                        item.itemId(),
                        item.lineKey(),
                        warehouseId,
                        locationId,
                        item.materialId(),
                        null,
                        null,
                        currentOccupiedArea,
                        null,
                        locationCapacity,
                        "skipped",
                        SKIPPED_MESSAGE,
                        SKIP_REASON_MISSING_AREA_COEFFICIENT
                ));
                hasSkipped = true;
                continue;
            }

            BigDecimal expectedOccupiedArea = scaled(defaultZero(item.quantity()).multiply(areaCoefficient));
            accumulator.expectedIncrementArea = accumulator.expectedIncrementArea.add(expectedOccupiedArea);
            validatedItems.add(new ValidatedItem(
                    item.itemId(),
                    item.lineKey(),
                    warehouseId,
                    locationId,
                    item.materialId(),
                    scaled(areaCoefficient),
                    expectedOccupiedArea,
                    currentOccupiedArea,
                    null,
                    locationCapacity,
                    null,
                    null,
                    null
            ));
        }

        boolean hasExceeded = false;
        List<LocationValidationSummary> locationSummaries = new ArrayList<>();
        for (LocationAccumulator accumulator : summaryMap.values()) {
            BigDecimal projectedOccupiedArea = scaled(accumulator.currentOccupiedArea.add(accumulator.expectedIncrementArea));
            String validationResult = projectedOccupiedArea.compareTo(accumulator.locationCapacity) > 0 ? "exceeded" : "passed";
            if ("exceeded".equals(validationResult)) {
                hasExceeded = true;
                accumulator.message = EXCEEDED_MESSAGE;
            }
            locationSummaries.add(new LocationValidationSummary(
                    accumulator.warehouseId,
                    accumulator.locationId,
                    accumulator.locationName,
                    accumulator.currentOccupiedArea,
                    scaled(accumulator.expectedIncrementArea),
                    projectedOccupiedArea,
                    accumulator.locationCapacity,
                    validationResult,
                    accumulator.hasSkippedItems,
                    accumulator.message
            ));
        }

        List<ValidatedItem> finalizedItems = new ArrayList<>(validatedItems.size());
        for (ValidatedItem item : validatedItems) {
            if ("skipped".equals(item.validationResult())) {
                finalizedItems.add(item);
                continue;
            }
            LocationAccumulator accumulator = summaryMap.get(summaryKey(item.warehouseId(), item.locationId()));
            BigDecimal projectedOccupiedArea = scaled(accumulator.currentOccupiedArea.add(accumulator.expectedIncrementArea));
            String validationResult = projectedOccupiedArea.compareTo(accumulator.locationCapacity) > 0 ? "exceeded" : "passed";
            finalizedItems.add(new ValidatedItem(
                    item.itemId(),
                    item.lineKey(),
                    item.warehouseId(),
                    item.locationId(),
                    item.materialId(),
                    item.areaCoefficient(),
                    item.expectedOccupiedArea(),
                    item.currentOccupiedArea(),
                    projectedOccupiedArea,
                    item.locationCapacity(),
                    validationResult,
                    "exceeded".equals(validationResult) ? EXCEEDED_MESSAGE : null,
                    null
            ));
        }

        String globalMessage = null;
        if (hasExceeded) {
            globalMessage = EXCEEDED_MESSAGE;
        } else if (hasSkipped) {
            globalMessage = SKIPPED_MESSAGE;
        }
        return new AreaValidationResult(finalizedItems, locationSummaries, hasExceeded, hasSkipped, globalMessage);
    }

    private InboundAreaValidationPreviewVO toPreviewVO(AreaValidationResult validation) {
        InboundAreaValidationPreviewVO result = new InboundAreaValidationPreviewVO();
        List<InboundAreaValidationPreviewVO.ItemResult> itemResults = new ArrayList<>();
        for (ValidatedItem item : validation.itemResults()) {
            InboundAreaValidationPreviewVO.ItemResult itemResult = new InboundAreaValidationPreviewVO.ItemResult();
            itemResult.setLineKey(item.lineKey());
            itemResult.setWarehouseId(item.warehouseId());
            itemResult.setLocationId(item.locationId());
            itemResult.setAreaCoefficient(item.areaCoefficient());
            itemResult.setExpectedOccupiedArea(item.expectedOccupiedArea());
            itemResult.setCurrentOccupiedArea(item.currentOccupiedArea());
            itemResult.setProjectedOccupiedArea(item.projectedOccupiedArea());
            itemResult.setLocationCapacity(item.locationCapacity());
            itemResult.setValidationResult(item.validationResult());
            itemResult.setMessage(item.message());
            itemResults.add(itemResult);
        }

        List<InboundAreaValidationPreviewVO.LocationSummary> locationResults = new ArrayList<>();
        for (LocationValidationSummary summary : validation.locationSummaries()) {
            InboundAreaValidationPreviewVO.LocationSummary locationSummary = new InboundAreaValidationPreviewVO.LocationSummary();
            locationSummary.setWarehouseId(summary.warehouseId());
            locationSummary.setLocationId(summary.locationId());
            locationSummary.setLocationName(summary.locationName());
            locationSummary.setCurrentOccupiedArea(summary.currentOccupiedArea());
            locationSummary.setExpectedIncrementArea(summary.expectedIncrementArea());
            locationSummary.setProjectedOccupiedArea(summary.projectedOccupiedArea());
            locationSummary.setLocationCapacity(summary.locationCapacity());
            locationSummary.setValidationResult(summary.validationResult());
            locationSummary.setHasSkippedItems(summary.hasSkippedItems());
            locationSummary.setMessage(summary.message());
            locationResults.add(locationSummary);
        }

        result.setItemResults(itemResults);
        result.setLocationSummaries(locationResults);
        result.setHasExceeded(validation.hasExceeded());
        result.setHasSkipped(validation.hasSkipped());
        result.setGlobalMessage(validation.globalMessage());
        return result;
    }

    private Long resolveWarehouseId(Long headerWarehouseId, Long itemWarehouseId, Location location) {
        if (itemWarehouseId != null) {
            return itemWarehouseId;
        }
        if (location != null && location.getWarehouseId() != null) {
            return location.getWarehouseId();
        }
        return headerWarehouseId;
    }

    private boolean isSquareMeter(String capacityUnit) {
        return capacityUnit == null || capacityUnit.isBlank() || "㎡".equals(capacityUnit);
    }

    private String summaryKey(Long warehouseId, Long locationId) {
        return warehouseId + "_" + locationId;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scaled(BigDecimal value) {
        return defaultZero(value).setScale(4, RoundingMode.HALF_UP);
    }

    public record AreaValidationResult(
            List<ValidatedItem> itemResults,
            List<LocationValidationSummary> locationSummaries,
            boolean hasExceeded,
            boolean hasSkipped,
            String globalMessage
    ) {
        public void throwIfExceeded() {
            if (hasExceeded) {
                throw BizException.validationFailed(EXCEEDED_MESSAGE);
            }
        }

        public List<PostingEntry> postingEntries() {
            return itemResults.stream()
                    .map(item -> {
                        if ("skipped".equals(item.validationResult())) {
                            return new PostingEntry(
                                    item.itemId(),
                                    item.warehouseId(),
                                    item.locationId(),
                                    item.materialId(),
                                    null,
                                    null,
                                    null,
                                    "skipped",
                                    item.skipReason()
                            );
                        }
                        return new PostingEntry(
                                item.itemId(),
                                item.warehouseId(),
                                item.locationId(),
                                item.materialId(),
                                item.expectedOccupiedArea() == null || item.areaCoefficient() == null || item.areaCoefficient().compareTo(BigDecimal.ZERO) == 0
                                        ? null
                                        : item.expectedOccupiedArea().divide(item.areaCoefficient(), 4, RoundingMode.HALF_UP),
                                item.areaCoefficient(),
                                item.expectedOccupiedArea(),
                                "calculated",
                                null
                        );
                    })
                    .toList();
        }
    }

    public record PostingEntry(
            Long itemId,
            Long warehouseId,
            Long locationId,
            Long materialId,
            BigDecimal effectiveQuantity,
            BigDecimal areaCoefficientSnapshot,
            BigDecimal areaDelta,
            String validationMode,
            String skipReason
    ) {
    }

    private record ValidationCandidate(
            Long itemId,
            String lineKey,
            Long warehouseId,
            Long locationId,
            Long materialId,
            BigDecimal quantity
    ) {
    }

    public record ValidatedItem(
            Long itemId,
            String lineKey,
            Long warehouseId,
            Long locationId,
            Long materialId,
            BigDecimal areaCoefficient,
            BigDecimal expectedOccupiedArea,
            BigDecimal currentOccupiedArea,
            BigDecimal projectedOccupiedArea,
            BigDecimal locationCapacity,
            String validationResult,
            String message,
            String skipReason
    ) {
    }

    public record LocationValidationSummary(
            Long warehouseId,
            Long locationId,
            String locationName,
            BigDecimal currentOccupiedArea,
            BigDecimal expectedIncrementArea,
            BigDecimal projectedOccupiedArea,
            BigDecimal locationCapacity,
            String validationResult,
            boolean hasSkippedItems,
            String message
    ) {
    }

    private static final class LocationAccumulator {
        private final Long warehouseId;
        private final Long locationId;
        private final String locationName;
        private final BigDecimal currentOccupiedArea;
        private final BigDecimal locationCapacity;
        private BigDecimal expectedIncrementArea = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        private boolean hasSkippedItems;
        private String message;

        private LocationAccumulator(Long warehouseId,
                                    Long locationId,
                                    String locationName,
                                    BigDecimal currentOccupiedArea,
                                    BigDecimal locationCapacity) {
            this.warehouseId = warehouseId;
            this.locationId = locationId;
            this.locationName = locationName;
            this.currentOccupiedArea = currentOccupiedArea;
            this.locationCapacity = locationCapacity;
        }
    }
}
