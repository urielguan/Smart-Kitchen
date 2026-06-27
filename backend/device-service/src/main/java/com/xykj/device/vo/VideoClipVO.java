package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频片段VO
 */
@Data
public class VideoClipVO {

    /** 片段ID */
    private Long id;

    /** 来源录像ID */
    private Long recordingId;

    /** 设备ID */
    private Long deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 片段开始时间点 (秒) */
    private Integer startTimeOffset;

    /** 片段结束时间点 (秒) */
    private Integer endTimeOffset;

    /** 片段时长 (秒) */
    private Integer clipDuration;

    /** 时长格式化字符串 */
    private String durationFormat;

    /** 文件大小 (字节) */
    private Long fileSize;

    /** 文件大小格式化 */
    private String fileSizeFormat;

    /** 用途标签 */
    private String purposeTag;

    /** 用途标签名称 */
    private String purposeTagName;

    /** 导出状态 */
    private String status;

    /** 导出状态名称 */
    private String statusName;

    /** 失败原因 */
    private String failReason;

    /** 下载地址 */
    private String downloadUrl;

    /** 版本号 */
    private Integer versionNo;

    /** 创建人名称 */
    private String createdByName;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
