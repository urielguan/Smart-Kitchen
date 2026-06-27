package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单处理记录VO
 */
@Data
public class WorkOrderRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 关联派单ID
     */
    private Long dispatchId;

    /**
     * 关联投诉ID
     */
    private Long complaintId;

    /**
     * 操作类型
     */
    private String action;

    /**
     * 操作名称
     */
    private String actionName;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人姓名
     */
    private String operatorName;

    /**
     * 操作内容
     */
    private String content;

    /**
     * 处理图片
     */
    private List<String> images;

    /**
     * 操作时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
