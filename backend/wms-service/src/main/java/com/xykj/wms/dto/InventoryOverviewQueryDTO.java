package com.xykj.wms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class InventoryOverviewQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小为1")
    private Integer pageSize = 20;

    private String keyword;
    private String categoryName;
    private Long warehouseId;
    private Long locationId;
    private String stockStatus;
    private String shelfLifeLevel;
    private String materialStatus;
}
