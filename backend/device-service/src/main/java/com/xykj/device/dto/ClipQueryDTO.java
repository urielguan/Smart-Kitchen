package com.xykj.device.dto;

import lombok.Data;

/**
 * 视频片段查询DTO
 */
@Data
public class ClipQueryDTO {

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 20;

    /** 来源录像ID */
    private Long recordingId;

    /** 设备ID */
    private Long deviceId;

    /** 用途标签 */
    private String purposeTag;

    /** 导出状态 */
    private String status;
}
