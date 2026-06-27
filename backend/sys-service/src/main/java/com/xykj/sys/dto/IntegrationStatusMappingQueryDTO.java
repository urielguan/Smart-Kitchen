package com.xykj.sys.dto;

import lombok.Data;

@Data
public class IntegrationStatusMappingQueryDTO {

    private Long configId;
    private String keyword;
    private Long pageNum = 1L;
    private Long pageSize = 20L;
}
