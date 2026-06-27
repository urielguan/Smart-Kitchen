package com.xykj.sys.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用餐评价实体类
 * 对应数据库表: sys_meal_review
 */
@Data
@TableName("sys_meal_review")
public class MealReview implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 评价ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 评价编号
     */
    private String reviewNo;

    /**
     * 来源：meal=用餐评价，supervision=监管反馈，manual=人工录入
     */
    private String source;

    /**
     * 评价人ID
     */
    private Long employeeId;

    /**
     * 评价人姓名（冗余）
     */
    private String employeeName;

    /**
     * 关联菜谱计划ID
     */
    private Long planId;

    /**
     * 评价菜品ID
     */
    private Long menuId;

    /**
     * 菜品名称（冗余）
     */
    private String menuName;

    /**
     * 评价日期
     */
    private LocalDate reviewDate;

    /**
     * 餐次：breakfast/lunch/dinner
     */
    private String mealType;

    /**
     * 综合评分（1-5）
     */
    private Integer overallScore;

    /**
     * 口味评分（1-5）
     */
    private Integer tasteScore;

    /**
     * 营养评分（1-5）
     */
    private Integer nutritionScore;

    /**
     * 份量评分（1-5）
     */
    private Integer portionScore;

    /**
     * 评价内容
     */
    private String content;

    /**
     * 评价图片（JSON数组）
     */
    private String images;

    /**
     * 评价标签（JSON数组）
     */
    private String tags;

    /**
     * 获得积分
     */
    private Integer points;

    /**
     * 回复内容
     */
    private String replyContent;

    /**
     * 回复人ID
     */
    private Long replyBy;

    /**
     * 回复人姓名
     */
    private String replyByName;

    /**
     * 回复时间
     */
    private LocalDateTime replyAt;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0=未删除，1=已删除
     */
    @TableLogic
    private Integer deleted;
}
