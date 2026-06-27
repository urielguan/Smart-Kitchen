package com.xykj.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 物料创建参数
 */
@Data
public class MaterialCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 物料编码（唯一）
     */
    @NotBlank(message = "物料编码不能为空")
    @Size(max = 50, message = "物料编码长度不能超过50个字符")
    private String materialCode;

    /**
     * 物料名称
     */
    @NotBlank(message = "物料名称不能为空")
    @Size(max = 100, message = "物料名称长度不能超过100个字符")
    private String materialName;

    /**
     * 物料规格
     */
    @NotBlank(message = "物料规格不能为空")
    @Size(max = 100, message = "物料规格长度不能超过100个字符")
    private String materialSpec;

    /**
     * 物料单位
     */
    @NotBlank(message = "物料单位不能为空")
    @Size(max = 20, message = "物料单位长度不能超过20个字符")
    private String unit;

    /**
     * 物料类别名称
     */
    @NotBlank(message = "物料类别不能为空")
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
    @NotNull(message = "保质期不能为空")
    private Integer shelfLifeDays;

    /**
     * 临期提醒天数
     */
    private Integer nearExpiryDays = 7;

    /**
     * 预警天数
     */
    private Integer warningDays = 30;

    /**
     * 最低库存
     */
    @NotNull(message = "最低库存不能为空")
    private BigDecimal minStock;

    /**
     * 最高库存
     */
    @NotNull(message = "最高库存不能为空")
    private BigDecimal maxStock;

    /**
     * 物料图片URL
     */
    @Size(max = 500, message = "图片URL长度不能超过500个字符")
    private String imageUrl;

    /**
     * 备注
     */
    @Size(max = 300, message = "备注长度不能超过300个字符")
    private String remark;
}