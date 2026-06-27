package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警列表VO
 */
@Data
public class AlertVO {

    private Long id;

    /** 告警编号 */
    private String alertNo;

    /** 告警类型 */
    private String alertType;

    /** 关联告警规则ID */
    private Long alertRuleId;

    /** 关联告警规则名称 */
    private String alertRuleName;

    /** 告警类型名称 */
    private String alertTypeName;

    /** 告警级别 */
    private String alertLevel;

    /** 告警级别名称 */
    private String alertLevelName;

    /** 关联设备ID */
    private Long deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 设备类型 */
    private String deviceType;

    /** 关联物料ID（物料告警时使用） */
    private Long materialId;

    /** 告警内容 */
    private String alertContent;

    /** 触发时间 */
    private LocalDateTime triggeredAt;

    /** 指派处理人ID */
    private Long assignedTo;

    /** 处理人姓名 */
    private String assignedToName;

    /** 处理人手机号 */
    private String assignedToPhone;

    /** 状态 */
    private String status;

    /** 状态名称 */
    private String statusName;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
