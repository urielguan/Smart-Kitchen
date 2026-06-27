package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class StocktakeOperationLogVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long stocktakeId;
    private String action;
    private String actionName;
    private Long operatorId;
    private String operatorName;
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
