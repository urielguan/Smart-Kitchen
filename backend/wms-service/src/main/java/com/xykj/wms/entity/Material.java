package com.xykj.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料主数据实体
 * 对应数据库表: wms_material
 */
@Data
@TableName("wms_material")
public class Material implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 物料ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 物料分类
     */
    private String materialCategory;

    /**
     * 关联标准食品ID
     */
    private Long foodItemId;

    /**
     * 基本单位
     */
    private String unit;

    /**
     * 规格说明
     */
    private String spec;

    /**
     * 保质期（天）
     */
    private Integer shelfLifeDays;

    /**
     * 最低库存预警量
     */
    private BigDecimal minStock;

    /**
     * 最高库存量
     */
    private BigDecimal maxStock;

    /**
     * 临期提醒天数（到期前N天提醒）
     */
    private Integer nearExpiryDays;

    /**
     * 预警天数（采购预警）
     */
    private Integer warningDays;

    /**
     * 存储条件说明
     */
    private String storageConditions;

    /**
     * 存储类型: normal=常温, cold=冷藏, freeze=冷冻
     */
    private String storageType;

    /**
     * 物料图片URL
     */
    private String imageUrl;

    /**
     * 状态: active=启用, inactive=停用
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    private BigDecimal calories;
    private BigDecimal protein;
    private BigDecimal carbohydrate;
    private BigDecimal fat;
    private BigDecimal sodium;
    private BigDecimal fiber;
    private BigDecimal vitaminA;
    private BigDecimal vitaminB1;
    private BigDecimal vitaminB2;
    private BigDecimal vitaminC;
    private BigDecimal vitaminE;
    private BigDecimal calcium;
    private BigDecimal iron;
    private BigDecimal zinc;
    private String nutritionSourceType;
    private Long nutritionSourceRefId;
    private LocalDateTime nutritionSyncedAt;

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
