package com.xykj.recipe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜谱食材配比实体
 * 对应数据库表: recipe_ingredient
 */
@Data
@TableName("recipe_ingredient")
public class RecipeIngredient implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配比ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 菜谱ID
     */
    private Long recipeId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 物料规格
     */
    private String materialSpec;

    /**
     * 用量
     */
    private BigDecimal quantity;

    /**
     * 单位
     */
    private String unit;

    /**
     * 是否主料
     */
    private Boolean isMain;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 备注
     */
    private String remark;

    // ========== 食材营养成分（冗余存储，便于计算） ==========

    /**
     * 热量（千卡）
     */
    private BigDecimal calories;

    /**
     * 蛋白质（g）
     */
    private BigDecimal protein;

    /**
     * 碳水化合物（g）
     */
    private BigDecimal carbohydrate;

    /**
     * 脂肪（g）
     */
    private BigDecimal fat;

    /**
     * 钠（mg）
     */
    private BigDecimal sodium;

    /**
     * 膳食纤维（g）
     */
    private BigDecimal fiber;

    // ========== 维生素 ==========

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

    // ========== 矿物质 ==========

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
