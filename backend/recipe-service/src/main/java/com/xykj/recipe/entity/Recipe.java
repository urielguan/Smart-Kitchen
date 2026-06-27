package com.xykj.recipe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜谱实体
 * 对应数据库表: recipe
 */
@Data
@TableName("recipe")
public class Recipe implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 菜谱ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 菜谱编码
     */
    private String recipeCode;

    /**
     * 菜谱名称
     */
    private String recipeName;

    /**
     * 菜谱类别ID
     */
    private Long categoryId;

    /**
     * 菜谱描述
     */
    private String description;

    /**
     * 菜谱图片URL
     */
    private String imageUrl;

    /**
     * 份量（克）
     */
    private BigDecimal servingSize;

    /**
     * 目标烹饪时长（分钟）
     */
    private Integer targetCookTime;

    /**
     * 最低烹饪温度（℃）
     */
    private Integer targetTempMin;

    /**
     * 最高烹饪温度（℃）
     */
    private Integer targetTempMax;

    /**
     * 制作步骤
     */
    private String cookingSteps;

    /**
     * 单份成本（元）
     */
    private BigDecimal unitCost;

    // ========== 营养成分 ==========

    /**
     * 热量（千卡/100g）
     */
    private BigDecimal calories;

    /**
     * 蛋白质（g/100g）
     */
    private BigDecimal protein;

    /**
     * 碳水化合物（g/100g）
     */
    private BigDecimal carbohydrate;

    /**
     * 脂肪（g/100g）
     */
    private BigDecimal fat;

    /**
     * 钠（mg/100g）
     */
    private BigDecimal sodium;

    /**
     * 膳食纤维（g/100g）
     */
    private BigDecimal fiber;

    /**
     * 维生素A（μg）
     */
    private BigDecimal vitaminA;

    /**
     * 维生素B1（mg）
     */
    private BigDecimal vitaminB1;

    /**
     * 维生素B2（mg）
     */
    private BigDecimal vitaminB2;

    /**
     * 维生素C（mg）
     */
    private BigDecimal vitaminC;

    /**
     * 维生素D（μg）
     */
    private BigDecimal vitaminD;

    /**
     * 维生素E（mg）
     */
    private BigDecimal vitaminE;

    /**
     * 钙（mg）
     */
    private BigDecimal calcium;

    /**
     * 铁（mg）
     */
    private BigDecimal iron;

    /**
     * 锌（mg）
     */
    private BigDecimal zinc;

    /**
     * 营养评分（0-100）
     */
    private Integer nutritionScore;

    /**
     * AI优化建议
     */
    private String aiSuggestions;

    /**
     * 是否使用AI建议
     */
    private Boolean useAiSuggestion;

    /**
     * 状态: active=启用, inactive=停用
     */
    private String status;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

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
