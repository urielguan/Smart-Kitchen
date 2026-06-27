package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控录像索引实体
 * 对应表: device_monitor_record
 */
@Data
@TableName("device_monitor_record")
public class DeviceMonitorRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备ID（device_info.id） */
    private Long deviceId;

    /** 设备名称（冗余） */
    private String deviceName;

    /** 安装位置（冗余） */
    private String location;

    /** MP4文件磁盘绝对路径 */
    private String filePath;

    /** 文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 录像时长（秒） */
    private Integer duration;

    /** 录像开始时间 */
    private LocalDateTime startTime;

    /** 录像结束时间 */
    private LocalDateTime endTime;

    /** 分辨率 */
    private String resolution;

    /** 录像类型：continuous/alarm/manual */
    private String recordingType;

    /** 状态：recording/completed/archived */
    private String status;

    /** 是否关联AI违规标记 */
    private Integer hasAiMarks;

    /** 保留天数（普通30，证据365） */
    private Integer retentionDays;

    /** 过期时间（startTime + retentionDays） */
    private LocalDateTime expiresAt;

    /** 是否证据录像：0=否，1=是 */
    private Integer isEvidence;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** 标记为证据的原因 */
    private String evidenceReason;

    /** 缩略图文件路径 */
    private String thumbnailPath;

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
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
