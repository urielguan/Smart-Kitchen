package com.xykj.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class InboundOrderUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long warehouseId;
    private Long version;
    private String sourceType;
    private Long sourceId;
    private Long sourceOrderId;
    private String sourceOrderNo;
    private Long supplierId;
    private String supplierName;
    private Long orgId;
    private Long receivingOrgId;

    @Size(max = 500)
    private String remark;

    private List<String> attachments;

    @Valid
    private List<InboundOrderItemDTO> items;

    @Data
    public static class InboundOrderItemDTO extends InboundOrderCreateDTO.InboundOrderItemDTO {
        private static final long serialVersionUID = 1L;
    }
}
