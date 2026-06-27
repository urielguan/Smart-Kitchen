package com.xykj.sys.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 系统字典项
 */
@Data
@TableName("sys_dict")
public class SysDictItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dictType;

    private String dictCode;

    private String dictName;

    private String dictValue;

    private String parentCode;

    private Integer sortOrder;

    private Integer isSystem;

    private String remark;

    private String status;

    /**
     * 物料类别统一面积系数（㎡/单件），仅 material_category 使用
     */
    private BigDecimal areaCoefficient;

    /**
     * 面积系数来源：system/manual/ai
     */
    private String areaCoefficientSource;

    /**
     * 最近一次 AI 建议面积系数
     */
    private BigDecimal aiSuggestedAreaCoefficient;

    /**
     * AI 建议依据摘要
     */
    private String aiSuggestionReason;

    /**
     * AI 建议生成时间
     */
    private LocalDateTime aiSuggestionGeneratedAt;

    private Long orgId;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
