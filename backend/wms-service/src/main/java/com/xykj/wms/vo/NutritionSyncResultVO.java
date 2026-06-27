package com.xykj.wms.vo;

import lombok.Data;

@Data
public class NutritionSyncResultVO {
    private Long materialId;
    private Long foodItemId;
    private String materialName;
    private String foodName;
    private String nutritionSourceType;
}
