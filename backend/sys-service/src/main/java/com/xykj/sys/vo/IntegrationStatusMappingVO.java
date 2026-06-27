package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntegrationStatusMappingVO {

    private Long id;
    private Long configId;
    private String configName;
    private String providerCode;
    private String sourceStatusCode;
    private String sourceStatusName;
    private String targetStatusCode;
    private Integer finishFlag;
    private Integer triggerBusinessAction;
    private String actionCode;
    private Integer writeAttachmentFlag;
    private Integer sortNo;
    private Integer status;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
