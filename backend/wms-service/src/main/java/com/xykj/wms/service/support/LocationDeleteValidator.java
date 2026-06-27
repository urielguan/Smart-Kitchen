package com.xykj.wms.service.support;

import com.xykj.common.exception.BizException;
import com.xykj.wms.mapper.InboundOrderItemMapper;
import com.xykj.wms.mapper.InventoryMapper;
import com.xykj.wms.mapper.OutboundOrderItemMapper;
import com.xykj.wms.mapper.StocktakeOrderItemMapper;
import com.xykj.wms.mapper.StocktakeOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LocationDeleteValidator {

    private static final List<String> INBOUND_OPEN = List.of("pending", "approved", "partial");
    private static final List<String> OUTBOUND_OPEN = List.of("pending", "approved");
    private static final List<String> STOCKTAKE_OPEN = List.of("draft", "pending", "in_progress");

    private final InventoryMapper inventoryMapper;
    private final InboundOrderItemMapper inboundOrderItemMapper;
    private final OutboundOrderItemMapper outboundOrderItemMapper;
    private final StocktakeOrderMapper stocktakeOrderMapper;
    private final StocktakeOrderItemMapper stocktakeOrderItemMapper;

    public void validateDelete(Long locationId) {
        if (inventoryMapper.existsLocationNonZeroInventory(locationId)) {
            throw BizException.validationFailed("该仓位仍有库存数据，无法删除");
        }
        if (inboundOrderItemMapper.existsLocationInboundOccupancy(locationId, INBOUND_OPEN)) {
            throw BizException.validationFailed("该仓位存在待处理入库业务，无法删除");
        }
        if (outboundOrderItemMapper.existsLocationOutboundOccupancy(locationId, OUTBOUND_OPEN)) {
            throw BizException.validationFailed("该仓位存在待处理出库业务，无法删除");
        }
        if (stocktakeOrderMapper.existsLocationStocktakeHeaderOccupancy(locationId, STOCKTAKE_OPEN)
                || stocktakeOrderItemMapper.existsLocationStocktakeItemOccupancy(locationId, STOCKTAKE_OPEN)) {
            throw BizException.validationFailed("该仓位存在进行中的盘点业务，无法删除");
        }
        if (inboundOrderItemMapper.existsLocationInboundHistory(locationId)) {
            throw BizException.validationFailed("该仓位存在历史入库记录，无法删除");
        }
        if (outboundOrderItemMapper.existsLocationOutboundHistory(locationId)) {
            throw BizException.validationFailed("该仓位存在历史出库记录，无法删除");
        }
        if (stocktakeOrderMapper.existsLocationStocktakeHeaderHistory(locationId)
                || stocktakeOrderItemMapper.existsLocationStocktakeItemHistory(locationId)) {
            throw BizException.validationFailed("该仓位存在历史盘点记录，无法删除");
        }
        if (inventoryMapper.existsLocationInventoryHistory(locationId)) {
            throw BizException.validationFailed("该仓位存在历史库存流水记录，无法删除");
        }
    }
}
