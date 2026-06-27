package com.xykj.device.dto;

import lombok.Data;

import java.util.List;

/**
 * 告警规则查询DTO
 */
@Data
public class AlertRuleQueryDTO {

    private Integer pageNum = 1;
    private Integer pageSize = 20;

    private Long orgId;
    private List<Long> orgIds;

    /** 规则名称（模糊搜索） */
    private String ruleName;

    /** 规则类型 */
    private String ruleType;

    /** 适用设备类型 */
    private String deviceType;

    /** 告警级别 */
    private String alertLevel;

    /** 是否启用 */
    private Integer isEnabled;
}
