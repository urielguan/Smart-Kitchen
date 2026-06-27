package com.xykj.wms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaterialFoodMappingCreateDTO {
    @NotNull(message = "物料ID不能为空")
    private Long materialId;
    @NotNull(message = "标准食品ID不能为空")
    private Long foodItemId;
    private String matchStatus = "confirmed";
    private String remark;
}
