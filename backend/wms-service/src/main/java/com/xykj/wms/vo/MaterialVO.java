package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料视图对象
 */
@Data
public class MaterialVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 物料ID
     */
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
     * 物料规格
     */
    private String materialSpec;

    /**
     * 物料单位
     */
    private String unit;

    /**
     * 物料类别名称
     */
    private String categoryName;

    /**
     * 存储要求
     */
    private String storageRequire;

    /**
     * 保质期（天）
     */
    private Integer shelfLifeDays;

    /**
     * 临期提醒天数
     */
    private Integer nearExpiryDays;

    /**
     * 预警天数
     */
    private Integer warningDays;

    /**
     * 当前库存
     */
    private BigDecimal currentStock;

    /**
     * 最低库存
     */
    private BigDecimal minStock;

    /**
     * 最高库存
     */
    private BigDecimal maxStock;

    /**
     * 库存状态: normal/low/high/expired
     */
    private String stockStatus;

    /**
     * 物料状态: active/inactive
     */
    private String status;

    /**
     * 物料图片URL
     */
    private String imageUrl;

    /**
     * 备注
     */
    private String remark;

    private Long foodItemId;
    private String foodCode;
    private String foodName;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nutritionSyncedAt;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
