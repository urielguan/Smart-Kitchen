package com.xykj.wms.dto;

import lombok.Data;

@Data
public class MaterialFoodMappingQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private Long materialId;
    private String materialName;
    private String matchStatus;
}
