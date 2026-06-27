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
 * 物料类别面积系数历史回溯重算任务
 */
@Data
@TableName("sys_dict_area_coefficient_recalc_task")
public class SysDictAreaCoefficientRecalcTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;

    private Long dictId;

    private String dictType;

    private String dictCode;

    private String dictName;

    private String dictValue;

    private Long correctionId;

    private Integer correctionVersion;

    private BigDecimal oldAreaCoefficient;

    private BigDecimal newAreaCoefficient;

    private String status;

    private Integer progressPercent;

    private Integer totalSteps;

    private Integer completedSteps;

    private Integer successCount;

    private Integer failureCount;

    private String resultMessage;

    private Long startedBy;

    private String startedByName;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

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
