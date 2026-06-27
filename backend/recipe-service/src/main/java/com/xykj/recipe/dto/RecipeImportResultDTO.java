package com.xykj.recipe.dto;

import lombok.Data;

import java.util.List;

/**
 * 菜谱导入结果DTO
 */
@Data
public class RecipeImportResultDTO {

    /**
     * 总行数
     */
    private int total;

    /**
     * 成功行数
     */
    private int successCount;

    /**
     * 失败行数
     */
    private int failCount;

    /**
     * 是否有错误文件
     */
    private boolean hasErrors;

    /**
     * 错误文件下载URL
     */
    private String errorFileUrl;

    /**
     * 新增数量
     */
    private int createCount;

    /**
     * 更新数量
     */
    private int updateCount;

    /**
     * 错误详情列表
     */
    private List<String> errors;

    /**
     * 失败记录明细列表
     */
    private List<RecipeImportFailureDTO> failures;
}
