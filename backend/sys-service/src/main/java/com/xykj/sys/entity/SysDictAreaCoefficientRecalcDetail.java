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
 * 物料类别面积系数历史回溯重算明细
 */
@Data
@TableName("sys_dict_area_coefficient_recalc_detail")
public class SysDictAreaCoefficientRecalcDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String detailCode;

    private String detailName;

    private String detailType;

    private String status;

    private Long affectedRecordCount;

    private BigDecimal quantityTotal;

    private BigDecimal oldAreaTotal;

    private BigDecimal newAreaTotal;

    private BigDecimal deltaAreaTotal;

    private String detailMessage;

    private String snapshotPayload;

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
