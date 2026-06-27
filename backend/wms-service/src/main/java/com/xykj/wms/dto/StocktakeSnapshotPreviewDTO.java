package com.xykj.wms.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class StocktakeSnapshotPreviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long warehouseId;
    private Long locationId;
    private List<Long> warehouseIds;
    private List<Long> locationIds;
    private Long orgId;
    private Long tenantId;

    @AssertTrue(message = "盘点仓库不能为空")
    public boolean hasWarehouseSelection() {
        return warehouseId != null || (warehouseIds != null && !warehouseIds.isEmpty());
    }
}
