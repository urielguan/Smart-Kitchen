package com.xykj.sys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 新增评价DTO
 */
@Data
public class ReviewCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 来源：meal/supervision/manual
     */
    @NotNull(message = "来源不能为空")
    private String source;

    /**
     * 评价人ID
     */
    @NotNull(message = "评价人ID不能为空")
    private Long employeeId;

    /**
     * 关联菜谱计划ID
     */
    private Long planId;

    /**
     * 评价菜品ID
     */
    private Long menuId;

    /**
     * 菜品名称
     */
    private String menuName;

    /**
     * 评价日期
     */
    @NotNull(message = "评价日期不能为空")
    private LocalDate reviewDate;

    /**
     * 餐次：breakfast/lunch/dinner
     */
    private String mealType;

    /**
     * 综合评分（1-5）
     */
    @NotNull(message = "综合评分不能为空")
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    private Integer overallScore;

    /**
     * 口味评分（1-5）
     */
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    private Integer tasteScore;

    /**
     * 营养评分（1-5）
     */
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    private Integer nutritionScore;

    /**
     * 份量评分（1-5）
     */
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    private Integer portionScore;

    /**
     * 评价内容
     */
    private String content;

    /**
     * 评价图片
     */
    private List<String> images;

    /**
     * 评价标签
     */
    private List<String> tags;

    /**
     * 所属组织ID
     */
    @NotNull(message = "组织ID不能为空")
    private Long orgId;
}
