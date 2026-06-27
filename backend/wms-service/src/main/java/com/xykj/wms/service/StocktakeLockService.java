package com.xykj.wms.service;

public interface StocktakeLockService {

    void validateUnlocked(Long warehouseId, Long locationId, String actionName);
}
