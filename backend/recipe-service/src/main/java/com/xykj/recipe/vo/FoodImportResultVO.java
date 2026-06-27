package com.xykj.recipe.vo;

import lombok.Data;

import java.util.Map;

@Data
public class FoodImportResultVO {
    private Integer fileCount;
    private Integer categoryCount;
    private Integer itemCount;
    private String sourceVersion;
    private Integer newFoodItemCount;
    private Integer supplementedFieldCount;
    private Integer skippedItemCount;
    private Integer duplicateItemCount;
    private Integer exceptionCount;
    private Map<String, Integer> sourceStats;
}
