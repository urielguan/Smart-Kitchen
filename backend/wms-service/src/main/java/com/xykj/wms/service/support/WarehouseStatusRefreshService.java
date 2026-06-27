package com.xykj.wms.service.support;

import com.xykj.wms.entity.Location;
import com.xykj.wms.entity.Warehouse;
import com.xykj.wms.mapper.InboundOrderItemMapper;
import com.xykj.wms.mapper.InboundOrderMapper;
import com.xykj.wms.mapper.InventoryMapper;
import com.xykj.wms.mapper.LocationMapper;
import com.xykj.wms.mapper.OutboundOrderItemMapper;
import com.xykj.wms.mapper.OutboundOrderMapper;
import com.xykj.wms.mapper.StocktakeOrderItemMapper;
import com.xykj.wms.mapper.StocktakeOrderMapper;
import com.xykj.wms.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseStatusRefreshService {

    private static final List<String> INBOUND_OPEN = List.of("pending", "approved", "partial");
    private static final List<String> OUTBOUND_OPEN = List.of("pending", "approved");
    private static final List<String> STOCKTAKE_OPEN = List.of("draft", "pending", "in_progress");

    private final InventoryMapper inventoryMapper;
    private final WarehouseMapper warehouseMapper;
    private final LocationMapper locationMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderItemMapper inboundOrderItemMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderItemMapper outboundOrderItemMapper;
    private final StocktakeOrderMapper stocktakeOrderMapper;
    private final StocktakeOrderItemMapper stocktakeOrderItemMapper;

    public void refreshLocation(Long locationId) {
        if (locationId == null) {
            return;
        }
        Location location = locationMapper.selectById(locationId);
        if (location == null || "maintenance".equals(location.getStatus())) {
            return;
        }

        boolean hasOccupancy = hasCurrentLocationOccupancy(locationId);
        if (hasOccupancy && !"occupied".equals(location.getStatus())) {
            location.setStatus("occupied");
            locationMapper.updateById(location);
        }
        refreshWarehouse(location.getWarehouseId());
    }

    public void refreshWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            return;
        }
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null || !"active".equals(warehouse.getStatus())) {
            return;
        }
    }

    public void refreshLocationAndWarehouse(Long warehouseId, Long locationId) {
        if (locationId != null) {
            refreshLocation(locationId);
            return;
        }
        refreshWarehouse(warehouseId);
    }

    public boolean hasCurrentLocationOccupancy(Long locationId) {
        return inventoryMapper.existsLocationNonZeroInventory(locationId)
                || inboundOrderItemMapper.existsLocationInboundOccupancy(locationId, INBOUND_OPEN)
                || outboundOrderItemMapper.existsLocationOutboundOccupancy(locationId, OUTBOUND_OPEN)
                || stocktakeOrderMapper.existsLocationStocktakeHeaderOccupancy(locationId, STOCKTAKE_OPEN)
                || stocktakeOrderItemMapper.existsLocationStocktakeItemOccupancy(locationId, STOCKTAKE_OPEN);
    }

    private boolean hasCurrentWarehouseOccupancy(Long warehouseId) {
        return locationMapper.existsOccupiedLocationInWarehouse(warehouseId)
                || inventoryMapper.existsWarehouseNonZeroInventory(warehouseId)
                || inboundOrderMapper.existsWarehouseInboundOccupancy(warehouseId, INBOUND_OPEN)
                || inboundOrderItemMapper.existsWarehouseInboundItemOccupancy(warehouseId, INBOUND_OPEN)
                || outboundOrderMapper.existsWarehouseOutboundOccupancy(warehouseId, OUTBOUND_OPEN)
                || outboundOrderItemMapper.existsWarehouseOutboundItemOccupancy(warehouseId, OUTBOUND_OPEN)
                || stocktakeOrderMapper.existsWarehouseStocktakeOccupancy(warehouseId, STOCKTAKE_OPEN)
                || stocktakeOrderItemMapper.existsWarehouseStocktakeItemOccupancy(warehouseId, STOCKTAKE_OPEN);
    }
}
