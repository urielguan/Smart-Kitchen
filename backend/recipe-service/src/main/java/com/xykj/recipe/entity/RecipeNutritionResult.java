package com.xykj.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("recipe_nutrition_result")
public class RecipeNutritionResult implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private Integer calcVersion;
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
    private String missingMaterials;
    private LocalDateTime calculatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
