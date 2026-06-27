package com.xykj.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboundImportResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer successCount;
    private Integer failureCount;
    private Integer totalCount;
    private Boolean partialSuccess;
    private List<OutboundImportResultErrorDTO> errors;
    private String errorFileName;
    private String taskNo;
    private String taskStatus;
    private OutboundImportTaskDTO task;
}
