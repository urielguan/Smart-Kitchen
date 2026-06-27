package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 录像清理审计日志实体
 * 对应表: device_cleanup_audit_log
 */
@Data
@TableName("device_cleanup_audit_log")
public class DeviceCleanupAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 清理批次号（CLN-yyyyMMddHHmmss） */
    private String batchId;

    /** 被删除的录像ID */
    private Long recordingId;

    /** 设备ID */
    private Long deviceId;

    /** 被删除的录像文件路径 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 删除原因：retention_expired/orphan_clip/orphan_screenshot */
    private String reason;

    /** 级联删除的片段数 */
    private Integer cascadedClips;

    /** 级联删除的截图数 */
    private Integer cascadedScreenshots;

    /** 置空的告警关联数 */
    private Integer cascadedAlertsNullified;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
