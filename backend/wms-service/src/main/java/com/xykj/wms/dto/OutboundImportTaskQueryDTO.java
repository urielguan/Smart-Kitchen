package com.xykj.wms.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class OutboundImportTaskQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean includeRows;
}
