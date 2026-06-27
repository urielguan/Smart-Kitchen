package com.xykj.wms.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class OutboundSourceOrderOptionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long orgId;
    private String orgName;
}
