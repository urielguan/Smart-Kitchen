package com.xykj.sample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 销样手工补录元数据
 * 对应数据库表: sample_disposal_supplement
 */
@Data
@TableName("sample_disposal_supplement")
public class SampleDisposalSupplement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 留样记录ID */
    private Long sampleRecordId;

    /** 留样编号 */
    private String sampleNo;

    /** 关联烹饪任务ID */
    private Long taskId;

    /** 销样来源类型：manual_exception_supplement */
    private String disposalSourceType;

    /** 补录场景 */
    private String supplementScene;

    /** 补录备注 */
    private String supplementRemark;

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
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
