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

@Data
@TableName("sys_integration_health_check_log")
public class IntegrationHealthCheckLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long configId;
    private String configNameSnapshot;
    private String bizModule;
    private String bizScene;
    private String providerCode;
    private String providerNameSnapshot;
    private String testStatus;
    private Integer authSuccess;
    private Integer reachable;
    private Integer callbackReachable;
    private String authMessage;
    private String reachabilityMessage;
    private String callbackMessage;
    private String testMessage;
    private String errorCode;
    private String errorMessage;
    private String requestPayload;
    private String requestHeaders;
    private String requestBody;
    private String responsePayload;
    private Long operatorId;
    private String operatorName;
    private Long orgId;
    private String orgNameSnapshot;
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
