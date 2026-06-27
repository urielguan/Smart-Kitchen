package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 证据包导出实体
 * 对应表: device_evidence_package
 */
@Data
@TableName(value = "device_evidence_package", autoResultMap = true)
public class EvidencePackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 证据包编号 EP-YYYYMMDD-XXXX */
    private String packageNo;

    /** 证据包名称 */
    private String packageName;

    /** 打包状态: packing/completed/failed/expired */
    private String status;

    /** 包含的录像ID列表 (JSON) */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Long> recordingIds;

    /** 包含的片段ID列表 (JSON) */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Long> clipIds;

    /** 包含的截图ID列表 (JSON) */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Long> screenshotIds;

    /** ZIP文件磁盘绝对路径 */
    private String filePath;

    /** ZIP文件名 */
    private String fileName;

    /** ZIP文件大小(字节) */
    private Long fileSize;

    /** 包含文件数量 */
    private Integer itemCount;

    /** 所属组织ID */
    private Long orgId;

    /** 失败原因 */
    private String failReason;

    /** 下载过期时间 */
    private LocalDateTime expiresAt;

    /** 最近下载时间 */
    private LocalDateTime downloadedAt;

    /** 下载次数 */
    private Integer downloadCount;

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
}
