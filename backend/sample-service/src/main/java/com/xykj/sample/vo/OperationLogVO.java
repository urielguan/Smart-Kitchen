package com.xykj.sample.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志VO
 */
@Data
public class OperationLogVO {
    private Long id;
    private Long recordId;
    private String action;
    private String actionName;
    private Long operatorId;
    private String operatorName;
    private String content;
    private String terminal;
    private LocalDateTime createdAt;
}
