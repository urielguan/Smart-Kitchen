package com.xykj.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InboundImportResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer successCount;
    private Integer failureCount;
    private Integer totalCount;
    private Boolean partialSuccess;
    private List<InboundImportResultErrorDTO> errors;
    private String errorFileName;
}
