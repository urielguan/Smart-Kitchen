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
 * 物料类别面积系数修正版本记录
 */
@Data
@TableName("sys_dict_area_coefficient_correction")
public class SysDictAreaCoefficientCorrection implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dictId;

    private String dictType;

    private String dictCode;

    private String dictName;

    private String dictValue;

    private Integer correctionVersion;

    private BigDecimal oldAreaCoefficient;

    private BigDecimal newAreaCoefficient;

    private String oldAreaCoefficientSource;

    private String newAreaCoefficientSource;

    private String impactScope;

    private Integer impactAcknowledged;

    private LocalDateTime impactAcknowledgedAt;

    private String recalcStatus;

    private Long recalcTaskId;

    private LocalDateTime recalcCompletedAt;

    private Long operatorId;

    private String operatorName;

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
