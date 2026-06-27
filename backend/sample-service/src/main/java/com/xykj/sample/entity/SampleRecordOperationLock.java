package com.xykj.sample.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 留样记录操作锁实体
 */
@Data
@TableName("sample_record_operation_lock")
public class SampleRecordOperationLock implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 留样记录ID */
    private Long sampleRecordId;

    /** 留样编号 */
    private String sampleNo;

    /** 锁令牌 */
    private String lockToken;

    /** 操作类型 */
    private String operationType;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作终端 */
    private String sourceTerminal;

    /** 是否激活：0否 1是 */
    private Integer active;

    /** 抢占时间 */
    private LocalDateTime acquiredAt;

    /** 最近续租时间 */
    private LocalDateTime lastHeartbeatAt;

    /** 锁过期时间 */
    private LocalDateTime expiresAt;

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

    private Integer deleted;
}
