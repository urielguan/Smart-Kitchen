package com.xykj.sys.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 物料类别面积系数当前冻结锁
 */
@Data
@TableName("sys_dict_area_coefficient_lock")
public class SysDictAreaCoefficientLock implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dictId;

    private String dictType;

    private String dictCode;

    private String dictName;

    private String dictValue;

    private String lockStatus;

    private String lockType;

    private String lockReason;

    private Long correctionId;

    private Integer correctionVersion;

    private Long taskId;

    private Long lockedBy;

    private String lockedByName;

    private LocalDateTime lockedAt;

    private Long releasedBy;

    private String releasedByName;

    private LocalDateTime releasedAt;

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
