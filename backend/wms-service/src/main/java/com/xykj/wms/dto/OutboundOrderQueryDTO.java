package com.xykj.wms.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OutboundOrderQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private String outboundNo;
    private String outboundType;
    private String status;
    private Long warehouseId;
    private Long orgId;
    private List<Long> orgIds;
    private String startDate;
    private String endDate;
}
