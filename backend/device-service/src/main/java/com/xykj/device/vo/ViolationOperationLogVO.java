package com.xykj.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 违规事件操作日志VO
 */
@Data
public class ViolationOperationLogVO {

    private Long id;

    /** 操作类型 */
    private String action;

    /** 操作名称 */
    private String actionName;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作内容 */
    private String content;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
