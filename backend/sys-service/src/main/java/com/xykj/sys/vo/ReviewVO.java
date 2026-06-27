package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价VO
 */
@Data
public class ReviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 评价ID
     */
    private Long id;

    /**
     * 评价编号
     */
    private String reviewNo;

    /**
     * 来源：meal/supervision/manual
     */
    private String source;

    /**
     * 来源名称
     */
    private String sourceName;

    /**
     * 评价人ID
     */
    private Long employeeId;

    /**
     * 评价人姓名
     */
    private String employeeName;

    /**
     * 菜品ID
     */
    private Long menuId;

    /**
     * 菜品名称
     */
    private String menuName;

    /**
     * 评价日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reviewDate;

    /**
     * 餐次
     */
    private String mealType;

    /**
     * 餐次名称
     */
    private String mealTypeName;

    /**
     * 综合评分（1-5）
     */
    private Integer overallScore;

    /**
     * 口味评分
     */
    private Integer tasteScore;

    /**
     * 营养评分
     */
    private Integer nutritionScore;

    /**
     * 份量评分
     */
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
     * 获得积分
     */
    private Integer points;

    /**
     * 组织ID
     */
    private Long orgId;

    /**
     * 组织名称
     */
    private String orgName;

    /**
     * 回复内容
     */
    private String replyContent;

    /**
     * 回复人姓名
     */
    private String replyByName;

    /**
     * 回复时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime replyAt;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
