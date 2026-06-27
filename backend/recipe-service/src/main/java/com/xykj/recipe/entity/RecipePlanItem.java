package com.xykj.recipe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜谱计划明细实体
 * 对应数据库表: recipe_plan_item
 */
@Data
@TableName("recipe_plan_item")
public class RecipePlanItem implements Serializable {

    private static final long serialVersionUID = 1L;

    // 状态常量
    /** 待烹饪 */
    public static final String STATUS_PENDING = "pending";
    /** 烹饪中 */
    public static final String STATUS_COOKING = "cooking";
    /** 已完成 */
    public static final String STATUS_COMPLETED = "completed";

    /**
     * 明细ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 菜谱ID
     */
    private Long recipeId;

    /**
     * 菜谱名称
     */
    private String recipeName;

    /**
     * 菜谱编码
     */
    private String recipeCode;

    /**
     * 菜谱类别
     */
    private String categoryName;

    private String mealKey;

    private String mealType;

    private String mealName;

    private Integer mealExpectedCount;

    private Integer mealSortOrder;

    /**
     * 计划份数
     */
    private Integer plannedServings;

    /**
     * 已烹饪份数
     */
    private Integer cookedServings;

    /**
     * 单份成本（元）
     */
    private BigDecimal unitCost;

    /**
     * 小计成本（元）
     */
    private BigDecimal totalCost;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 备注
     */
    private String remark;

    /**
     * 状态: pending=待烹饪, cooking=烹饪中, completed=已完成
     */
    private String status;

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
     * 逻辑删除: 0=未删除, 1=已删除
     */
    @TableLogic
    private Integer deleted;
}
