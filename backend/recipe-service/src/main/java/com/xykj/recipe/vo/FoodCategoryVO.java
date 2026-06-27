package com.xykj.recipe.vo;

import lombok.Data;

@Data
public class FoodCategoryVO {
    private Long id;
    private Long parentId;
    private String categoryCode;
    private String categoryName;
    private Integer categoryLevel;
    private Integer sortOrder;
    private String sourceFile;
    private String status;
}
