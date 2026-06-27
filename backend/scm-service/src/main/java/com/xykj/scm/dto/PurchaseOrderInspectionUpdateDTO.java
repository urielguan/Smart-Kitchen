package com.xykj.scm.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 采购订单检测报告维护入参
 */
@Data
public class PurchaseOrderInspectionUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String reportNo;

    private String result;

    private String agency;

    private String inspectedAt;

    private String remark;

    private String sourceType;

    private String syncPayload;

    private Long integrationConfigId;

    private String integrationExternalNo;

    @Valid
    private List<PurchaseOrderAttachmentDTO> attachments;
}
