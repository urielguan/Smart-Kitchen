package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频片段截取实体
 * 对应表: device_video_clip
 */
@Data
@TableName("device_video_clip")
public class DeviceVideoClip {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源录像ID (device_monitor_record.id) */
    private Long recordingId;

    /** 设备ID (冗余自来源录像) */
    private Long deviceId;

    /** 设备名称 (冗余) */
    private String deviceName;

    /** 所属组织ID */
    private Long orgId;

    /** 片段开始时间点 (秒，相对于源录像起始) */
    private Integer startTimeOffset;

    /** 片段结束时间点 (秒，相对于源录像起始) */
    private Integer endTimeOffset;

    /** 片段时长 (秒) */
    private Integer clipDuration;

    /** MP4文件磁盘绝对路径 */
    private String filePath;

    /** 文件名 */
    private String fileName;

    /** 文件大小 (字节) */
    private Long fileSize;

    /** 用途标签: violation_trace/accident_review/process_review */
    private String purposeTag;

    /** 导出状态: processing/completed/failed */
    private String status;

    /** 失败原因 */
    private String failReason;

    /** 不可变版本号 */
    private Integer versionNo;

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
