package com.xykj.scm.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 采购订单溯源信息维护入参
 */
@Data
public class PurchaseOrderTraceabilityUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String traceBatchId;

    private String origin;

    private String remark;

    private String sourceType;

    private String syncPayload;

    private Long integrationConfigId;

    private String integrationExternalNo;

    @Valid
    private List<PurchaseOrderAttachmentDTO> attachments;
}
