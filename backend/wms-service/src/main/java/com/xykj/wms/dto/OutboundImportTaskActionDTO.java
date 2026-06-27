package com.xykj.wms.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class OutboundImportTaskActionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String reason;
    private String sourceTaskNo;
}
