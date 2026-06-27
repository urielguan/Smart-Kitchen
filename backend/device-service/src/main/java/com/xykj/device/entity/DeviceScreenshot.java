package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频回放截图实体
 * 对应表: device_screenshot
 */
@Data
@TableName("device_screenshot")
public class DeviceScreenshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源录像ID */
    private Long recordingId;

    /** 设备ID (冗余) */
    private Long deviceId;

    /** 设备名称 (冗余) */
    private String deviceName;

    /** 所属组织ID */
    private Long orgId;

    /** 抓拍时间点 (秒) */
    private Integer captureTimeOffset;

    /** 截图文件磁盘绝对路径 */
    private String filePath;

    /** 文件名 */
    private String fileName;

    /** 文件大小 (字节) */
    private Long fileSize;

    /** 截图分辨率 */
    private String resolution;

    /** 用途标签 */
    private String purposeTag;

    /** 状态 */
    private String status;

    /** 版本号 */
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
