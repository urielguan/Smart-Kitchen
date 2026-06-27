package com.xykj.sys.vo;

import lombok.Data;

@Data
public class IntegrationCallbackHandleResultVO {

    private Long callbackLogId;
    private String processStatus;
    private String message;
    private Long taskId;
    private Long syncLogId;
    private Long auditLogId;
}
