package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警策略配置实体
 */
@Data
@TableName("device_alert_rule")
public class DeviceAlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型：threshold=阈值告警，offline=离线告警，ai_event=AI事件告警 */
    private String ruleType;

    /** 适用设备类型（为空表示全部） */
    private String deviceType;

    /** 适用设备ID列表（逗号分隔，为空则该规则不对任何设备生效） */
    private String deviceIds;

    /** 适用物料ID列表（逗号分隔，rule_type=material时使用，为空则该规则不对任何物料生效） */
    private String materialIds;

    /** 触发条件（JSON格式） */
    private String conditionJson;

    /** 告警级别 */
    private String alertLevel;

    /** 通知渠道（逗号分隔：sms,email,wechat,system） */
    private String notifyChannels;

    /** 通知用户ID列表（逗号分隔） */
    private String notifyUsers;

    /** 告警派单范围（角色ID列表，逗号分隔） */
    private String dispatchScopeRoles;

    /** 是否启用：0=禁用，1=启用 */
    private Integer isEnabled;

    /** 自动派单：0=关闭，1=开启 */
    private Integer autoDispatch;

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
    private Long tenantId;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
