package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录实体
 */
@Data
@TableName("device_alert")
public class DeviceAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 告警编号 */
    private String alertNo;

    /** 告警类型：device_offline=设备离线，device_fault=设备故障，temp_abnormal=温度异常，humidity_abnormal=湿度异常，ai_violation=AI违规识别，threshold_exceed=阈值超限 */
    private String alertType;

    /** 关联告警规则ID */
    private Long alertRuleId;

    /** 告警级别：info=提示，warning=警告，error=错误，critical=严重 */
    private String alertLevel;

    /** 关联设备ID */
    private Long deviceId;

    /** 设备名称（冗余） */
    private String deviceName;

    /** 关联物料ID（物料告警时使用） */
    private Long materialId;

    /** 告警内容 */
    private String alertContent;

    /** 告警详情（JSON格式） */
    private String alertDetail;

    /** 告警截图/照片（JSON数组） */
    private String alertImages;

    /** 告警视频URL */
    private String alertVideoUrl;

    /** 关联录像段ID (device_monitor_record.id) */
    private Long recordingId;

    /** 风暴批次标识 */
    private String stormGroupId;

    /** 是否被抑制: 0=正常, 1=被抑制 */
    private Integer suppressed;

    /** 该告警代表的被抑制告警数 */
    private Integer suppressedCount;

    /** 触发时间 */
    private LocalDateTime triggeredAt;

    /** 指派处理人ID */
    private Long assignedTo;

    /** 指派时间 */
    private LocalDateTime assignedAt;

    /** 实际处理人ID */
    private Long handledBy;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 处理结果 */
    private String handleResult;

    /** 处理照片（JSON数组） */
    private String handleImages;

    /** 复核人ID */
    private Long reviewedBy;

    /** 复核时间 */
    private LocalDateTime reviewedAt;

    /** 复核结果：approved=通过，rejected=退回 */
    private String reviewResult;

    /** 复核备注 */
    private String reviewRemark;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 关闭人ID */
    private Long closedBy;

    /** 关闭说明 */
    private String closeRemark;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** 归档人ID */
    private Long archivedBy;

    /** 归档说明 */
    private String archiveRemark;

    /** 状态：pending=待处理，assigned=已指派，handling=处理中，handled=已处置，reviewed=已复核，closed=已关闭 */
    private String status;

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
