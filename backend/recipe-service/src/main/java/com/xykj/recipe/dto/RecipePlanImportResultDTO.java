package com.xykj.recipe.dto;

import lombok.Data;

import java.util.List;

/**
 * 菜谱计划导入结果 DTO
 */
@Data
public class RecipePlanImportResultDTO {
    private Integer total;
    private Integer successCount;
    private Integer createdCount;
    private Integer updatedCount;
    private Integer adjustmentCreatedCount;
    private Integer skippedCount;
    private Integer failCount;
    private Boolean hasErrors;
    /** 错误文件下载 URL，如 /api/v1/recipe/plans/import/errors/xxx.xlsx */
    private String errorFileUrl;
    /** 逐计划导入结果明细 */
    private List<RecipePlanImportRecordResultDTO> records;
}
