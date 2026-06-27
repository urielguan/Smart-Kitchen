package com.xykj.sys.dto;

import lombok.Data;

@Data
public class IntegrationProviderTemplateQueryDTO {

    private String keyword;
    private String providerType;
    private String status;
    private Long pageNum = 1L;
    private Long pageSize = 20L;
}
