package com.xykj.common.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_ai_request_log")
public class AiRequestLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long serviceConfigId;
    private String requestType;
    private String serviceType;
    private String moduleCode;
    private String modelName;
    private String requestSummary;
    private String responseSummary;
    private String targetUrl;
    private Integer durationMs;
    private String status;
    private String errorMessage;
    private Long orgId;
    private Long tenantId;
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
