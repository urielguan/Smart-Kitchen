package com.xykj.sys.dto;

import lombok.Data;

@Data
public class IntegrationModuleConfigQueryDTO {

    private String keyword;
    private Long orgId;
    private String bizModule;
    private String bizScene;
    private String providerCode;
    private Integer enabled;
    private String defaultMode;
    private Long pageNum = 1L;
    private Long pageSize = 20L;
}
