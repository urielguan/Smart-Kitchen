package com.xykj.wms.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 物料更新参数
 */
@Data
public class MaterialUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 物料名称
     */
    @Size(max = 100, message = "物料名称长度不能超过100个字符")
    private String materialName;

    /**
     * 物料规格
     */
    @Size(max = 100, message = "物料规格长度不能超过100个字符")
    private String materialSpec;

    /**
     * 物料单位
     */
    @Size(max = 20, message = "物料单位长度不能超过20个字符")
    private String unit;

    /**
     * 物料类别名称
     */
    @Size(max = 50, message = "物料类别长度不能超过50个字符")
    private String categoryName;

    /**
     * 存储要求
     */
    @Size(max = 200, message = "存储要求长度不能超过200个字符")
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
     * 最低库存
     */
    private BigDecimal minStock;

    /**
     * 最高库存
     */
    private BigDecimal maxStock;

    /**
     * 物料图片URL
     */
    @Size(max = 500, message = "图片URL长度不能超过500个字符")
    private String imageUrl;

    /**
     * 物料状态: active/inactive
     */
    private String status;

    /**
     * 备注
     */
    @Size(max = 300, message = "备注长度不能超过300个字符")
    private String remark;
}