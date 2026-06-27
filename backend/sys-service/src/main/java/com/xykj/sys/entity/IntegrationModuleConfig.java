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
@TableName("sys_integration_module_config")
public class IntegrationModuleConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orgId;
    private Long tenantId;
    private String bizModule;
    private String bizScene;
    private String providerCode;
    private String configName;
    private Integer enabled;
    private String defaultMode;
    private Integer allowDocumentSwitch;
    private Integer forceThirdParty;
    private String triggerStrategy;
    private Integer allowManualFallback;
    private Integer autoCoverEnabled;
    private String autoCoverStrategy;
    private Integer allowManualConfirmCover;
    private Integer attachmentPullEnabled;
    private Integer callbackEnabled;
    private String callbackUrl;
    private String externalNoFieldRule;
    private String accessTokenUrl;
    private String refreshTokenUrl;
    private String tokenRequestMethod;
    private Integer syncFrequencyMinutes;
    private String scheduleCron;
    private Long timeoutMs;
    private Integer retryMaxCount;
    private LocalDateTime lastSyncAt;
    private String lastSyncStatus;
    private String lastErrorMessage;
    private String remark;

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
