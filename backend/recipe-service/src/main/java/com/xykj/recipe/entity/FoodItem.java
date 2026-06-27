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
@TableName("food_item")
public class FoodItem implements Serializable {

    @TableId(type = IdType.AUTO)
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
    private String rawPayload;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
