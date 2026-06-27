package com.xykj.wms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class LocationQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "仓库ID不能为空")
    private Long warehouseId;

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    /** 模糊搜索关键词，匹配 location_code 或 location_name */
    private String keyword;
    private String status;
    private String format;
    private String warehouseName;
    private String warehouseType;
    private String warehouseStatus;
    private Long orgId;
    private List<Long> orgIds;
}
