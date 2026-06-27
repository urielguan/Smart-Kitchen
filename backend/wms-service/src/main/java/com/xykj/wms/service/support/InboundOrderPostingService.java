package com.xykj.wms.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xykj.common.exception.BizException;
import com.xykj.wms.entity.InboundOrder;
import com.xykj.wms.entity.InboundOrderItem;
import com.xykj.wms.entity.Inventory;
import com.xykj.wms.entity.Material;
import com.xykj.wms.mapper.InboundOrderMapper;
import com.xykj.wms.mapper.InventoryMapper;
import com.xykj.wms.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InboundOrderPostingService {

    private static final String DEFAULT_INVENTORY_STATUS = "normal";
    private static final String DEFAULT_BATCH_NO = "默认批次";

    private final InboundOrderMapper inboundOrderMapper;
    private final InventoryMapper inventoryMapper;
    private final MaterialMapper materialMapper;
    private final LocationAreaPostingService locationAreaPostingService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void executeApprovedPosting(InboundOrder order,
                                       List<InboundOrderItem> items,
                                       LocationAreaValidationService.AreaValidationResult validation) {
        syncInventory(order, items);
        locationAreaPostingService.postInboundApprovedArea(order, validation.postingEntries());
        writebackPurchaseOrderInboundQty(order, items);
    }

    private void syncInventory(InboundOrder order, List<InboundOrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw BizException.validationFailed("入库单明细不能为空");
        }
        Map<Long, Material> materialMap = loadMaterials(items);
        for (InboundOrderItem item : items) {
            if (hasInventoryRecord(order, item)) {
                continue;
            }
            Inventory inventory = new Inventory();
            inventory.setWarehouseId(item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId());
            inventory.setLocationId(item.getLocationId());
            inventory.setMaterialId(item.getMaterialId());
            inventory.setMaterialName(item.getMaterialName());
            inventory.setSpec(blankToNull(item.getSpec()));
            inventory.setBatchNo(blankToNull(item.getBatchNo()) == null ? DEFAULT_BATCH_NO : item.getBatchNo());
            inventory.setTraceBatchId(blankToNull(item.getTraceBatchId()));
            inventory.setQuantity(defaultZero(item.getQuantity()));
            inventory.setUnit(item.getUnit());
            inventory.setUnitCost(item.getUnitCost());
            inventory.setTotalCost(defaultZero(item.getQuantity()).multiply(defaultZero(item.getUnitCost())));
            inventory.setProductionDate(item.getProductionDate());
            inventory.setExpiryDate(resolveInventoryExpiryDate(item, materialMap));
            inventory.setStatus(DEFAULT_INVENTORY_STATUS);
            inventory.setSourceType(order.getSourceType() == null || order.getSourceType().isBlank() ? "purchase" : order.getSourceType());
            inventory.setSourceId(order.getId());
            inventory.setOrgId(order.getOrgId() != null ? order.getOrgId() : 1L);
            inventory.setTenantId(order.getTenantId() != null ? order.getTenantId() : 1L);
            inventory.setCreatedBy(order.getApprovedBy() != null ? order.getApprovedBy() : order.getSubmittedBy());
            inventoryMapper.insert(inventory);
        }
    }

    private Map<Long, Material> loadMaterials(List<InboundOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        List<Long> materialIds = items.stream()
                .map(InboundOrderItem::getMaterialId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (materialIds.isEmpty()) {
            return Map.of();
        }
        return materialMapper.selectBatchIds(materialIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Material::getId, material -> material, (left, right) -> left));
    }

    private LocalDate resolveInventoryExpiryDate(InboundOrderItem item, Map<Long, Material> materialMap) {
        if (item == null) {
            return null;
        }
        if (item.getExpiryDate() != null) {
            return item.getExpiryDate();
        }
        if (item.getProductionDate() == null || item.getMaterialId() == null || materialMap == null || materialMap.isEmpty()) {
            return null;
        }
        Material material = materialMap.get(item.getMaterialId());
        Integer shelfLifeDays = material != null ? material.getShelfLifeDays() : null;
        if (shelfLifeDays == null || shelfLifeDays <= 0) {
            return null;
        }
        return item.getProductionDate().plusDays(shelfLifeDays.longValue());
    }

    private void writebackPurchaseOrderInboundQty(InboundOrder inboundOrder, List<InboundOrderItem> inboundItems) {
        if (!"purchase".equals(inboundOrder.getSourceType()) || inboundOrder.getSourceOrderId() == null) {
            return;
        }
        Long purchaseOrderId = inboundOrder.getSourceOrderId();
        for (InboundOrderItem inboundItem : inboundItems) {
            if (inboundItem.getMaterialId() == null || inboundItem.getQuantity() == null) {
                continue;
            }
            BigDecimal inboundQty = inboundItem.getQuantity();
            jdbcTemplate.update(
                    "UPDATE scm_purchase_order_item SET inbound_qty = COALESCE(inbound_qty, 0) + ?, " +
                            "remaining_inbound_qty = GREATEST(order_qty - (COALESCE(inbound_qty, 0) + ?), 0) " +
                            "WHERE order_id = ? AND material_id = ?",
                    inboundQty, inboundQty, purchaseOrderId, inboundItem.getMaterialId()
            );
        }

        Integer remainingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_order_item " +
                        "WHERE order_id = ? " +
                        "AND (inbound_qty IS NULL OR inbound_qty < order_qty)",
                Integer.class, purchaseOrderId
        );
        if (remainingCount != null && remainingCount == 0) {
            jdbcTemplate.update(
                    "UPDATE scm_purchase_order SET status = 'completed' WHERE id = ? AND deleted = 0",
                    purchaseOrderId
            );
        }
    }

    private boolean hasInventoryRecord(InboundOrder order, InboundOrderItem item) {
        String sourceType = order.getSourceType() == null || order.getSourceType().isBlank() ? "purchase" : order.getSourceType();
        String spec = blankToNull(item.getSpec());
        String batchNo = blankToNull(item.getBatchNo()) == null ? DEFAULT_BATCH_NO : item.getBatchNo();
        String traceBatchId = blankToNull(item.getTraceBatchId());
        return inventoryMapper.selectCount(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getSourceType, sourceType)
                .eq(Inventory::getSourceId, order.getId())
                .eq(Inventory::getWarehouseId, item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId())
                .eq(item.getLocationId() != null, Inventory::getLocationId, item.getLocationId())
                .isNull(item.getLocationId() == null, Inventory::getLocationId)
                .eq(Inventory::getMaterialId, item.getMaterialId())
                .eq(Inventory::getOrgId, order.getOrgId() != null ? order.getOrgId() : 1L)
                .eq(Inventory::getTenantId, order.getTenantId() != null ? order.getTenantId() : 1L)
                .and(spec == null, q -> q.isNull(Inventory::getSpec).or().eq(Inventory::getSpec, ""))
                .eq(spec != null, Inventory::getSpec, spec)
                .and(batchNo == null, q -> q.isNull(Inventory::getBatchNo).or().eq(Inventory::getBatchNo, ""))
                .eq(batchNo != null, Inventory::getBatchNo, batchNo)
                .and(traceBatchId == null, q -> q.isNull(Inventory::getTraceBatchId).or().eq(Inventory::getTraceBatchId, ""))
                .eq(traceBatchId != null, Inventory::getTraceBatchId, traceBatchId)) > 0;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
