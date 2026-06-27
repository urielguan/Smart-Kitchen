package com.xykj.sample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 留样操作日志实体
 * 对应数据库表: sample_operation_log
 */
@Data
@TableName("sample_operation_log")
public class SampleOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 留样记录ID */
    private Long recordId;

    /** 操作类型：create/update/dispose/manual_disposal_supplement/void/archive/ai_evaluate */
    private String action;

    /** 操作名称 */
    private String actionName;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作内容 */
    private String content;

    /** 操作终端 */
    private String terminal;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
