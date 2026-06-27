package com.xykj.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 告警复核DTO
 */
@Data
public class AlertReviewDTO {

    @NotBlank(message = "复核结果不能为空")
    private String reviewResult;

    /** 复核备注；驳回时必填 */
    private String reviewRemark;

    /** 复核附件列表 [{url, name}] */
    private List<Map<String, String>> reviewAttachments;

    public boolean isApproved() {
        return "approved".equals(reviewResult);
    }

    public boolean isRejected() {
        return "rejected".equals(reviewResult);
    }
}
