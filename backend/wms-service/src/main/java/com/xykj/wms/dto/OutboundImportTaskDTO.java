package com.xykj.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboundImportTaskDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskNo;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private String errorFileName;
    private List<RowDTO> rows;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer rowNumber;
        private String status;
        private String errorField;
        private String errorReason;
        private String exceptionType;
        private String outboundNo;
        private String materialCode;
        private String warehouseCode;
        private String outboundType;
        private String taskNo;
    }
}
