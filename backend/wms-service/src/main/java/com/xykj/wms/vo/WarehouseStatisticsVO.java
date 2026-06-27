package com.xykj.wms.vo;

import lombok.Data;
import java.io.Serializable;

@Data
public class WarehouseStatisticsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long warehouseTotal;
    private Long activeCount;
    private Long maintenanceCount;
    private Long positionTotal;
}
