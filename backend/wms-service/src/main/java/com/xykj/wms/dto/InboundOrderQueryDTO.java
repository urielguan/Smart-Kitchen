package com.xykj.wms.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class InboundOrderQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer pageNum  = 1;
    private Integer pageSize = 10;

    private String inboundNo;
    private Long warehouseId;
    private String sourceType;
    private String status;
    private String startDate;
    private String endDate;
    private Long orgId;
    private List<Long> orgIds;
}
