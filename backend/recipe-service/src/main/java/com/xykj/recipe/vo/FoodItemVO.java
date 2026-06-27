package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FoodItemVO {
    private Long id;
    private String foodCode;
    private String foodName;
    private Long categoryLevel1Id;
    private Long categoryLevel2Id;
    private BigDecimal edibleRatio;
    private BigDecimal energyKcal;
    private BigDecimal protein;
    private BigDecimal fat;
    private BigDecimal carbohydrate;
    private BigDecimal dietaryFiber;
    private BigDecimal sodium;
    private BigDecimal vitaminA;
    private BigDecimal vitaminB1;
    private BigDecimal vitaminB2;
    private BigDecimal vitaminC;
    private BigDecimal vitaminE;
    private BigDecimal calcium;
    private BigDecimal iron;
    private BigDecimal zinc;
    private String sourceFile;
    private String sourceVersion;
    private String status;
}
