package com.xykj.sys.dto;

import lombok.Data;

@Data
public class IntegrationFieldMappingQueryDTO {

    private Long configId;
    private String keyword;
    private Long pageNum = 1L;
    private Long pageSize = 20L;
}
