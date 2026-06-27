package com.xykj.device.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警工单处理记录VO
 */
@Data
public class AlertWorkOrderRecordVO {

    private Long id;

    /** 操作类型：dispatch / process / complete / review / cancel */
    private String action;

    /** 操作名称 */
    private String actionName;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作内容 */
    private String content;

    /** 附件列表 */
    private List<Object> attachments;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
