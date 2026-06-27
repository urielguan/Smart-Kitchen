package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频录像VO
 */
@Data
public class RecordingVO {

    /** 录像ID */
    private Long id;

    /** 设备ID */
    private Long deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 安装位置 */
    private String location;

    /** 录像开始时间 */
    private LocalDateTime startTime;

    /** 录像结束时间 */
    private LocalDateTime endTime;

    /** 录像时长（秒） */
    private Integer duration;

    /** 时长格式化字符串（如：01:30:45） */
    private String durationFormat;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件大小格式化（如：125.5MB） */
    private String fileSizeFormat;

    /** 分辨率 */
    private String resolution;

    /** 录像类型：continuous/alarm/manual */
    private String recordingType;

    /** 录像类型名称 */
    private String recordingTypeName;

    /** 回放地址 */
    private String playbackUrl;

    /** 下载地址 */
    private String downloadUrl;

    /** 缩略图地址 */
    private String thumbnailUrl;

    /** 是否有AI标记 */
    private Boolean hasAiMarks;

    /** 是否证据录像 */
    private Boolean isEvidence;

    /** 保留天数 */
    private Integer retentionDays;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 证据标记原因 */
    private String evidenceReason;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
