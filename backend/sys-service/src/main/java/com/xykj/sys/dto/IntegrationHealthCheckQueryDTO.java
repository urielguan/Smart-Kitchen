package com.xykj.sys.dto;

import lombok.Data;

@Data
public class IntegrationHealthCheckQueryDTO {

    private String keyword;
    private Long orgId;
    private Long configId;
    private String bizModule;
    private String bizScene;
    private String providerCode;
    private Long pageNum = 1L;
    private Long pageSize = 20L;
}
