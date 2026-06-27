package com.xykj.wms.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class WarehouseQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String warehouseName;
    private String warehouseCode;
    private String warehouseType;
    private String status;
    private String format;

    /** 组织ID（业务筛选） */
    private Long orgId;

    /** 数据权限注入组织ID列表 */
    private List<Long> orgIds;
}
