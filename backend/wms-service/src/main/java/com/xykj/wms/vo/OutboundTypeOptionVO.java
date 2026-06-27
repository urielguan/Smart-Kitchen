package com.xykj.wms.vo;

import lombok.Data;

@Data
public class OutboundTypeOptionVO {
    private Long typeId;
    private String typeCode;
    private String typeName;
    private String status;
    private Integer sortOrder;
    private String typeSource;
    private String sourceRequirementText;
    private Boolean requiresSourceBiz;
    private String approvalMode;
    private Boolean supportsAiSuggestion;
}
