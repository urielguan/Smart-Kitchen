package com.xykj.wms.service.impl;

import com.xykj.common.exception.BizException;
import com.xykj.wms.mapper.StocktakeOrderMapper;
import com.xykj.wms.service.StocktakeLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StocktakeLockServiceImpl implements StocktakeLockService {

    private final StocktakeOrderMapper stocktakeOrderMapper;

    @Override
    public void validateUnlocked(Long warehouseId, Long locationId, String actionName) {
        if (warehouseId == null) {
            return;
        }
        Long count = stocktakeOrderMapper.countActiveRange(warehouseId, locationId, null, 1L, 1L);
        if (count != null && count > 0) {
            throw BizException.conflict("当前仓库/仓位范围存在进行中的盘点单，暂不能" + actionName);
        }
    }
}
