package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RecipeNutritionResultVO {
    private Long recipeId;
    private BigDecimal calories;
    private BigDecimal protein;
    private BigDecimal carbohydrate;
    private BigDecimal fat;
    private BigDecimal sodium;
    private BigDecimal fiber;
    private BigDecimal vitaminA;
    private BigDecimal vitaminB1;
    private BigDecimal vitaminB2;
    private BigDecimal vitaminC;
    private BigDecimal vitaminD;
    private BigDecimal vitaminE;
    private BigDecimal calcium;
    private BigDecimal iron;
    private BigDecimal zinc;
    private Integer nutritionScore;
    private String passStatus;
    private BigDecimal dataCompleteness;
    private Integer missingMaterialCount;
    private List<String> missingMaterials;
    private LocalDateTime calculatedAt;
}
