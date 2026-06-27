package com.xykj.wms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Data
public class StocktakeOrderQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private Integer pageSize = 10;

    private String stocktakeNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long warehouseId;
    private Long locationId;
    private String status;
    private Long checkerId;
    private String checkerName;
    private Long orgId;
    private List<Long> orgIds;
    private Long tenantId;
}
