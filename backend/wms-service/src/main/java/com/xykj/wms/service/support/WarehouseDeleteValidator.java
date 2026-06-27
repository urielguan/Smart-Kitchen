package com.xykj.wms.service.support;

import com.xykj.common.exception.BizException;
import com.xykj.wms.mapper.InboundOrderItemMapper;
import com.xykj.wms.mapper.InboundOrderMapper;
import com.xykj.wms.mapper.InventoryMapper;
import com.xykj.wms.mapper.LocationMapper;
import com.xykj.wms.mapper.OutboundOrderItemMapper;
import com.xykj.wms.mapper.OutboundOrderMapper;
import com.xykj.wms.mapper.StocktakeOrderItemMapper;
import com.xykj.wms.mapper.StocktakeOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WarehouseDeleteValidator {

    private static final List<String> INBOUND_OPEN = List.of("pending", "approved", "partial");
    private static final List<String> OUTBOUND_OPEN = List.of("pending", "approved");
    private static final List<String> STOCKTAKE_OPEN = List.of("draft", "pending", "in_progress");

    private final InventoryMapper inventoryMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderItemMapper inboundOrderItemMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderItemMapper outboundOrderItemMapper;
    private final StocktakeOrderMapper stocktakeOrderMapper;
    private final StocktakeOrderItemMapper stocktakeOrderItemMapper;
    private final LocationMapper locationMapper;

    public void validateDelete(Long warehouseId) {
        if (inventoryMapper.existsWarehouseNonZeroInventory(warehouseId)) {
            throw BizException.validationFailed("该仓库仍有库存数据，无法删除");
        }
        if (inboundOrderMapper.existsWarehouseInboundOccupancy(warehouseId, INBOUND_OPEN)
                || inboundOrderItemMapper.existsWarehouseInboundItemOccupancy(warehouseId, INBOUND_OPEN)) {
            throw BizException.validationFailed("该仓库存在待审批/未完成入库单，无法删除");
        }
        if (outboundOrderMapper.existsWarehouseOutboundOccupancy(warehouseId, OUTBOUND_OPEN)
                || outboundOrderItemMapper.existsWarehouseOutboundItemOccupancy(warehouseId, OUTBOUND_OPEN)) {
            throw BizException.validationFailed("该仓库存在待审批/未完成出库单，无法删除");
        }
        if (stocktakeOrderMapper.existsWarehouseStocktakeOccupancy(warehouseId, STOCKTAKE_OPEN)
                || stocktakeOrderItemMapper.existsWarehouseStocktakeItemOccupancy(warehouseId, STOCKTAKE_OPEN)) {
            throw BizException.validationFailed("该仓库存在进行中的盘点单，无法删除");
        }
        if (inboundOrderMapper.existsWarehouseInboundHistory(warehouseId)
                || inboundOrderItemMapper.existsWarehouseInboundItemHistory(warehouseId)) {
            throw BizException.validationFailed("该仓库存在历史入库业务记录，无法删除");
        }
        if (outboundOrderMapper.existsWarehouseOutboundHistory(warehouseId)
                || outboundOrderItemMapper.existsWarehouseOutboundItemHistory(warehouseId)) {
            throw BizException.validationFailed("该仓库存在历史出库业务记录，无法删除");
        }
        if (stocktakeOrderMapper.existsWarehouseStocktakeHistory(warehouseId)
                || stocktakeOrderItemMapper.existsWarehouseStocktakeItemHistory(warehouseId)) {
            throw BizException.validationFailed("该仓库存在历史盘点业务记录，无法删除");
        }
        if (inventoryMapper.existsWarehouseInventoryHistory(warehouseId)) {
            throw BizException.validationFailed("该仓库存在历史库存流水记录，无法删除");
        }
        if (locationMapper.existsActiveLocationInWarehouse(warehouseId)) {
            throw BizException.validationFailed("该仓库下存在仓位，请先删除所有仓位后再删除仓库");
        }
    }
}
