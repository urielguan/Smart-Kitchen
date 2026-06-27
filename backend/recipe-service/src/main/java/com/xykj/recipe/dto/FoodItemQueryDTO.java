package com.xykj.recipe.dto;

import lombok.Data;

@Data
public class FoodItemQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private String foodName;
    private Long categoryId;
}
