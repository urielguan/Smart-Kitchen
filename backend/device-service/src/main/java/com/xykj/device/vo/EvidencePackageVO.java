package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 证据包视图对象
 */
@Data
public class EvidencePackageVO {

    private Long id;

    /** 证据包编号 */
    private String packageNo;

    /** 证据包名称 */
    private String packageName;

    /** 打包状态 */
    private String status;

    /** 状态中文名 */
    private String statusName;

    /** 包含的录像ID */
    private List<Long> recordingIds;

    /** 包含的片段ID */
    private List<Long> clipIds;

    /** 包含的截图ID */
    private List<Long> screenshotIds;

    /** ZIP文件名 */
    private String fileName;

    /** ZIP文件大小(字节) */
    private Long fileSize;

    /** 格式化文件大小 */
    private String fileSizeFormat;

    /** 包含文件数量 */
    private Integer itemCount;

    /** 下载地址 */
    private String downloadUrl;

    /** 下载过期时间 */
    private LocalDateTime expiresAt;

    /** 最近下载时间 */
    private LocalDateTime downloadedAt;

    /** 下载次数 */
    private Integer downloadCount;

    /** 失败原因 */
    private String failReason;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
