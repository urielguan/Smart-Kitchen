package com.xykj.recipe.dto;

import lombok.Data;

/**
 * 菜谱计划导入DTO（Sheet1 行数据）
 */
@Data
public class RecipePlanImportDTO {
    /** 序号（关联 Sheet2 的计划序号） */
    private Integer seqNo;
    /** 计划日期 yyyy-MM-dd */
    private String planDate;
    /** 餐次：breakfast/lunch/dinner/supper */
    private String mealType;
    /** 就餐人数 */
    private String expectedCount;
    /** 目标人群：adult/elderly/child/teenager/patient/worker */
    private String targetGroup;
    /** 开始日期 yyyy-MM-dd */
    private String startDate;
    /** 结束日期 yyyy-MM-dd */
    private String endDate;
    /** 计划单号（可选，匹配已有计划） */
    private String planCode;
    /** 备注 */
    private String remark;

    // ---- 运行时字段 ----
    /** Excel 行号（用于错误提示） */
    private int rowNum;
    /** 错误原因（校验失败时填充） */
    private String errorMessage;
}
