package com.xykj.sys.dto;

import lombok.Data;

@Data
public class AiServiceConfigQueryDTO {

    private String keyword;
    private String serviceType;
    private String status;
    private Long pageNum = 1L;
    private Long pageSize = 20L;
}
