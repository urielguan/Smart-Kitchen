package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警规则VO
 */
@Data
public class AlertRuleVO {

    private Long id;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型 */
    private String ruleType;

    /** 规则类型名称 */
    private String ruleTypeName;

    /** 适用设备类型 */
    private String deviceType;

    /** 适用设备类型名称 */
    private String deviceTypeName;

    /** 适用设备ID列表（逗号分隔） */
    private String deviceIds;

    /** 适用设备名称列表 */
    private java.util.List<String> deviceNames;

    /** 适用物料ID列表（逗号分隔） */
    private String materialIds;

    /** 适用物料名称列表 */
    private java.util.List<String> materialNames;

    /** 触发条件JSON */
    private String conditionJson;

    /** 告警级别 */
    private String alertLevel;

    /** 告警级别名称 */
    private String alertLevelName;

    /** 通知渠道 */
    private String notifyChannels;

    /** 通知用户ID列表 */
    private String notifyUsers;

    /** 告警派单范围（角色ID列表，逗号分隔） */
    private String dispatchScopeRoles;

    /** 派单范围角色名称列表 */
    private java.util.List<String> dispatchScopeRoleNames;

    /** 是否启用 */
    private Integer isEnabled;

    /** 自动派单 */
    private Integer autoDispatch;

    private Long orgId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
