package com.xykj.scm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 采购订单物流信息维护入参
 */
@Data
public class PurchaseOrderLogisticsUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String company;

    private String trackingNo;

    @NotBlank(message = "物流状态不能为空")
    private String logisticsStatus;

    private String shippedAt;

    private String arrivedAt;

    private String remark;

    private String sourceType;

    private String syncPayload;

    private Long integrationConfigId;

    private String integrationExternalNo;

    @Valid
    private List<PurchaseOrderAttachmentDTO> attachments;
}
