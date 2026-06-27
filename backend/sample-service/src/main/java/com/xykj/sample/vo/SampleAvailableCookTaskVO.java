package com.xykj.sample.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 手工新增留样可选烹饪任务
 */
@Data
public class SampleAvailableCookTaskVO {

    private Long id;

    /** 烹饪任务编号 */
    private String taskNo;

    /** 菜谱ID */
    private Long menuId;

    /** 菜品名称 */
    private String menuName;

    /** 留样日期 */
    private LocalDate sampleDate;

    /** 餐次 */
    private String mealType;

    /** 烹饪任务状态 */
    private String taskStatus;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
