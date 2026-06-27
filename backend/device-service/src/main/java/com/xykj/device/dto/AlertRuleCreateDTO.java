package com.xykj.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建告警规则DTO
 */
@Data
public class AlertRuleCreateDTO {

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 100, message = "规则名称最长100个字符")
    private String ruleName;

    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    /** 设备类型（material类型时可为空） */
    private String deviceType;

    /** 适用设备ID列表（逗号分隔，为空则该规则不对任何设备生效） */
    private String deviceIds;

    /** 适用物料ID列表（逗号分隔，rule_type=material时使用） */
    private String materialIds;

    /** 触发条件（material类型时可为空） */
    private String conditionJson;

    @NotBlank(message = "告警级别不能为空")
    private String alertLevel;

    /** 通知渠道（逗号分隔） */
    private String notifyChannels;

    /** 通知用户ID列表（逗号分隔） */
    private String notifyUsers;

    /** 告警派单范围（角色ID列表，逗号分隔） */
    private String dispatchScopeRoles;

    /** 是否启用 */
    private Integer isEnabled;

    /** 自动派单：0=关闭，1=开启 */
    private Integer autoDispatch;

    private Long orgId;
}
