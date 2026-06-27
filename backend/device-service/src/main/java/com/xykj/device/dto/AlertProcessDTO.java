package com.xykj.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 告警工单处理表单
 */
@Data
public class AlertProcessDTO {

    /** 处理结果 */
    @NotBlank(message = "处理结果不能为空")
    private String handleResult;

    /** 处理附件列表 [{url, name}] */
    private List<Map<String, String>> handleAttachments;
}
