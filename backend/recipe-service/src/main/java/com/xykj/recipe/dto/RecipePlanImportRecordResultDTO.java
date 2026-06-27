package com.xykj.recipe.dto;

import lombok.Data;

/**
 * 菜谱计划导入单条结果 DTO
 */
@Data
public class RecipePlanImportRecordResultDTO {
    private Integer seqNo;
    private String planCode;
    private String planDate;
    private String action;
    private String actionName;
    private String message;
}
